package modi.backend.domain.exhibition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.support.error.CoreException;

class ExhibitionFormatTest {

	@Test
	@DisplayName("from — 정의된 코드는 대소문자 무관하게 변환")
	void from_정상() {
		assertThat(ExhibitionFormat.from("SOLO")).isEqualTo(ExhibitionFormat.SOLO);
		assertThat(ExhibitionFormat.from("art_fair")).isEqualTo(ExhibitionFormat.ART_FAIR);
	}

	@Test
	@DisplayName("from — 정의되지 않은 코드는 INVALID_INPUT")
	void from_미정의_실패() {
		assertThatThrownBy(() -> ExhibitionFormat.from("UNKNOWN")).isInstanceOf(CoreException.class);
		assertThatThrownBy(() -> ExhibitionFormat.from(null)).isInstanceOf(CoreException.class);
	}
}
