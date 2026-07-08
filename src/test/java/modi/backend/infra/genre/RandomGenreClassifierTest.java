package modi.backend.infra.genre;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.domain.exhibition.GenreClassification;
import modi.backend.domain.exhibition.GenreKeyword;

class RandomGenreClassifierTest {

	private final RandomGenreClassifier classifier = new RandomGenreClassifier();

	@Test
	@DisplayName("랜덤 분류기는 입력과 무관하게 마스터 장르 중 하나를 반환한다")
	void classify_returnsMasterGenre() {
		GenreClassification input = new GenreClassification("모네 특별전", "PAINTING", null, "한가람미술관", null, "전시");

		for (int i = 0; i < 50; i++) {
			assertThat(GenreKeyword.all()).contains(classifier.classify(input));
		}
	}

	@Test
	@DisplayName("빈 입력에도 유효한 장르를 반환한다")
	void classify_emptyInput_returnsMasterGenre() {
		GenreClassification empty = new GenreClassification(null, null, null, null, null, null);

		assertThat(GenreKeyword.all()).contains(classifier.classify(empty));
	}
}
