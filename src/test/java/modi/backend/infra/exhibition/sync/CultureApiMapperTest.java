package modi.backend.infra.exhibition.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.domain.exhibition.sync.data.CatalogDetailData;
import modi.backend.domain.exhibition.sync.data.CatalogExhibitionData;
import modi.backend.domain.exhibition.catalog.ExhibitionRegion;
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

	// 실제 원천 표본: 워드프레스 블록 주석 + <p>/<span style> 태그로 감싼 본문(단일 이스케이프). 배민정 전시 실데이터 축약.
	private static final String DETAIL2_WORDPRESS_XML = "<response><header><resultCode>00</resultCode><resultMsg>정상입니다.</resultMsg></header>"
			+ "<body><totalCount>1</totalCount><items>"
			+ "<item><seq>319007</seq><title>TRACE</title><price>무료</price>"
			+ "<contents1>&lt;!-- wp:paragraph --&gt;&lt;p&gt;배민정 작가는 자신의 일상에서 떠오른 감성적 주제를 AI에 입력한다.&lt;/p&gt;&lt;!-- /wp:paragraph --&gt;"
			+ "&lt;!-- wp:paragraph --&gt;&lt;p&gt;이번 전시 [ TRACE : 생성 과정의 잔여 ]에서는 &lt;span style=&quot;font-size: 10pt;&quot;&gt;잔여&lt;/span&gt;에 주목한다.&lt;/p&gt;&lt;!-- /wp:paragraph --&gt;</contents1>"
			+ "<url>http://detail/319007</url></item>"
			+ "</items></body></response>";

	// 이중 이스케이프 표본(&amp;lt;p&amp;gt;) — XML 파싱 1단계 + 재해제 1단계로 태그까지 벗겨져야 한다.
	private static final String DETAIL2_DOUBLE_ESCAPED_XML = "<response><header><resultCode>00</resultCode><resultMsg>정상입니다.</resultMsg></header>"
			+ "<body><totalCount>1</totalCount><items>"
			+ "<item><seq>319008</seq><title>이중</title><price>무료</price>"
			+ "<contents1>&amp;lt;p style=&amp;quot;line-height:1.8;&amp;quot;&amp;gt;소장품 이야기&amp;lt;/p&amp;gt;</contents1>"
			+ "<url>http://detail/319008</url></item>"
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
	@DisplayName("payloadOf — 응답 아이템이 준 필드를 빠짐없이 JSON으로 직렬화한다(실측 기준 무손실)")
	void payloadOf_전필드_직렬화() {
		CultureApiResponse.Item item = mapper.parse(REALM2_XML).items().get(0);

		String payload = mapper.payloadOf(item);

		// 실측(realm2 12필드)이 확정한 목록 응답의 전 필드가 payload에 남아야 한다 — 하나라도 빠지면 재파싱 원료가 아니다.
		assertThat(payload).contains("\"seq\":\"319005\"").contains("\"title\":\"패트릭 블랑\"")
				.contains("\"startDate\":\"20180616\"").contains("\"endDate\":\"20281231\"")
				.contains("\"place\":\"부산현대미술관\"").contains("\"realmName\":\"전시\"")
				.contains("\"area\":\"부산\"").contains("\"sigungu\":\"사하구\"")
				.contains("\"thumbnail\":\"http://t/x.jpg\"").contains("\"gpsX\":\"128.9\"")
				.contains("\"gpsY\":\"35.1\"").contains("\"serviceName\":\"전시\"");
	}

	@Test
	@DisplayName("payloadOf — 원천이 주지 않은 필드는 싣지 않는다(안 준 것과 빈 값을 뒤섞지 않는다)")
	void payloadOf_결측필드_제외() {
		CultureApiResponse.Item item = mapper.parse(REALM2_XML).items().get(0);

		String payload = mapper.payloadOf(item);

		// 목록 응답엔 상세 필드가 없다 — null로 채워 넣으면 payload만 비대해지고 "원천이 뭘 줬나"가 흐려진다.
		assertThat(payload).doesNotContain("\"price\"").doesNotContain("\"contents1\"")
				.doesNotContain("\"placeAddr\"");
	}

	@Test
	@DisplayName("payloadOf — 상세의 워드프레스 HTML 본문을 도메인 변환 전 원문 그대로 보존한다(재추출 원료)")
	void payloadOf_HTML원문_보존() {
		CultureApiResponse.Item item = mapper.parse(DETAIL2_WORDPRESS_XML).items().get(0);

		String payload = mapper.payloadOf(item);

		// 평문 추출은 toDetail의 몫이고, payload엔 그 이전 원문이 남아야 한다 — 규칙이 바뀌면 여기서 다시 뽑는다.
		assertThat(payload).contains("wp:paragraph");
		assertThat(mapper.toDetail(item, payload).description()).doesNotContain("wp:paragraph"); // 도메인 값은 평문
	}

	@Test
	@DisplayName("payloadOf — 아이템이 없으면 null(적재를 건너뛴다)")
	void payloadOf_없으면_null() {
		assertThat(mapper.payloadOf(null)).isNull();
	}

	@Test
	@DisplayName("toCatalog — area→region=BUSAN·sigungu·realmName·areaText 매핑")
	void toCatalog_매핑() {
		CultureApiResponse.Item item = mapper.parse(REALM2_XML).items().get(0);

		CatalogExhibitionData data = mapper.toCatalog(item, null);

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

		CatalogDetailData detail = mapper.toDetail(item, null);

		assertThat(detail.price()).isEqualTo("무료");
		assertThat(detail.placeAddr()).isEqualTo("부산광역시 사하구 낙동남로 1191");
		assertThat(detail.placeSeq()).isEqualTo("P1");
	}

	@Test
	@DisplayName("toCatalog — 원본이 HTML 엔티티로 이스케이프돼 있으면 디코딩해 저장한다")
	void toCatalog_HTML_엔티티_디코딩() {
		CultureApiResponse.Item item = mapper.parse(REALM2_ESCAPED_XML).items().get(0);

		CatalogExhibitionData data = mapper.toCatalog(item, null);

		assertThat(data.title()).isEqualTo("이사라 <A Girl From Wonderland>");
		assertThat(data.place()).isEqualTo("서울 & 경기 전시관");
	}

	@Test
	@DisplayName("toDetail — description의 HTML 엔티티를 디코딩하고 br 태그를 줄바꿈으로 정리한다")
	void toDetail_HTML_엔티티_디코딩() {
		CultureApiResponse.Item item = mapper.parse(DETAIL2_ESCAPED_XML).items().get(0);

		CatalogDetailData detail = mapper.toDetail(item, null);

		assertThat(detail.description()).isEqualTo("첫째 줄입니다.\n둘째 줄입니다.");
	}

	@Test
	@DisplayName("toDetail — 워드프레스 블록 주석·<p>·<span> 태그를 벗겨 읽기 좋은 평문으로 만든다")
	void toDetail_워드프레스_태그제거() {
		CultureApiResponse.Item item = mapper.parse(DETAIL2_WORDPRESS_XML).items().get(0);

		CatalogDetailData detail = mapper.toDetail(item, null);

		String desc = detail.description();
		// 태그·주석·이스케이프 잔재가 남지 않아야 한다
		assertThat(desc).doesNotContain("<").doesNotContain("&lt;").doesNotContain("wp:paragraph")
				.doesNotContain("style=");
		// 본문 텍스트는 보존되고 문단은 개행으로 구분된다
		assertThat(desc).contains("배민정 작가는").contains("TRACE : 생성 과정의 잔여").contains("잔여에 주목한다");
		assertThat(desc).isEqualTo("배민정 작가는 자신의 일상에서 떠오른 감성적 주제를 AI에 입력한다.\n"
				+ "이번 전시 [ TRACE : 생성 과정의 잔여 ]에서는 잔여에 주목한다.");
	}

	@Test
	@DisplayName("toDetail — 이중 이스케이프(&lt;p&gt;)된 본문도 태그까지 완전히 벗겨낸다")
	void toDetail_이중이스케이프_태그제거() {
		CultureApiResponse.Item item = mapper.parse(DETAIL2_DOUBLE_ESCAPED_XML).items().get(0);

		CatalogDetailData detail = mapper.toDetail(item, null);

		assertThat(detail.description()).isEqualTo("소장품 이야기");
	}
}
