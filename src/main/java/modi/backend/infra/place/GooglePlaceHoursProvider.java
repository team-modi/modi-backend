package modi.backend.infra.place;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import modi.backend.config.PlaceHoursProperties;
import modi.backend.domain.exhibition.PlaceHoursData;
import modi.backend.domain.exhibition.PlaceHoursProvider;
import modi.backend.domain.exhibition.WeeklyOpeningHours;

/**
 * 구글 Places(New) 실호출 영업시간 조회기. 장소명+주소로 Text Search 1콜을 보내 {@code regularOpeningHours}를 받고,
 * periods를 요일별 {@link WeeklyOpeningHours}로 파싱한다.
 * <p>
 * 계약({@link PlaceHoursProvider}): 미발견은 {@link Optional#empty()}, 전송 오류(WebClient 예외)는 전파해 상위가 스킵/재시도한다.
 * 장소는 찾았으나 영업시간이 없으면 {@link WeeklyOpeningHours#empty()}를 담아 반환한다(장소 확인은 됐으므로 재조회 대상에서 빠지게).
 * 운영에서만 선택되며(mock 기본), 키 미설정 시엔 애초에 {@link MockPlaceHoursProvider}가 @Primary로 선택된다.
 */
@Component
public class GooglePlaceHoursProvider implements PlaceHoursProvider {

	private static final Logger log = LoggerFactory.getLogger(GooglePlaceHoursProvider.class);

	/** New API 필수 헤더 — 받을 필드만. 영업시간까지 한 콜로 받는다(2단계 Place Details 불필요). */
	private static final String FIELD_MASK =
			"places.id,places.displayName,places.formattedAddress,places.regularOpeningHours";

	/** 구글 day(0=일요일 … 6=토요일) → java DayOfWeek. */
	private static final DayOfWeek[] GOOGLE_DAY = {
			DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
			DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY };

	// 프로젝트 관례(GeminiGenreClassifier와 동일) — ObjectMapper 빈 주입 대신 자체 인스턴스를 둔다.
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final GoogleMapsApi googleMapsApi;
	private final PlaceHoursProperties properties;

	public GooglePlaceHoursProvider(GoogleMapsApi googleMapsApi, PlaceHoursProperties properties) {
		this.googleMapsApi = googleMapsApi;
		this.properties = properties;
	}

	@Override
	public Optional<PlaceHoursData> fetch(String placeName, String placeAddr) {
		GoogleMapsDto.SearchTextRequest request = new GoogleMapsDto.SearchTextRequest(
				buildQuery(placeName, placeAddr), properties.languageCode(), properties.regionCode());
		// 전송 오류는 여기서 잡지 않고 전파한다 — enricher가 해당 장소만 스킵하고 다음 주기에 재시도한다.
		GoogleMapsDto.SearchTextResponse response =
				googleMapsApi.searchText(properties.apiKey(), FIELD_MASK, request);
		if (response == null) {
			return Optional.empty();
		}
		return response.firstPlace().map(place -> toData(place, placeAddr));
	}

	/** 장소명이 있으면 주소와 함께 질의(매칭 정확도↑). 없으면 주소만. */
	private String buildQuery(String placeName, String placeAddr) {
		if (placeName == null || placeName.isBlank()) {
			return placeAddr;
		}
		return (placeName.trim() + " " + placeAddr).trim();
	}

	private PlaceHoursData toData(GoogleMapsDto.Place place, String queriedAddr) {
		WeeklyOpeningHours weekly = toWeekly(place.regularOpeningHours());
		return new PlaceHoursData(place.id(), place.displayText(), place.formattedAddress(), weekly,
				rawJson(place.regularOpeningHours()), "GOOGLE");
	}

	/** periods를 요일별 영업시간으로. day/시각 결측이나 close 부재(24시간 등)는 건너뛴다(엣지 — P1에서 정교화). */
	private WeeklyOpeningHours toWeekly(GoogleMapsDto.RegularOpeningHours hours) {
		if (hours == null || hours.periods() == null || hours.periods().isEmpty()) {
			return WeeklyOpeningHours.empty();
		}
		WeeklyOpeningHours.Builder builder = WeeklyOpeningHours.builder();
		for (GoogleMapsDto.Period period : hours.periods()) {
			GoogleMapsDto.TimePoint open = period.open();
			GoogleMapsDto.TimePoint close = period.close();
			if (!isUsable(open) || !isUsable(close)) {
				continue;
			}
			DayOfWeek day = GOOGLE_DAY[open.day()];
			builder.add(day, time(open), time(close));
		}
		return builder.build();
	}

	private boolean isUsable(GoogleMapsDto.TimePoint point) {
		return point != null && point.day() != null && point.day() >= 0 && point.day() <= 6
				&& point.hour() != null && point.hour() >= 0 && point.hour() <= 23;
	}

	private LocalTime time(GoogleMapsDto.TimePoint point) {
		int minute = point.minute() == null ? 0 : point.minute();
		return LocalTime.of(point.hour(), Math.floorMod(minute, 60));
	}

	private String rawJson(GoogleMapsDto.RegularOpeningHours hours) {
		if (hours == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(hours);
		} catch (Exception e) {
			log.debug("영업시간 원본 직렬화 실패(무시): {}", e.getMessage());
			return null;
		}
	}
}
