package modi.backend.application.exhibition;

import modi.backend.application.exhibition.sync.LocalExhibitionSeeder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.exhibition.sync.ExhibitionCatalogClient;

/**
 * 로컬 시드 적재기 검증(@SpringBootTest + Testcontainers-MySQL). 플래그 on + 빈 DB로 기동하면 부팅 시 classpath 시드 SQL이
 * 적재되어 각 테이블 건수가 맞는지, 그리고 데이터가 이미 있으면 재적재하지 않는지(멱등)를 본다.
 * BootSync는 이 플래그가 켜지면 skip되므로 외부 API를 호출하지 않는다(catalogClient는 안전하게 목으로 둔다).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.local-seed.enabled=true")
class LocalExhibitionSeederTest {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	LocalExhibitionSeeder localExhibitionSeeder;

	@MockitoBean
	ExhibitionCatalogClient exhibitionCatalogClient;

	@Test
	@DisplayName("플래그 on + 빈 DB → 부팅 시 시드 적재(코어·장소·상세·영업시간·장르 건수) + 재실행 멱등")
	void 부팅시_시드적재_및_멱등() {
		// 부팅(ApplicationRunner)에서 이미 1회 적재됐다.
		assertThat(count("exhibitions")).isEqualTo(313);
		assertThat(count("exhibition_place")).isEqualTo(229);
		assertThat(count("exhibition_detail")).isEqualTo(313); // 스냅샷 293 + 더미 보강 20
		assertThat(count("place_hours")).isEqualTo(229);       // 장소당 1행(더미 영업시간)
		assertThat(count("exhibition_genre")).isEqualTo(313);
		// 참조 무결성 — 모든 전시가 유효한 전시장을 가리킨다(코어 NOT NULL·N:1).
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from exhibitions e left join exhibition_place p on p.id=e.exhibition_place_id "
						+ "where p.id is null", Long.class)).isZero();

		// 데이터가 있는 상태로 다시 실행 → 아무것도 안 함(재적재 없음, 건수 불변).
		localExhibitionSeeder.run(null);

		assertThat(count("exhibitions")).isEqualTo(313);
		assertThat(count("exhibition_place")).isEqualTo(229);
		assertThat(count("exhibition_detail")).isEqualTo(313);
	}

	private long count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
	}
}
