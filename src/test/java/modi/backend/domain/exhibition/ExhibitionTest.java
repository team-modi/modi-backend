package modi.backend.domain.exhibition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExhibitionTest {

	@Test
	@DisplayName("applyDetail — 상세 필드를 채우고 동기화 시각을 기록한다")
	void applyDetail_상세필드_채우고_동기화시각기록() {
		Exhibition e = Exhibition.createCatalog("S1", "t", "p", null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null);

		assertThat(e.isDetailSynced()).isFalse();

		e.applyDetail(new CatalogDetailData("무료", "소개", "http://u", "010", "http://img", "http://pu", "서울 어딘가",
				"place-seq-1"));

		assertThat(e.getPrice()).isEqualTo("무료");
		assertThat(e.getDescription()).isEqualTo("소개");
		assertThat(e.getDetailUrl()).isEqualTo("http://u");
		assertThat(e.getPhone()).isEqualTo("010");
		assertThat(e.getImgUrl()).isEqualTo("http://img");
		assertThat(e.getPlaceUrl()).isEqualTo("http://pu");
		assertThat(e.getPlaceAddr()).isEqualTo("서울 어딘가");
		assertThat(e.getPlaceSeq()).isEqualTo("place-seq-1");
		assertThat(e.getDetailSyncedAt()).isNotNull();
		assertThat(e.isDetailSynced()).isTrue();
	}

	@Test
	@DisplayName("applyDetail — detailUrl이 null이면 기존 detailUrl을 유지한다")
	void applyDetail_detailUrl이_null이면_기존값_유지() {
		Exhibition e = Exhibition.createCatalog("S1b", "t", "p", null, null, null, null, null, null, null, null,
				"http://original", null, null, null, null, null, null);

		e.applyDetail(new CatalogDetailData("무료", "소개", null, "010", "http://img", "http://pu", "서울 어딘가", "seq"));

		assertThat(e.getDetailUrl()).isEqualTo("http://original");
	}

	@Test
	@DisplayName("refreshCatalog — 목록 재동기화가 상세2 필드(price·description)를 null로 덮어쓰지 않는다")
	void refreshCatalog_상세필드_보존() {
		Exhibition e = Exhibition.createCatalog("S3", "옛제목", "옛장소", null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null);
		// 상세 지연수집/백필로 price·description을 채운 상태
		e.applyDetail(new CatalogDetailData("무료", "상세 소개", "http://u", "010", "http://img", "http://pu", "서울", "seq"));
		assertThat(e.getPrice()).isEqualTo("무료");

		// 목록만 있는 정기 재동기화(상세2 필드 없음) — 목록 필드는 갱신하되 상세2는 건드리지 않아야 한다
		e.refreshCatalog("새제목", "새장소", null, null, null, null, "http://poster", "http://detail", "svc", 1.0, 2.0,
				"강남구", "전시", "서울");

		assertThat(e.getTitle()).isEqualTo("새제목"); // 목록 필드는 갱신됨
		assertThat(e.getPlace()).isEqualTo("새장소");
		assertThat(e.getPrice()).isEqualTo("무료"); // 상세2 필드는 보존 → 무료 판정·무료 섹션 유지
		assertThat(e.isFree()).isTrue();
		assertThat(e.getDescription()).isEqualTo("상세 소개");
		assertThat(e.isDetailSynced()).isTrue(); // 이미 상세를 채운 행은 백필 대상에서 제외됨
	}

	@Test
	@DisplayName("increaseView — 조회수가 1 증가한다")
	void increaseView_조회수증가() {
		Exhibition e = Exhibition.createCatalog("S2", "t", "p", null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null);
		long before = e.getOurViewCount();

		e.increaseView();

		assertThat(e.getOurViewCount()).isEqualTo(before + 1);
	}
}
