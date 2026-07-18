package modi.backend.application.exhibition.sync.enricher;

import modi.backend.application.exhibition.sync.ExhibitionSyncFacade;

import modi.backend.application.exhibition.sync.outbox.ExhibitionOutboxFacade;
import modi.backend.application.exhibition.sync.outbox.OutboxProcessing;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import modi.backend.config.CatalogEnrichProperties;
import modi.backend.config.GenreProperties;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessage;
import modi.backend.domain.exhibition.sync.data.GenreClassification;
import modi.backend.domain.exhibition.sync.port.GenreClassifier;
import modi.backend.domain.exhibition.genre.GenreProvider;
import modi.backend.domain.exhibition.sync.data.GenreResult;
import modi.backend.domain.exhibition.sync.outbox.OutboxFailureType;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessageType;

/**
 * 공공데이터(CATALOG) 장르 보강 오케스트레이션 — <b>전시 아웃박스 기반</b>({@link OutboxMessageType#CLASSIFY_GENRE}).
 *
 * <p><b>왜 큐인가</b>(사용자 최우선 요구 "AI는 늦어도 최소 1회 무조건"): 현행은 AI 장애 시 랜덤 폴백값이
 * {@code genre_keyword}에 저장돼 미분류(IS NULL) 대상에서 빠져 <b>영구 이탈</b>했다 — AI가 회복돼도 다시 분류되지 않았다.
 * 이제 미분류 전시를 CLASSIFY_GENRE 작업으로 남기고, AI가 폴백하면(=장애) 작업을 <b>RETRYABLE</b>로 두어 회복 후
 * 자동 재분류되게 한다. 그동안 전시는 장르 없이 노출된다(기능 강등, 설계 §2).
 *
 * <p>흐름: (1) 스윕 — 미분류 CATALOG를 CLASSIFY_GENRE로 멱등 enqueue. (2) 드레인 — 도래 작업을 배치로(배치당 AI 1콜)
 * 분류하고, 성공분만 정준층에 쓰고 작업을 성공 처리, 폴백분은 작업을 RETRYABLE로 둔다. AI 호출은 트랜잭션 밖이다.
 */
@Component
@RequiredArgsConstructor
public class CatalogEnricher {

	private static final Logger log = LoggerFactory.getLogger(CatalogEnricher.class);

	private final ExhibitionSyncFacade exhibitionSyncFacade;
	private final ExhibitionOutboxFacade exhibitionOutboxFacade;
	private final CatalogEnrichProperties properties;
	private final GenreProperties genreProperties;
	/** 장르 분류 전략(랜덤/AI) — 주입되는 구현은 {@code app.exhibition.genre.classifier}로 선택된다(@Primary). */
	private final GenreClassifier genreClassifier;

	/**
	 * 미분류 CATALOG 장르를 아웃박스로 스윕·드레인한다. 각 배치는 AI 1회 호출(배치가 비면 조기 종료).
	 *
	 * @return 이번 실행에서 상태를 전이시킨 CLASSIFY_GENRE 작업 수(분류·재시도·마감)
	 */
	public int enrichGenres() {
		LocalDateTime now = LocalDateTime.now();
		sweepUnclassified(now);
		int total = 0;
		for (int i = 0; i < properties.genreMaxBatchesPerRun(); i++) {
			int processed = drainBatch(properties.genreBatchSize());
			total += processed;
			if (processed == 0) {
				break; // 도래 작업 소진 → 조기 종료(AI를 빈 배치로 태우지 않음)
			}
		}
		if (total > 0) {
			log.info("전시 장르 작업 처리 {}건", total);
		}
		return total;
	}

	/** 미분류 CATALOG를 CLASSIFY_GENRE로 멱등 enqueue한다(이번 실행이 드레인할 수 있는 만큼만 — 나머진 다음 실행이 스윕). */
	private void sweepUnclassified(LocalDateTime now) {
		int sweepLimit = properties.genreBatchSize() * properties.genreMaxBatchesPerRun();
		List<String> externalIds = exhibitionSyncFacade.findUnclassifiedCatalogExternalIds(sweepLimit);
		if (!externalIds.isEmpty()) {
			exhibitionOutboxFacade.enqueueAll(OutboxMessageType.CLASSIFY_GENRE, externalIds, now);
		}
	}

	/**
	 * 한 배치 드레인: 도래 작업 조회 → [조회 tx] 대상 해소 → <b>tx 밖 AI 호출</b> → [반영 tx] 성공분 쓰기 + 작업 전이.
	 * AI 호출이 트랜잭션 밖이라는 것이 이 배치의 존재 이유다(배치 1콜이 최대 60초 + 429 백오프까지 걸린다).
	 *
	 * @return 이 배치에서 상태를 전이시킨 작업 수(0이면 도래 작업 없음)
	 */
	private int drainBatch(int batchSize) {
		LocalDateTime now = LocalDateTime.now();
		List<OutboxMessage> jobs = exhibitionOutboxFacade.findDue(OutboxMessageType.CLASSIFY_GENRE, batchSize, now);
		if (jobs.isEmpty()) {
			return 0;
		}
		List<String> externalIds = jobs.stream().map(OutboxMessage::getTargetKey).toList();
		Map<String, GenreClassification> inputs = exhibitionSyncFacade.resolveGenreInputs(externalIds);

		List<OutboxMessage> actionable = new ArrayList<>();
		List<GenreClassification> inputList = new ArrayList<>();
		int transitioned = 0;
		for (OutboxMessage job : jobs) {
			GenreClassification input = inputs.get(job.getTargetKey());
			if (input == null) {
				// 이미 분류됐거나 전시가 사라짐 — 할 일 없으니 성공으로 마감(재분류 대상 아님).
				if (OutboxProcessing.succeed(exhibitionOutboxFacade, job, now)) {
					transitioned++;
				}
			} else {
				actionable.add(job);
				inputList.add(input);
			}
		}
		if (actionable.isEmpty()) {
			return transitioned;
		}

		List<GenreResult> results = genreClassifier.classifyAll(inputList); // 트랜잭션 밖 AI 호출(배치당 1콜)

		Map<String, GenreResult> toWrite = new HashMap<>();
		List<OutboxMessage> succeededJobs = new ArrayList<>();
		List<OutboxMessage> retryJobs = new ArrayList<>();
		for (int i = 0; i < actionable.size(); i++) {
			OutboxMessage job = actionable.get(i);
			GenreResult result = i < results.size() ? results.get(i) : null;
			if (result != null && isRealClassification(result)) {
				toWrite.put(job.getTargetKey(), result);
				succeededJobs.add(job);
			} else {
				retryJobs.add(job); // AI 미가용(폴백) — 회복 후 재분류되게 RETRYABLE로 둔다.
			}
		}

		exhibitionSyncFacade.applyGenreResults(toWrite, now); // 성공분만 정준층에 쓴다(폴백값은 저장하지 않음)
		for (OutboxMessage job : succeededJobs) {
			if (OutboxProcessing.succeed(exhibitionOutboxFacade, job, now)) {
				transitioned++;
			}
		}
		for (OutboxMessage job : retryJobs) {
			if (OutboxProcessing.fail(exhibitionOutboxFacade, job, OutboxFailureType.RETRYABLE,
					"AI 분류 미가용(폴백) — 회복 후 재분류", now)) {
				transitioned++;
			}
		}
		return transitioned;
	}

	/**
	 * 이 결과를 "성공한 분류"로 볼지 판정한다. AI(gemini) 구성일 때는 랜덤 폴백(=AI 장애)은 성공이 아니다 —
	 * 그래야 회복 후 재분류된다. 랜덤/mock 구성일 때는 RANDOM이 정상 산출이라 성공으로 본다(무한 재시도 방지).
	 */
	private boolean isRealClassification(GenreResult result) {
		if (genreProperties.useGemini()) {
			return result.provider() != GenreProvider.RANDOM;
		}
		return true;
	}
}
