package modi.backend.infra.exhibition.sync.mock;

import org.springframework.stereotype.Component;

import modi.backend.domain.exhibition.genre.GenreKeyword;
import modi.backend.domain.exhibition.sync.data.GenreClassification;
import modi.backend.domain.exhibition.sync.data.GenreResult;
import modi.backend.domain.exhibition.sync.port.GenreClassifier;

/**
 * 로컬/CI/키없음 환경의 <b>결정적</b> 장르 분류기 — 제목 해시로 마스터에서 1개를 고른다(AI 호출·비용 0).
 * {@code app.exhibition.genre.classifier=mock}(기본)일 때 주 분류기로 선택된다(운영 prod만 gemini 체인).
 *
 * <p>Random(무작위) 분류기를 대체한다(ADR-11): 같은 입력 → 같은 출력이라 테스트 단언이 가능하고,
 * 산출물엔 {@code provider=MOCK}이 붙어 실 분류와 구분된다. 계약(실패 시 예외)상 이 구현은 실패하지 않는다 —
 * 마스터가 비어 있지 않는 한 항상 유효 장르를 낸다.
 */
@Component
public class MockGenreClassifier implements GenreClassifier {

	@Override
	public GenreResult classify(GenreClassification input) {
		String seed = input == null || input.title() == null ? "" : input.title();
		int index = Math.floorMod(seed.hashCode(), GenreKeyword.all().size());
		return GenreResult.mock(GenreKeyword.all().get(index));
	}
}
