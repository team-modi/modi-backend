package modi.backend.infra.exhibition;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import modi.backend.config.PublicDataProperties;
import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.CatalogExhibitionData;
import modi.backend.domain.exhibition.ExhibitionRegion;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * {@link CultureExhibitionClient} — 실 HTTP(MockWebServer)로 realm2 목록·detail2 상세 파싱/매핑을 검증한다.
 * XML 샘플은 {@link CultureApiResponseTest}의 검증 표본(seq=319005, 부산/사하구)을 재사용한다.
 */
class CultureExhibitionClientTest {

	private static final String REALM2_XML = "<response><header><resultCode>00</resultCode><resultMsg>정상입니다.</resultMsg></header>"
			+ "<body><totalCount>1</totalCount><PageNo>1</PageNo><numOfrows>100</numOfrows><items>"
			+ "<item><serviceName>전시</serviceName><seq>319005</seq><title>패트릭 블랑</title>"
			+ "<startDate>20180616</startDate><endDate>20281231</endDate><place>부산현대미술관</place>"
			+ "<realmName>전시</realmName><area>부산</area><sigungu>사하구</sigungu>"
			+ "<thumbnail>http://t/x.jpg</thumbnail><gpsX>128.9</gpsX><gpsY>35.1</gpsY></item>"
			+ "</items></body></response>";

	private static final String DETAIL2_XML = "<response><header><resultCode>00</resultCode><resultMsg>정상입니다.</resultMsg></header>"
			+ "<body><totalCount>1</totalCount><items>"
			+ "<item><seq>319005</seq><title>패트릭 블랑</title><price>무료</price>"
			+ "<contents1>설명입니다.</contents1><url>http://detail/319005</url><phone>051-000-0000</phone>"
			+ "<imgUrl>http://img/x.jpg</imgUrl><placeUrl>http://place/x</placeUrl>"
			+ "<placeAddr>부산광역시 사하구 낙동남로 1191</placeAddr><placeSeq>P1</placeSeq></item>"
			+ "</items></body></response>";

	private MockWebServer server;
	private CultureExhibitionClient client;

	@BeforeEach
	void setUp() throws IOException {
		server = new MockWebServer();
		server.start();
		String baseUrl = "http://localhost:" + server.getPort();
		WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
		CultureApi cultureApi = HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient)).build()
				.createClient(CultureApi.class);
		PublicDataProperties properties = new PublicDataProperties(baseUrl, "test-service-key", "D000", 100, 5, 15L);
		client = new CultureExhibitionClient(cultureApi, new CultureApiMapper(), properties);
	}

	@AfterEach
	void tearDown() throws IOException {
		server.shutdown();
	}

	@Test
	@DisplayName("fetchAll — realm2 XML 파싱, area→region=BUSAN·sigungu·realmName·areaText 매핑")
	void fetchAll_realm2_파싱() {
		server.enqueue(new MockResponse().setBody(REALM2_XML).addHeader("Content-Type", "application/xml"));

		List<CatalogExhibitionData> result = client.fetchAll();

		assertThat(result).hasSize(1);
		CatalogExhibitionData item = result.get(0);
		assertThat(item.externalId()).isEqualTo("319005");
		assertThat(item.region()).isEqualTo(ExhibitionRegion.BUSAN);
		assertThat(item.sigungu()).isEqualTo("사하구");
		assertThat(item.realmName()).isEqualTo("전시");
		assertThat(item.areaText()).isEqualTo("부산");
	}

	@Test
	@DisplayName("fetchDetail — detail2 XML 파싱, price·placeAddr·placeSeq 매핑")
	void fetchDetail_detail2_파싱() {
		server.enqueue(new MockResponse().setBody(DETAIL2_XML).addHeader("Content-Type", "application/xml"));

		Optional<CatalogDetailData> result = client.fetchDetail("319005");

		assertThat(result).isPresent();
		assertThat(result.get().price()).isEqualTo("무료");
		assertThat(result.get().placeAddr()).isEqualTo("부산광역시 사하구 낙동남로 1191");
		assertThat(result.get().placeSeq()).isEqualTo("P1");
	}
}
