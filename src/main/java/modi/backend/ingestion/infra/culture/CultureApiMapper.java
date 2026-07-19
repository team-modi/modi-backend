package modi.backend.ingestion.infra.culture;

import static modi.backend.domain.exhibition.catalog.ExhibitionErrorCode.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import modi.backend.ingestion.domain.data.CatalogDetailData;
import modi.backend.ingestion.domain.data.CatalogExhibitionData;
import modi.backend.domain.exhibition.catalog.ExhibitionCategory;
import modi.backend.domain.exhibition.catalog.ExhibitionErrorCode;
import modi.backend.domain.exhibition.catalog.ExhibitionRegion;
import modi.backend.support.error.CoreException;
import modi.backend.support.text.HtmlTextExtractor;

/**
 * 한눈에보는문화정보(15138937) realm2/detail2 XML 파싱 + 도메인 매핑 전담(SRP).
 * {@link CultureExhibitionClient}는 전송(HTTP 호출)·오케스트레이션(페이지네이션·키 미설정 스킵·전송 오류 변환)만 맡고,
 * "XML을 정상 응답으로 해석"·"응답 필드를 도메인으로 정규화"하는 책임은 이 컴포넌트가 단독으로 진다.
 */
@Component
public class CultureApiMapper {

	private static final Logger log = LoggerFactory.getLogger(CultureApiMapper.class);
	private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final XmlMapper xmlMapper = new XmlMapper();
	private static final ObjectMapper jsonMapper = new ObjectMapper()
			.setSerializationInclusion(JsonInclude.Include.NON_NULL);

	public CultureApiResponse parse(String xml) {
		CultureApiResponse body;
		try {
			body = xmlMapper.readValue(xml, CultureApiResponse.class);
		} catch (Exception e) {
			throw new CoreException(EXTERNAL_API_UNAVAILABLE, "외부 전시 API 응답 파싱 실패", e);
		}
		if (!body.isSuccess()) {
			// 표준 코드를 사람이 읽는 라벨로 남긴다 — 운영 로그에서 "왜 실패했나"(한도초과 vs 키오류)를 코드 암기 없이 판독.
			String resultCode = body.header() == null ? null : body.header().resultCode();
			throw new CoreException(EXTERNAL_API_UNAVAILABLE,
					"외부 전시 API 비정상: " + CultureResultCode.describe(resultCode));
		}
		return body;
	}

	/**
	 * 응답 아이템 하나를 벤더층 payload(JSON)로 직렬화한다 — {@code culture_*_response.payload} 적재용.
	 *
	 * <p><b>왜 응답 XML을 그대로 자르지 않고 매핑된 구조를 남기나</b>: 원천의 응답 구조가 실측으로 확정돼 있기 때문이다
	 * (2026-07-15, {@code docs/개인 폴더/전시/공공데이터-제공률-실측과-DB빈값-분석.md}). 목록 전수 279건·상세 60건의
	 * <b>응답 태그를 전수 집계</b>한 결과 realm2는 12필드, detail2는 18태그이고, 그 합집합이 {@link CultureApiResponse.Item}이
	 * 선언한 20필드와 정확히 일치한다. 즉 <b>원천에 우리가 안 받는 필드가 존재하지 않으므로</b> 이 직렬화는 무손실이다.
	 * ({@code @JsonIgnoreProperties(ignoreUnknown = true)}는 미래 대비 방어일 뿐, 지금 버리는 필드가 있다는 뜻이 아니다.)
	 *
	 * <p>덕분에 원문 조각을 잘라 인덱스로 짝짓는 경로가 통째로 사라진다 — 짝이 밀리면 <b>A 전시의 원본이 B에 붙어</b>
	 * 재파싱 원료가 오염되는데, 아이템 객체에서 바로 직렬화하면 그 사고가 원인부터 불가능하다.
	 * 코드베이스의 기존 관행({@code google_place_response.raw_json})과도 같은 방식이다.
	 *
	 * <p>도메인 변환({@code decode()}·HTML 평문 추출) <b>이전</b> 값이라 재파싱 원료로서 온전하다 — 특히
	 * {@code contents1}의 워드프레스 HTML 원문이 보존되어, 평문 추출 규칙이 바뀌면 여기서 다시 뽑을 수 있다.
	 *
	 * <p><b>잔여 위험</b>: 원천이 <i>나중에</i> 필드를 추가하면 파싱 시 버려져 payload에도 남지 않는다.
	 * 실측 기준으로 지금은 그런 필드가 없고, 추가되면 {@code Item}에 선언을 더하는 것이 정상 대응이다.
	 */
	public String payloadOf(CultureApiResponse.Item item) {
		if (item == null) {
			return null;
		}
		try {
			return jsonMapper.writeValueAsString(item);
		} catch (Exception e) {
			// 원본 적재는 부가 기록이다 — 직렬화가 실패해도 수집·적재를 깨지 않고 payload 없이 진행한다.
			log.debug("응답 원본 직렬화 실패(무시): {}", e.getMessage());
			return null;
		}
	}

	public CatalogExhibitionData toCatalog(CultureApiResponse.Item item, String payload) {
		return new CatalogExhibitionData(
				item.seq(),
				decode(item.title()),
				decode(blankToNull(item.place())),
				parseDate(item.startDate()),
				parseDate(item.endDate()),
				ExhibitionRegion.fromAreaText(item.area()),
				ExhibitionCategory.fromRealmName(item.realmName()),
				blankToNull(item.thumbnail()),
				blankToNull(item.url()),
				blankToNull(item.serviceName()),
				parseCoordinate(item.gpsX()),
				parseCoordinate(item.gpsY()),
				blankToNull(item.sigungu()),
				decode(blankToNull(item.realmName())),
				decode(blankToNull(item.area())),
				payload);
	}

	public CatalogDetailData toDetail(CultureApiResponse.Item item, String payload) {
		return new CatalogDetailData(
				decode(blankToNull(item.price())), decodeDescription(blankToNull(item.contents1())), blankToNull(item.url()),
				decode(blankToNull(item.phone())), blankToNull(item.imgUrl()), blankToNull(item.placeUrl()),
				decode(blankToNull(item.placeAddr())), blankToNull(item.placeSeq()), payload);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	/**
	 * 공공데이터 원문이 XML-escape된 채로 내려오므로(예: {@code &lt;A Girl&gt;}), 사람이 읽을 텍스트 필드는
	 * 저장 전 원래 값으로 되돌린다. 의미를 바꾸는 가공이 아니라 소스가 이스케이프한 것을 원복하는 정규화다.
	 * 순서 주의: 명명 엔티티·숫자 엔티티를 먼저 풀고 {@code &amp;}는 마지막에 풀어야 이중 디코딩을 피한다.
	 */
	private static String decode(String value) {
		// 모든 HTML4 명명·숫자 엔티티(&lt; &amp; &middot; &ndash; &#nnn; 등)를 스프링 표준 디코더로 일괄 처리한다.
		return value == null ? null : HtmlUtils.htmlUnescape(value);
	}

	/**
	 * contents1(설명)은 원천이 워드프레스 블록/HTML(예: {@code <!-- wp:paragraph --><p style="…">…</p>})로 내려주므로
	 * {@link HtmlTextExtractor}로 태그를 벗겨 <b>읽기 좋은 평문</b>으로 만든다(최초 수집 파싱과 기존 데이터 재파싱이 같은 규칙 공유).
	 */
	private static String decodeDescription(String value) {
		return HtmlTextExtractor.toPlainText(value);
	}

	/** YYYYMMDD 8자리만 파싱, 그 외/결측은 null. */
	private static LocalDate parseDate(String value) {
		String text = blankToNull(value);
		if (text == null || text.length() != 8) {
			return null;
		}
		try {
			return LocalDate.parse(text, YYYYMMDD);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static Double parseCoordinate(String value) {
		String text = blankToNull(value);
		if (text == null) {
			return null;
		}
		try {
			return Double.parseDouble(text);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
