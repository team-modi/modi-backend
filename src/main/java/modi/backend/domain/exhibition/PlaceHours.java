package modi.backend.domain.exhibition;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장소 영업시간(정준층 — 벤더 무관) — {@code place_hours} 매핑.
 *
 * <p><b>행 = 장소 1곳</b>(UK {@code place_key})이다. 전시가 아니라 장소의 속성이라, 같은 건물에서 열리는 전시 여럿이
 * 한 행을 공유한다 — 그래서 조회도 장소당 1콜이면 된다.
 *
 * <p>여기 들어가는 건 <b>우리가 정의한 표시 모델</b>({@code formatted})뿐이다. 벤더 원본 JSON은
 * {@link GooglePlaceResponse}에 남는다 — 정준층에 벤더 원본을 복사하면 Silver가 Bronze의 복제가 되어 층의 의미가 죽는다.
 * 변환 규칙(요일 묶기·휴무 표기)이 바뀌면 벤더 원본에서 이 값을 재생성한다.
 *
 * <p>{@code status}가 푸는 문제와 {@code provider}가 푸는 문제는 각각
 * {@link PlaceHoursStatus}·{@link PlaceHoursVendor} 참조.
 *
 * <p>{@link Exhibition}과는 {@code place_key} 값으로만 이어진다(FK ❌, @ManyToOne ❌) — 정준층은 도메인과 생명주기가
 * 다르고 원본에서 재생성될 수 있는 층이다. 감사 컬럼이 필요 없어 {@code BaseEntity}를 상속하지 않는다.
 */
@Entity
@Table(name = "place_hours")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceHours {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "place_key", nullable = false, length = 500)
	private String placeKey;

	/** 우리 표시 규칙 문자열({@link OpeningHoursFormatter} 산출). 영업시간 정보가 없으면 null. */
	@Column(name = "formatted", length = 500)
	private String formatted;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PlaceHoursStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 20)
	private PlaceHoursVendor provider;

	/** 우리가 센 조회 시도 횟수. 영구 실패 장소가 매 주기 무한 재시도되는 것을 막을 재료다. */
	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	/**
	 * 다음 재시도 도래 시각. <b>이관 4단계에서는 항상 null이다</b> — 대상 선별은 아직
	 * {@code exhibitions.operating_hours_synced_at}이 맡는다(구조 이관과 동작 변경을 섞지 않는다).
	 * 백오프 정책은 이 테이블이 선별을 맡는 단계에서 정한다 — 읽는 곳이 없는 지금 값을 지어내면,
	 * 나중에 그 값이 근거 있는 정책인 줄 알고 쓰게 된다.
	 */
	@Column(name = "next_attempt_at")
	private LocalDateTime nextAttemptAt;

	private PlaceHours(String placeKey, String formatted, PlaceHoursStatus status, PlaceHoursVendor provider) {
		this.placeKey = placeKey;
		this.formatted = formatted;
		this.status = status;
		this.provider = provider;
		this.attemptCount = 1;
	}

	/** 조회 결과로 장소 행을 처음 만든다. */
	public static PlaceHours first(String placeKey, String formatted, PlaceHoursStatus status,
			PlaceHoursVendor provider) {
		return new PlaceHours(placeKey, formatted, status, provider);
	}

	/**
	 * 재조회 결과를 반영한다. 조회 결과 덮어쓰기가 정상 동작이다(영업시간은 바뀌는 값이고, 30일마다 갱신한다).
	 * <p>
	 * 다만 <b>실패로는 기존 표시값을 지우지 않는다</b>({@link PlaceHoursStatus#FAILED}) — 전송 오류 때문에
	 * 사용자에게 보이던 영업시간이 사라지면, 부가 기능의 일시 장애가 서비스 후퇴가 된다. 상태만 남기고 값은 지킨다.
	 * 반대로 NOT_FOUND·NO_HOURS는 <b>벤더가 "없다"고 답한 것</b>이라 값을 비우는 게 사실에 맞다.
	 */
	public void refresh(String formatted, PlaceHoursStatus status, PlaceHoursVendor provider) {
		this.attemptCount++;
		this.status = status;
		this.provider = provider;
		if (status != PlaceHoursStatus.FAILED) {
			this.formatted = formatted;
		}
	}

	/** mock으로 채워진 행인가 — 실호출 결과와 구분해 선별 재조회할 때 쓴다. */
	public boolean isMock() {
		return this.provider == PlaceHoursVendor.MOCK;
	}
}
