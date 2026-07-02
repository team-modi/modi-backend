package modi.backend.infra.exhibition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.CatalogExhibitionData;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.support.error.CoreException;

/**
 * {@link CultureApiMapper} 순수 단위 테스트(HTTP 없음) — XML 문자열 → {@link CultureApiResponse} 파싱 +
 * 도메인({@link CatalogExhibitionData}/{@link CatalogDetailData}) 매핑을 검증한다.
 * 샘플은 {@link CultureApiResponseTest}·{@link CultureExhibitionClientTest}와 동일 표본(seq=319005, 부산/사하구)을 재사용한다.
 */
class CultureApiMapperTest {

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

	private static final String ERROR_XML = "<response><header><resultCode>99</resultCode><resultMsg>ERR</resultMsg></header><body/></response>";

	private static final String REALM2_ESCAPED_XML = "<response><header><resultCode>00</resultCode><resultMsg>정상입니다.</resultMsg></header>"
			+ "<body><totalCount>1</totalCount><PageNo>1</PageNo><numOfrows>100</numOfrows><items>"
			+ "<item><serviceName>전시</serviceName><seq>319006</seq><title>이사라 &amp;lt;A Girl From Wonderland&amp;gt;</title>"
			+ "<startDate>20180616</startDate><endDate>20281231</endDate><place>서울 &amp;amp; 경기 전시관</place>"
			+ "<realmName>전시</realmName><area>부산</area><sigungu>사하구</sigungu>"
			+ "<thumbnail>http://t/x.jpg</thumbnail><gpsX>128.9</gpsX><gpsY>35.1</gpsY></item>"
			+ "</items></body></response>";

	private static final String DETAIL2_ESCAPED_XML = "<response><header><resultCode>00</resultCode><resultMsg>정상입니다.</resultMsg></header>"
			+ "<body><totalCount>1</totalCount><items>"
			+ "<item><seq>319006</seq><title>이사라 &amp;lt;A Girl From Wonderland&amp;gt;</title><price>무료</price>"
			+ "<contents1>첫째 줄입니다.&amp;lt;br/&amp;gt;둘째 줄입니다.</contents1><url>http://detail/319006</url><phone>051-000-0000</phone>"
			+ "<imgUrl>http://img/x.jpg</imgUrl><placeUrl>http://place/x</placeUrl>"
			+ "<placeAddr>부산광역시 사하구 낙동남로 1191</placeAddr><placeSeq>P1</placeSeq></item>"
			+ "</items></body></response>";

	private final CultureApiMapper mapper = new CultureApiMapper();

	@Test
	@DisplayName("parse — 정상 응답이면 items 접근 가능")
	void parse_성공() {
		CultureApiResponse response = mapper.parse(REALM2_XML);

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.items()).hasSize(1);
	}

	@Test
	@DisplayName("parse — resultCode 비정상이면 EXTERNAL_API_UNAVAILABLE")
	void parse_비정상_resultCode() {
		assertThatThrownBy(() -> mapper.parse(ERROR_XML))
				.isInstanceOf(CoreException.class);
	}

	@Test
	@DisplayName("parse — XML 자체가 깨졌으면 EXTERNAL_API_UNAVAILABLE")
	void parse_파싱실패() {
		assertThatThrownBy(() -> mapper.parse("not-xml"))
				.isInstanceOf(CoreException.class);
	}

	@Test
	@DisplayName("toCatalog — area→region=BUSAN·sigungu·realmName·areaText 매핑")
	void toCatalog_매핑() {
		CultureApiResponse.Item item = mapper.parse(REALM2_XML).items().get(0);

		CatalogExhibitionData data = mapper.toCatalog(item);

		assertThat(data.externalId()).isEqualTo("319005");
		assertThat(data.region()).isEqualTo(ExhibitionRegion.BUSAN);
		assertThat(data.sigungu()).isEqualTo("사하구");
		assertThat(data.realmName()).isEqualTo("전시");
		assertThat(data.areaText()).isEqualTo("부산");
		assertThat(data.startDate()).isNotNull();
	}

	@Test
	@DisplayName("toDetail — price·placeAddr·placeSeq 매핑")
	void toDetail_매핑() {
		CultureApiResponse.Item item = mapper.parse(DETAIL2_XML).items().get(0);

		CatalogDetailData detail = mapper.toDetail(item);

		assertThat(detail.price()).isEqualTo("무료");
		assertThat(detail.placeAddr()).isEqualTo("부산광역시 사하구 낙동남로 1191");
		assertThat(detail.placeSeq()).isEqualTo("P1");
	}

	@Test
	@DisplayName("toCatalog — 원본이 HTML 엔티티로 이스케이프돼 있으면 디코딩해 저장한다")
	void toCatalog_HTML_엔티티_디코딩() {
		CultureApiResponse.Item item = mapper.parse(REALM2_ESCAPED_XML).items().get(0);

		CatalogExhibitionData data = mapper.toCatalog(item);

		assertThat(data.title()).isEqualTo("이사라 <A Girl From Wonderland>");
		assertThat(data.place()).isEqualTo("서울 & 경기 전시관");
	}

	@Test
	@DisplayName("toDetail — description의 HTML 엔티티를 디코딩하고 br 태그를 줄바꿈으로 정리한다")
	void toDetail_HTML_엔티티_디코딩() {
		CultureApiResponse.Item item = mapper.parse(DETAIL2_ESCAPED_XML).items().get(0);

		CatalogDetailData detail = mapper.toDetail(item);

		assertThat(detail.description()).isEqualTo("첫째 줄입니다.\n둘째 줄입니다.");
	}
}
