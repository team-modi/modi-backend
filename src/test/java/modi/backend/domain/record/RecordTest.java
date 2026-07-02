package modi.backend.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.support.error.CoreException;

class RecordTest {

	@Test
	@DisplayName("create — 전시 스냅샷을 필드에 박제해 저장한다")
	void create_전시스냅샷_저장() {
		ExhibitionSnapshot snap = new ExhibitionSnapshot("모네전", "CATALOG", "http://p", "예술의전당", "SEOUL", "PAINTING",
				LocalDate.now(), null);

		Record r = Record.create(1L, 10L, snap, WriteMode.DIRECT, LocalDate.now(), "감상", null, null, null,
				AiStatus.PENDING);

		assertThat(r.getExhibitionTitle()).isEqualTo("모네전");
		assertThat(r.getExhibitionType()).isEqualTo("CATALOG");
		assertThat(r.getExhibitionPosterUrl()).isEqualTo("http://p");
		assertThat(r.getExhibitionPlace()).isEqualTo("예술의전당");
		assertThat(r.getExhibitionRegion()).isEqualTo("SEOUL");
		assertThat(r.getExhibitionCategory()).isEqualTo("PAINTING");
		assertThat(r.getExhibitionStartDate()).isEqualTo(LocalDate.now());
		assertThat(r.getExhibitionEndDate()).isNull();
	}

	@Test
	@DisplayName("ExhibitionSnapshot — 제목이 비어 있으면 생성을 거부한다")
	void snapshot_제목없으면_거부() {
		assertThatThrownBy(() -> new ExhibitionSnapshot(" ", "CATALOG", null, null, null, null, null, null))
				.isInstanceOf(CoreException.class);
	}

	@Test
	@DisplayName("replaceContent — 스냅샷은 변경되지 않는다(불변)")
	void replaceContent_스냅샷_불변() {
		ExhibitionSnapshot snap = new ExhibitionSnapshot("모네전", "CATALOG", "http://p", "예술의전당", "SEOUL", "PAINTING",
				LocalDate.now(), null);
		Record r = Record.create(1L, 10L, snap, WriteMode.DIRECT, LocalDate.now(), "감상", null, null, null,
				AiStatus.PENDING);

		r.replaceContent(LocalDate.now(), "수정한 감상", "요약", "MOVED", "문구", AiStatus.READY);

		assertThat(r.getExhibitionTitle()).isEqualTo("모네전");
		assertThat(r.getExhibitionRegion()).isEqualTo("SEOUL");
		assertThat(r.getContent()).isEqualTo("수정한 감상");
	}
}
