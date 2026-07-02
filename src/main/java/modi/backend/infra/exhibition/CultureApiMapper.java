package modi.backend.infra.exhibition;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.CatalogExhibitionData;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.support.error.CoreException;

/**
 * 한눈에보는문화정보(15138937) realm2/detail2 XML 파싱 + 도메인 매핑 전담(SRP).
 * {@link CultureExhibitionClient}는 전송(HTTP 호출)·오케스트레이션(페이지네이션·키 미설정 스킵·전송 오류 변환)만 맡고,
 * "XML을 정상 응답으로 해석"·"응답 필드를 도메인으로 정규화"하는 책임은 이 컴포넌트가 단독으로 진다.
 */
@Component
public class CultureApiMapper {

	private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final XmlMapper xmlMapper = new XmlMapper();
	private static final Pattern BR_TAG = Pattern.compile("(?i)<br\\s*/?>");

	/** XML을 파싱하고, 파싱 실패·비정상 응답(resultCode != 00)이면 {@link ExhibitionErrorCode#EXTERNAL_API_UNAVAILABLE}. */
	public CultureApiResponse parse(String xml) {
		CultureApiResponse body;
		try {
			body = xmlMapper.readValue(xml, CultureApiResponse.class);
		} catch (Exception e) {
			throw new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 전시 API 응답 파싱 실패", e);
		}
		if (!body.isSuccess()) {
			throw new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE,
					"외부 전시 API 비정상: " + (body.header() == null ? "null" : body.header().resultCode()));
		}
		return body;
	}

	public CatalogExhibitionData toCatalog(CultureApiResponse.Item item) {
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
				decode(blankToNull(item.area())));
	}

	public CatalogDetailData toDetail(CultureApiResponse.Item item) {
		return new CatalogDetailData(
				decode(blankToNull(item.price())), decodeDescription(blankToNull(item.contents1())), blankToNull(item.url()),
				decode(blankToNull(item.phone())), blankToNull(item.imgUrl()), blankToNull(item.placeUrl()),
				decode(blankToNull(item.placeAddr())), blankToNull(item.placeSeq()));
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

	/** contents1(설명)은 엔티티 디코딩 후 {@code <br/>} 계열 태그를 줄바꿈으로 정리해 읽기 좋게 만든다. */
	private static String decodeDescription(String value) {
		String decoded = decode(value);
		if (decoded == null) {
			return null;
		}
		return BR_TAG.matcher(decoded).replaceAll("\n");
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
