package modi.backend.domain.exhibition;

/**
 * 장르 분류 결과 + 계보(어느 출처가·어느 모델이 만들었나).
 *
 * <p>왜 분류기가 값만이 아니라 출처까지 반환해야 하나: AI 분류기는 미설정·429·타임아웃 시 <b>내부에서</b> 랜덤으로
 * 폴백한다. 그래서 호출부가 "어떤 빈이 @Primary인가"로는 실제 출처를 알 수 없다 — GEMINI를 골랐어도 반환값이
 * 랜덤일 수 있다. 값만 넘기면 그 구분이 영영 사라지고, 폴백값이 저장되는 순간 미분류(IS NULL) 대상에서 빠져
 * <b>영구 이탈</b>한다(현행 동작). 출처를 값과 함께 반환해야 {@code exhibition_genre.provider}가 이 문제를 푼다.
 *
 * @param genreKeyword 마스터({@link GenreKeyword}) 중 1개 — 어떤 경우에도 유효한 값
 * @param provider     실제 산출 출처. 폴백이 일어났다면 {@link GenreProvider#RANDOM}이다
 * @param model        실제 서빙 모델(응답 modelVersion). RANDOM·USER엔 모델이 없어 null
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

	/** 랜덤 폴백 — 나중에 선별 재분류 대상이 된다. */
	public static GenreResult random(String genreKeyword) {
		return new GenreResult(genreKeyword, GenreProvider.RANDOM, null);
	}

	/** 사용자가 직접 지정(CUSTOM 등록). */
	public static GenreResult user(String genreKeyword) {
		return new GenreResult(genreKeyword, GenreProvider.USER, null);
	}
}
