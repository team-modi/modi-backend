package modi.backend.domain.exhibition.sync.port;

import modi.backend.domain.exhibition.sync.data.GenreClassification;
import modi.backend.domain.exhibition.sync.data.GenreResult;

import java.util.List;

/**
 * 전시 장르 분류 포트(도메인 소유, 구현 무관). 애플리케이션은 이 인터페이스만 의존하고,
 * 실제 전략(랜덤/AI)은 구현체가 담당한다 — {@code app.exhibition.genre.classifier}로 교체(DIP).
 * <p>
 * 계약: 반환 {@link GenreResult#genreKeyword()}는 <b>항상</b> {@link GenreKeyword} 마스터 중 하나여야 한다. AI 미설정·한도 초과(429)·오류 등
 * 어떤 경우에도 예외를 던지지 말고 유효한 장르를 반환한다(구현이 폴백 보장) — 분류는 부가 기능이라 등록/초기화 흐름을 깨지 않는다.
 * <p>
 * 값이 아니라 {@link GenreResult}(값 + 계보)를 반환하는 이유: AI 구현은 미설정·429·오류 시 <b>내부에서</b> 랜덤으로 폴백하므로,
 * 값만 넘기면 호출부가 "AI가 분류함"과 "AI 실패 → 랜덤"을 구분할 수 없다. 그 구분이 사라지면 폴백값이 저장되는 순간
 * 미분류(IS NULL) 대상에서 빠져 <b>영구 이탈</b>한다. 출처를 값과 함께 반환해야 정준층이 선별 재분류를 할 수 있다.
 */
public interface GenreClassifier {

	/** 전시 정보를 장르 마스터 중 1개로 분류한다(실패 시에도 유효 장르 + 실제 출처 반환). */
	GenreResult classify(GenreClassification input);

	/**
	 * 여러 전시를 한꺼번에 분류한다. 반환 리스트는 입력과 <b>같은 순서·같은 크기</b>이며 각 값은 마스터 중 하나다.
	 * 기본 구현은 단건 분류를 반복하지만, AI 구현은 <b>단일 호출(배치)</b>로 오버라이드해 외부 호출 수를 1회로 줄인다
	 * (무료 한도 429 폭주·부팅 지연 방지 — CATALOG 초기화 백필의 핵심 경로).
	 */
	default List<GenreResult> classifyAll(List<GenreClassification> inputs) {
		return inputs.stream().map(this::classify).toList();
	}
}
