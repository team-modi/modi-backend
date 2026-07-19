package modi.backend.application.exhibition.sync.draft;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.application.exhibition.sync.outbox.ExhibitionOutboxFacade;
import modi.backend.domain.exhibition.catalog.Exhibition;
import modi.backend.domain.exhibition.catalog.ExhibitionPlace;
import modi.backend.domain.exhibition.catalog.ExhibitionPlaceRepository;
import modi.backend.domain.exhibition.catalog.ExhibitionRepository;
import modi.backend.domain.exhibition.sync.data.CatalogDetailData;
import modi.backend.domain.exhibition.sync.data.CatalogExhibitionData;
import modi.backend.domain.exhibition.sync.data.GenreClassification;
import modi.backend.domain.exhibition.sync.data.GenreResult;
import modi.backend.domain.exhibition.sync.draft.ExhibitionDraft;
import modi.backend.domain.exhibition.sync.draft.ExhibitionDraftRepository;
import modi.backend.domain.exhibition.sync.entity.CultureDetailResponse;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessageType;
import modi.backend.infra.exhibition.sync.CultureDetailResponseJpaRepository;

/**
 * 전시 초기화 스테이징·승격 유스케이스 조율(ADR-10 2부) — draft의 DB 경계를 맡는다.
 *
 * <p><b>스텝 체인</b>: 스테이징이 FETCH_DETAIL을, 상세 해소가 CLASSIFY_GENRE를 <b>같은 트랜잭션</b>에서 enqueue한다
 * (아웃박스 원자성 — 스텝은 반영됐는데 다음 스텝 메시지가 유실되는 창이 없다). 장르가 마지막 필수 스텝이므로
 * 승격 검사는 장르 반영 트랜잭션에서 일어난다.
 *
 * <p><b>승격 트랜잭션 경계(사용자 확정)</b>: 마지막 필수 스텝 반영과 같은 트랜잭션에서
 * [전시장 resolve → Exhibition 생성 → 상세 satellite → 장르 정준행 → 영업시간 재검증 enqueue → draft 종료]가
 * 전부 일어난다. 경합(동시 완주·재전달)은 draft 낙관락 + {@code exhibitions.external_id} UK가 멱등을 보장한다.
 *
 * <p>상태 변경은 전부 {@link ExhibitionDraft}·애그리거트 루트 메서드 안에서만 일어난다(Facade는 load·조율·save).
 */
@Service
@RequiredArgsConstructor
public class ExhibitionDraftFacade {

	private static final Logger log = LoggerFactory.getLogger(ExhibitionDraftFacade.class);

	private final ExhibitionDraftRepository exhibitionDraftRepository;
	/** 전시 애그리거트 루트 — 승격 시 코어 생성·부속(상세·장르) upsert의 단일 진입점. */
	private final ExhibitionRepository exhibitionRepository;
	/** 전시장 애그리거트 루트 — 승격 시 resolve-or-create·상세 보강의 단일 진입점. */
	private final ExhibitionPlaceRepository exhibitionPlaceRepository;
	/** 전시 아웃박스 — 스텝 체인(FETCH_DETAIL→CLASSIFY_GENRE)·승격 후 영업시간 재검증 enqueue(같은 트랜잭션). */
	private final ExhibitionOutboxFacade exhibitionOutboxFacade;
	private final CultureDetailResponseJpaRepository cultureDetailResponseRepository;

	/** 스테이징 결과 — 동기화 루프의 집계 어휘. */
	public enum StageOutcome {
		/** 새 draft가 만들어졌다(+ FETCH_DETAIL enqueue). */
		STAGED,
		/** 기존 미종료 draft의 목록분을 갱신했다. */
		REFRESHED,
		/** 종료(COMPLETED/FAILED) draft — 손대지 않았다. */
		SKIPPED
	}

	/**
	 * 목록 1건을 스테이징한다 — [draft 저장 + FETCH_DETAIL enqueue]가 <b>한 트랜잭션</b>(ADR-10 원자성).
	 * 재sync가 같은 원천을 다시 만나면 목록분만 갱신하고, 미해소 스텝의 메시지는 멱등 enqueue로 보강한다.
	 */
	@Transactional
	public StageOutcome stageFromList(CatalogExhibitionData data, LocalDateTime now) {
		ExhibitionDraft existing = exhibitionDraftRepository.findByExternalId(data.externalId()).orElse(null);
		if (existing == null) {
			ExhibitionDraft staged = exhibitionDraftRepository.save(ExhibitionDraft.stage(data));
			exhibitionOutboxFacade.enqueue(OutboxMessageType.FETCH_DETAIL, staged.getExternalId(), now);
			return StageOutcome.STAGED;
		}
		if (existing.getStatus().isTerminal()) {
			return StageOutcome.SKIPPED; // 종료 draft는 불변 — 승격됐거나(전시 존재) 영구 실패(수동 개입 대상).
		}
		existing.refreshFromList(data);
		exhibitionDraftRepository.save(existing);
		// 미해소 스텝의 메시지가 (수동 삭제 등으로) 사라졌어도 멱등 enqueue가 안전망으로 복원한다.
		if (existing.needsDetail()) {
			exhibitionOutboxFacade.enqueue(OutboxMessageType.FETCH_DETAIL, existing.getExternalId(), now);
		} else if (existing.needsGenre()) {
			exhibitionOutboxFacade.enqueue(OutboxMessageType.CLASSIFY_GENRE, existing.getExternalId(), now);
		}
		return StageOutcome.REFRESHED;
	}

	/** FETCH_DETAIL 핸들러의 draft 경로 판정 — 상세 스텝이 미해소인 draft가 있는가. */
	@Transactional(readOnly = true)
	public boolean needsDetail(String externalId) {
		return exhibitionDraftRepository.findByExternalId(externalId)
				.map(ExhibitionDraft::needsDetail)
				.orElse(false);
	}

	/** 미종료 draft가 있는가 — 재전달 메시지가 승격 전 draft를 "대상 미존재"로 오판하지 않기 위한 판정. */
	@Transactional(readOnly = true)
	public boolean hasActiveDraft(String externalId) {
		return exhibitionDraftRepository.findByExternalId(externalId)
				.map(draft -> !draft.getStatus().isTerminal())
				.orElse(false);
	}

	/** CLASSIFY_GENRE 핸들러의 draft 경로 판정 — 장르 스텝이 미해소인 draft의 분류 입력을 돌려준다(아니면 empty). */
	@Transactional(readOnly = true)
	public Optional<GenreClassification> resolveGenreInput(String externalId) {
		return exhibitionDraftRepository.findByExternalId(externalId)
				.filter(ExhibitionDraft::needsGenre)
				.map(draft -> new GenreClassification(draft.getTitle(),
						draft.getCategory() == null ? null : draft.getCategory().name(),
						draft.getDescription(), draft.getPlaceName(), null, draft.getRealmName()));
	}

	/**
	 * 상세 값 도착(FETCH_DETAIL 해소) — [draft 상세분 반영 + 벤더 원본 적재 + CLASSIFY_GENRE enqueue]가 한 트랜잭션.
	 * 다음 필수 스텝(장르)이 상세 도착 <b>후에</b> 걸리는 이유: 분류 입력(설명·장소)이 그때 온전해진다(스텝 체인).
	 */
	@Transactional
	public void applyDetail(String externalId, CatalogDetailData detail, LocalDateTime now) {
		ExhibitionDraft draft = exhibitionDraftRepository.findByExternalId(externalId).orElse(null);
		if (draft == null || !draft.needsDetail()) {
			return; // 재전달·경합 — 이미 해소됐거나 대상이 아니다.
		}
		draft.applyDetail(detail, now);
		archiveDetailPayload(externalId, detail);
		exhibitionOutboxFacade.enqueue(OutboxMessageType.CLASSIFY_GENRE, externalId, now);
		promoteIfReady(draft, now);
		exhibitionDraftRepository.save(draft);
	}

	/** 원천 무상세 확인(FETCH_DETAIL 해소) — 값 없이 스텝만 해소하고 다음 스텝(장르)을 건다. */
	@Transactional
	public void markDetailAbsent(String externalId, LocalDateTime now) {
		ExhibitionDraft draft = exhibitionDraftRepository.findByExternalId(externalId).orElse(null);
		if (draft == null || !draft.needsDetail()) {
			return;
		}
		draft.markDetailAbsent(now);
		exhibitionOutboxFacade.enqueue(OutboxMessageType.CLASSIFY_GENRE, externalId, now);
		promoteIfReady(draft, now);
		exhibitionDraftRepository.save(draft);
	}

	/**
	 * 장르 반영(CLASSIFY_GENRE 해소) + 승격 검사 — 정상 체인에선 장르가 마지막 필수 스텝이라 대개 여기서 승격된다.
	 * 게이트를 채우면 같은 트랜잭션에서 [전시장 resolve → 전시 생성 → 상세 satellite → 장르 정준행 →
	 * 영업시간 재검증 enqueue → draft 종료]까지 완주한다(사용자 확정 승격 경계).
	 */
	@Transactional
	public void applyGenreAndPromote(String externalId, GenreResult result, LocalDateTime now) {
		ExhibitionDraft draft = exhibitionDraftRepository.findByExternalId(externalId).orElse(null);
		if (draft == null || !draft.needsGenre()) {
			return; // 재전달·경합 — 이미 분류됐거나 대상이 아니다.
		}
		draft.applyGenre(result, now);
		promoteIfReady(draft, now);
		exhibitionDraftRepository.save(draft);
	}

	/**
	 * <b>모든 스텝 해소 지점</b>에서 게이트를 검사한다 — "마지막 스텝 = 장르" 순서 가정에 기대지 않는다.
	 * 스텝이 역순으로 도착해도(예: 잔존 CLASSIFY_GENRE 메시지가 상세보다 먼저 처리) 마지막으로 해소된 스텝의
	 * 트랜잭션이 승격을 완주하므로, 게이트를 다 채운 draft가 영구 ENRICHING으로 침묵하는 경로가 없다.
	 */
	private void promoteIfReady(ExhibitionDraft draft, LocalDateTime now) {
		if (draft.isReadyForPromotion()) {
			promote(draft, now);
		}
	}

	/** 필수 스텝의 영구 실패(4xx·시도 소진) — draft를 FAILED로 종료해 운영자에게 보인다. */
	@Transactional
	public void markStepPermanentlyFailed(String externalId, String error, LocalDateTime now) {
		ExhibitionDraft draft = exhibitionDraftRepository.findByExternalId(externalId).orElse(null);
		if (draft == null || draft.getStatus().isTerminal()) {
			return;
		}
		draft.fail(error, now);
		exhibitionDraftRepository.save(draft);
		log.warn("전시 draft 영구 실패(externalId={}): {}", externalId, error);
	}

	/**
	 * 승격 — 완성된 draft의 필드를 진짜 도메인으로 이관한다. 이미 같은 원천의 전시가 있으면(경합·수동 적재)
	 * 새로 만들지 않고 그 전시로 종료한다(external_id UK가 최후의 멱등 가드).
	 */
	private void promote(ExhibitionDraft draft, LocalDateTime now) {
		Exhibition existing = exhibitionRepository.findByExternalId(draft.getExternalId()).orElse(null);
		if (existing != null) {
			draft.complete(existing.getId(), now);
			return;
		}
		ExhibitionPlace place = exhibitionPlaceRepository.resolveOrCreate(draft.getPlaceName(), draft.getRegion(),
				draft.getSigungu(), draft.getGpsX(), draft.getGpsY());
		Exhibition promoted = exhibitionRepository.save(Exhibition.createCatalog(draft.getExternalId(),
				draft.getTitle(), place.getId(), draft.getStartDate(), draft.getEndDate(), draft.getCategory(),
				draft.getPosterUrl(), draft.getDetailUrl(), draft.getServiceName()));
		applyDetailToPromoted(draft, promoted, place, now);
		exhibitionRepository.applyGenre(promoted.getId(),
				new GenreResult(draft.getGenreKeyword(), draft.getGenreProvider(), draft.getGenreModel()), now);
		// 이벤트 구동 재검증(설계 §4-1): 새 전시가 기존 장소에 들어오면 재검증을 건다 — 같은 트랜잭션(원자성).
		exhibitionOutboxFacade.enqueueHoursRefresh(place.getPlaceKey(), now);
		draft.complete(promoted.getId(), now);
		log.info("전시 draft 승격(externalId={} → exhibitionId={})", draft.getExternalId(), promoted.getId());
	}

	/** 상세분 이관 — 값이 있으면 satellite·전시장 보강, 무상세 해소였으면 확인 완료 표식만(기존 의미 보존). */
	private void applyDetailToPromoted(ExhibitionDraft draft, Exhibition promoted, ExhibitionPlace place,
			LocalDateTime now) {
		boolean hasDetailValues = draft.getPrice() != null || draft.getDescription() != null
				|| draft.getImgUrl() != null;
		if (hasDetailValues) {
			exhibitionRepository.applyDetail(promoted.getId(), draft.getPrice(), draft.getDescription(),
					draft.getImgUrl(), now);
		} else {
			exhibitionRepository.markDetailChecked(promoted.getId(), now);
		}
		if (draft.getPlaceAddr() != null || draft.getPlacePhone() != null || draft.getPlaceUrl() != null) {
			place.enrichDetail(draft.getPlaceAddr(), draft.getPlacePhone(), draft.getPlaceUrl());
			exhibitionPlaceRepository.save(place);
		}
	}

	/** 상세 원본을 벤더층에 upsert한다(순수 원본 보관소 — payload 있을 때만). 부가 기록이라 실패해도 반영을 깨지 않는다. */
	private void archiveDetailPayload(String externalId, CatalogDetailData detail) {
		if (detail == null || detail.payload() == null) {
			return;
		}
		try {
			cultureDetailResponseRepository.findByExternalId(externalId)
					.ifPresentOrElse(row -> {
						row.refresh(detail.payload());
						cultureDetailResponseRepository.save(row);
					}, () -> cultureDetailResponseRepository.save(
							CultureDetailResponse.first(externalId, detail.payload())));
		} catch (RuntimeException e) {
			log.warn("상세 원본 적재 실패(externalId={}, 반영은 계속): {}", externalId, e.getMessage());
		}
	}
}
