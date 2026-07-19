package modi.backend.ingestion.domain.data;

import modi.backend.domain.exhibition.genre.GenreProvider;

/**
 * 장르 분류 결과 + 계보(어느 출처가·어느 모델이 만들었나).
 *
 * <p>왜 분류기가 값만이 아니라 출처까지 반환해야 하나: 2차 폴백 체인(ADR-11)에서 실제 분류가 1차(Gemini)인지
 * 2차(Claude)인지, 혹은 로컬 mock인지는 반환값만이 안다 — 호출부가 "어떤 빈이 @Primary인가"로는 알 수 없다.
 * 이 계보가 {@code exhibition_genre.provider}에 남아 공급자 교체 추적·재분류 선별의 근거가 된다.
 * (과거엔 실패 시 랜덤 폴백값이 섞여 이 구분이 더 절실했다 — 지금은 실패가 예외 → 아웃박스 재시도로 표현되어
 * 가짜 값 자체가 생기지 않는다.)
 *
 * @param genreKeyword 마스터({@link GenreKeyword}) 중 1개 — 어떤 경우에도 유효한 값
 * @param provider     실제 산출 출처(GEMINI/CLAUDE/MOCK/USER — RANDOM·UNKNOWN은 레거시 판독 전용)
 * @param model        실제 서빙 모델(응답 modelVersion). MOCK·USER엔 모델이 없어 null
 */
public record GenreResult(String genreKeyword, GenreProvider provider, String model) {

	public GenreResult {
		if (genreKeyword == null || genreKeyword.isBlank()) {
			throw new IllegalArgumentException("genreKeyword는 항상 마스터 중 하나여야 한다");
		}
		if (provider == null) {
			throw new IllegalArgumentException("provider는 필수다 — 출처 미기록이 폴백 영구 이탈의 원인이다");
		}
	}

	/** AI가 분류. model은 응답 modelVersion(없으면 null). */
	public static GenreResult ai(String genreKeyword, GenreProvider provider, String model) {
		return new GenreResult(genreKeyword, provider, model);
	}

	/** 로컬/CI 결정적 분류기(mock) — 실 분류가 아님이 계보에 남는다. */
	public static GenreResult mock(String genreKeyword) {
		return new GenreResult(genreKeyword, GenreProvider.MOCK, null);
	}

	/** 사용자가 직접 지정(CUSTOM 등록). */
	public static GenreResult user(String genreKeyword) {
		return new GenreResult(genreKeyword, GenreProvider.USER, null);
	}
}
