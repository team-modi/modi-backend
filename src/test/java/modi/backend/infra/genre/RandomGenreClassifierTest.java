package modi.backend.infra.genre;

import modi.backend.infra.exhibition.sync.mock.RandomGenreClassifier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.domain.exhibition.sync.data.GenreClassification;
import modi.backend.domain.exhibition.genre.GenreKeyword;
import modi.backend.domain.exhibition.genre.GenreProvider;

class RandomGenreClassifierTest {

	private final RandomGenreClassifier classifier = new RandomGenreClassifier();

	@Test
	@DisplayName("랜덤 분류기는 입력과 무관하게 마스터 장르 중 하나를 반환한다")
	void classify_returnsMasterGenre() {
		GenreClassification input = new GenreClassification("모네 특별전", "PAINTING", null, "한가람미술관", null, "전시");

		for (int i = 0; i < 50; i++) {
			assertThat(GenreKeyword.all()).contains(classifier.classify(input).genreKeyword());
		}
	}

	@Test
	@DisplayName("빈 입력에도 유효한 장르를 반환한다")
	void classify_emptyInput_returnsMasterGenre() {
		GenreClassification empty = new GenreClassification(null, null, null, null, null, null);

		assertThat(GenreKeyword.all()).contains(classifier.classify(empty).genreKeyword());
	}

	@Test
	@DisplayName("산출물엔 provider=RANDOM이 붙는다 — 이 표식이 나중에 선별 재분류의 유일한 근거다")
	void classify_marksProviderRandom() {
		GenreClassification input = new GenreClassification("모네 특별전", "PAINTING", null, "한가람미술관", null, "전시");

		assertThat(classifier.classify(input).provider()).isEqualTo(GenreProvider.RANDOM);
	}
}
