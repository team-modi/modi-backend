package modi.backend.domain.exhibition;

/**
 * 전시 장르 분류 포트(도메인 소유, 구현 무관). 애플리케이션은 이 인터페이스만 의존하고,
 * 실제 전략(랜덤/AI)은 구현체가 담당한다 — {@code app.exhibition.genre.classifier}로 교체(DIP).
 * <p>
 * 계약: 반환값은 <b>항상</b> {@link GenreKeyword} 마스터 중 하나여야 한다. AI 미설정·한도 초과(429)·오류 등
 * 어떤 경우에도 예외를 던지지 말고 유효한 장르를 반환한다(구현이 폴백 보장) — 분류는 부가 기능이라 등록/초기화 흐름을 깨지 않는다.
 */
public interface GenreClassifier {

	/** 전시 정보를 장르 마스터 중 1개로 분류한다(실패 시에도 유효 장르 반환). */
	String classify(GenreClassification input);
}
