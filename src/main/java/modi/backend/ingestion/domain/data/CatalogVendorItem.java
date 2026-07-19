package modi.backend.ingestion.domain.data;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 문화포털 응답 아이템의 <b>원문 verbatim</b> 어휘(ADR-13) — 도메인 변환(디코드·평문 추출·타입 정제) <b>이전</b> 값이다.
 * 벤더 스냅샷({@code culture_list_snapshot}·{@code culture_detail_snapshot})이 이 필드들을 응답 구조 그대로 적재한다.
 * realm2(목록)는 이 중 12필드, detail2(상세)는 18태그를 채운다(실측 전수 집계 — 제공률 분석 문서).
 */
public record CatalogVendorItem(
		String seq,
		String title,
		String startDate,
		String endDate,
		String place,
		String realmName,
		String area,
		String sigungu,
		String thumbnail,
		String gpsX,
		String gpsY,
		String serviceName,
		String price,
		String contents,
		String url,
		String phone,
		String imgUrl,
		String placeUrl,
		String placeAddr,
		String placeSeq) {

	/**
	 * 변경 감지 해시의 원료 — 필드를 고정 순서로 이어붙인 정준 문자열(null은 빈 값, 구분자 US(0x1F)).
	 * 과거 payload JSON 해시의 역할을 필드 기반으로 승계한다(ADR-13).
	 */
	public String canonical() {
		return Stream.of(seq, title, startDate, endDate, place, realmName, area, sigungu, thumbnail, gpsX, gpsY,
						serviceName, price, contents, url, phone, imgUrl, placeUrl, placeAddr, placeSeq)
				.map(v -> v == null ? "" : v)
				.collect(Collectors.joining(""));
	}
}
