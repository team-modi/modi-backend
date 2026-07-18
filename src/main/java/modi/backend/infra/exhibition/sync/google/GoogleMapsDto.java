package modi.backend.infra.exhibition.sync.google;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 구글 Places(New) {@code places:searchText} 요청/응답 바인딩(외곽 1클래스 + 중첩 record — 컨벤션).
 * 응답에는 우리가 쓰지 않는 필드가 많아 응답 계열은 {@code ignoreUnknown}으로 관대하게 파싱한다.
 * 영업시간은 FieldMask로 {@code places.regularOpeningHours}만 요청하므로 그 하위만 매핑한다.
 */
public final class GoogleMapsDto {

	private GoogleMapsDto() {
	}

	// ----- 요청 -----

	/** Text Search 요청 본문. FieldMask·API 키는 헤더로 전달(여기엔 없음). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SearchTextRequest(String textQuery, String languageCode, String regionCode) {
	}

	// ----- 응답 -----

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SearchTextResponse(List<Place> places) {

		/** 첫 후보 장소(없으면 empty). */
		public Optional<Place> firstPlace() {
			return places == null || places.isEmpty() ? Optional.empty() : Optional.ofNullable(places.get(0));
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Place(String id, DisplayName displayName, String formattedAddress,
			RegularOpeningHours regularOpeningHours) {

		public String displayText() {
			return displayName == null ? null : displayName.text();
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record DisplayName(String text, String languageCode) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RegularOpeningHours(List<Period> periods, List<String> weekdayDescriptions) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Period(TimePoint open, TimePoint close) {
	}

	/** day: 0=일요일 … 6=토요일(구글 규격). hour/minute는 24시간 기준. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TimePoint(Integer day, Integer hour, Integer minute) {
	}
}
