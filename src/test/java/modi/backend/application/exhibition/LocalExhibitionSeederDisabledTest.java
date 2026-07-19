package modi.backend.application.exhibition;

import modi.backend.ingestion.application.seed.LocalExhibitionSeeder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import modi.backend.TestcontainersConfiguration;
import modi.backend.ingestion.domain.port.ExhibitionCatalogClient;

/**
 * 플래그 off(운영/테스트 기본)에서는 로컬 시더 빈이 아예 뜨지 않고, 시드도 적재되지 않음을 본다.
 * catalogClient를 목으로 두어 BootSync가 실행되더라도 외부 호출·적재가 없게 한다(빈 목록 반환).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.local-seed.enabled=false")
class LocalExhibitionSeederDisabledTest {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	ObjectProvider<LocalExhibitionSeeder> seederProvider;

	@MockitoBean
	ExhibitionCatalogClient exhibitionCatalogClient;

	@Test
	@DisplayName("플래그 off → 시더 빈 부재 + 시드 미적재")
	void 플래그off_시더미실행() {
		assertThat(seederProvider.getIfAvailable()).isNull(); // @ConditionalOnProperty(havingValue=true) → 빈 없음
		assertThat(jdbcTemplate.queryForObject("select count(*) from exhibitions", Long.class)).isZero();
	}
}
