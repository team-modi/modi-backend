package modi.backend.ingestion.infra.culture;

import static modi.backend.domain.exhibition.catalog.ExhibitionErrorCode.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import modi.backend.ingestion.domain.data.CatalogVendorItem;
import org.springframework.web.util.HtmlUtils;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import modi.backend.domain.exhibition.catalog.CatalogDetailData;
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
	 * 응답 아이템 하나를 벤더 스냅샷 어휘(원문 verbatim)로 옮긴다 — {@code culture_*_snapshot} 적재용(ADR-13).
	 *
	 * <p>도메인 변환({@code decode()}·HTML 평문 추출) <b>이전</b> 값이라 재파싱 원료로서 온전하다 — 특히
	 * {@code contents1}의 워드프레스 HTML 원문이 보존되어, 평문 추출 규칙이 바뀌면 여기서 다시 뽑을 수 있다.
	 * 원천의 응답 구조는 실측으로 확정돼 있다(목록 279건·상세 60건 태그 전수 집계 = Item 20필드와 일치 —
	 * 제공률 분석 문서). 원천이 나중에 필드를 추가하면 {@code Item}에 선언을 더하는 것이 정상 대응이다.
	 */
	public CatalogVendorItem vendorOf(CultureApiResponse.Item item) {
		if (item == null) {
			return null;
		}
		return new CatalogVendorItem(item.seq(), item.title(), item.startDate(), item.endDate(), item.place(),
				item.realmName(), item.area(), item.sigungu(), item.thumbnail(), item.gpsX(), item.gpsY(),
				item.serviceName(), item.price(), item.contents1(), item.url(), item.phone(), item.imgUrl(),
				item.placeUrl(), item.placeAddr(), item.placeSeq());
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
				decode(blankToNull(item.area())),
				vendorOf(item));
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
