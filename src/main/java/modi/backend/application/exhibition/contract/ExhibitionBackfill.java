package modi.backend.application.exhibition.contract;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import modi.backend.domain.exhibition.catalog.CatalogDetailData;
import modi.backend.domain.exhibition.genre.GenreClassification;
import modi.backend.domain.exhibition.genre.GenreResult;

/**
 * 레거시 전시 뒤채움 계약 — 이미 승격됐지만 상세/장르가 미완성인 전시를 수집(ingestion)이 채울 때 쓰는
 * 코어의 좁은 갱신 포트(ADR-12). 이 이음새의 절반이 동기 읽기(대상 판정·스윕·입력 해소)라 이벤트가 아니라
 * 포트다. 수집은 이 인터페이스만 주입한다 — 코어 리포 직주입 금지.
 */
public interface ExhibitionBackfill {

	/** DETAIL 작업이 만난 대상 전시의 상태(없음/이미완성/상세필요)를 판정한다. */
	DetailTargetState findDetailTargetState(String externalId);

	/**
	 * 레거시 전시에 상세를 반영한다 — 상세 satellite 채움 + 전시장 보강. 대상이 아니면(전시 없음·이미 완성) 미적용.
	 *
	 * @return 적용 여부 + 전시장 자연키(영업시간 재검증 enqueue용 — 호출 측이 같은 트랜잭션에서 잇는다)
	 */
	DetailApplied applyDetail(String externalId, CatalogDetailData detail, LocalDateTime now);

	/** 원천에 상세가 없음(빈 응답)을 확인 완료로 남겨 재조회를 막는다(기존 동작 보존). */
	void markDetailChecked(String externalId, LocalDateTime now);

	/** 미분류 CATALOG 전시의 원천 식별자들(CLASSIFY_GENRE 스윕용 — 대상이 "미분류 행"이라 멱등). */
	List<String> findUnclassifiedCatalogExternalIds(int limit);

	/** 미분류 전시만 골라 {@code external_id → 분류 입력}으로 돌려준다(이미 분류·소멸된 대상은 빠진다). */
	Map<String, GenreClassification> resolveGenreInputs(Collection<String> externalIds);

	/** 분류 결과를 장르 정준층에 쓴다(성공분만 — AI 실패는 호출 측이 아예 넘기지 않는다). */
	void applyGenreResults(Map<String, GenreResult> resultsByExternalId, LocalDateTime now);

	/** 장르 백필 1배치의 조회 단계 — 미분류 CATALOG 전시의 분류 입력 조립(전시 id 기준 배치 경로). */
	List<GenreTarget> findGenreTargets(int max);

	/** 장르 백필 1배치의 반영 단계 — 전시 id 기준 배치 경로. @return 장르를 부여한 전시 수 */
	int applyGenres(List<GenreTarget> targets, List<GenreResult> results, LocalDateTime now);

	/**
	 * 상세 반영 결과. {@code applied}=false면 대상이 아니었다(호출 측은 원본 보관·후속 enqueue를 생략한다).
	 *
	 * @param placeKey 반영된 전시의 전시장 자연키(전시장이 없으면 null)
	 */
	record DetailApplied(boolean applied, String placeKey) {

		public static DetailApplied skipped() {
			return new DetailApplied(false, null);
		}
	}
}
