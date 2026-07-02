package modi.backend.infra.exhibition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import modi.backend.config.PublicDataProperties;
import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.CatalogExhibitionData;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.support.error.CoreException;

/**
 * {@link ExhibitionCatalogClient} 어댑터 — 한눈에보는문화정보(15138937) realm2(목록)·detail2(상세) 호출을 담당한다(DIP).
 * 호출 자체는 선언형 HTTP Interface {@link CultureApi}에 위임하고(KakaoApi와 동일 패턴), 응답이 XML이라 JSON 코덱 없이
 * 문자열로 받는다. XML 파싱·도메인 매핑은 {@link CultureApiMapper}에 위임하고, 이 클래스는 전송(HTTP 호출)과
 * 오케스트레이션(페이지네이션·인증키 미설정 스킵·전송 오류 변환)만 담당한다(SRP).
 * 통신 실패·비정상 응답은 {@link ExhibitionErrorCode#EXTERNAL_API_UNAVAILABLE}로 변환한다(HTTP·라이브러리 예외 누수 차단).
 */
@Component
@RequiredArgsConstructor
public class CultureExhibitionClient implements ExhibitionCatalogClient {

	private static final Logger log = LoggerFactory.getLogger(CultureExhibitionClient.class);

	private final CultureApi cultureApi;
	private final CultureApiMapper mapper;
	private final PublicDataProperties properties;

	@Override
	public List<CatalogExhibitionData> fetchAll() {
		if (!properties.isConfigured()) {
			// 인증키 미설정: 외부 호출을 시도하지 않고 스킵한다(데모는 시드 데이터로 동작 — 04_전시_구현.md).
			log.info("CULTURE_API_KEY 미설정 — 동기화 스킵");
			return List.of();
		}
		List<CatalogExhibitionData> collected = new ArrayList<>();
		for (int pageNo = 1; pageNo <= properties.maxPages(); pageNo++) {
			int page = pageNo;
			String xml = request("/realm2", () -> cultureApi.getRealmList(
					properties.serviceKey(), page, properties.numOfRows(), properties.realmCode()));
			CultureApiResponse response = mapper.parse(xml);
			List<CultureApiResponse.Item> items = response.items();
			items.stream().map(mapper::toCatalog).filter(CatalogExhibitionData::isPersistable).forEach(collected::add);
			if (items.size() < properties.numOfRows()) {
				break; // 마지막 페이지
			}
		}
		return collected;
	}

	@Override
	public Optional<CatalogDetailData> fetchDetail(String externalId) {
		if (!properties.isConfigured()) {
			return Optional.empty();
		}
		String xml = request("/detail2", () -> cultureApi.getDetail(properties.serviceKey(), externalId));
		CultureApiResponse response = mapper.parse(xml);
		return response.items().stream().findFirst().map(mapper::toDetail);
	}

	private String request(String path, Supplier<String> call) {
		try {
			return call.get();
		} catch (RuntimeException e) {
			log.warn("외부 전시 API 호출 실패 {}: {}", path, e.getMessage());
			throw new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 전시 API 호출 실패", e);
		}
	}
}
