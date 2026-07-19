package modi.backend.ingestion.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구글 Places(New) 응답 원본(벤더층) — {@code google_place_response} 매핑. V19의 {@code google_place_hours}를 대체한다.
 *
 * <p><b>왜 원본을 남기나</b>: 변환이 손실적이기 때문이다. {@code periods} JSON(요일·구간 구조 전체)이
 * {@code "매일 10:00~18:00"} 문자열 한 줄로 눌린다 — 잃는 정보가 명확하다. 그래서 변환 규칙이 바뀌면
 * 구글을 다시 부르지 않고(=과금 없이) 여기서 정준({@link PlaceHours})을 재생성한다.
 * (장르 축에 벤더 테이블이 없는 것과 대비된다 — 거긴 변환이 무손실이라 원본이 정준의 복사본이 될 뿐이다.
 * 이 비대칭은 "손실적 변환만 원본을 보존한다"는 한 규칙을 세 축에 똑같이 적용한 결과다.)
 *
 * <p><b>V19에서 무엇이 달라졌나 — UK가 생겼다.</b> 기존 {@code google_place_hours}는 UK가 없어 재수집이 행을
 * 누적시켰고, 그래서 "매 실행 전체 삭제 후 재적재"라는 대응이 필요했다. 그 reset이 대상 조회·조기 종료보다 <b>먼저</b>
 * 도는 바람에 할 일이 0건이어도 매일 스테이징이 전멸했다. UK({@code place_key})로 upsert가 멱등해지면서
 * <b>reset이라는 개념 자체가 사라졌고</b>, 그 버그도 원인부터 없어졌다 — 이제 이 테이블은 per-run 스테이징이 아니라
 * <b>영속 원본</b>이다.
 *
 * <p>{@code raw_json}은 구글 Place 응답 <b>전체</b>다(id·displayName·formattedAddress·regularOpeningHours).
 * V19가 별도 컬럼으로 갖고 있던 값들이 여기로 들어온다 — 벤더 원본은 벤더 어휘 그대로 남긴다는 층 규칙에 맞고,
 * 구글이 필드를 늘려도 스키마 변경이 없다. 카카오 도입 시 {@code kakao_place_response}가 추가될 뿐,
 * 정준·도메인·읽기 경로는 불변이다.
 */
@Entity
@Table(name = "google_place_response")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GooglePlaceResponse {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "exhibition_place_id", nullable = false)
	private Long exhibitionPlaceId;

	/** 구글 Place 응답 원본 JSON. */
	@Column(name = "raw_json", columnDefinition = "text")
	private String rawJson;

	@Column(name = "fetched_at")
	private LocalDateTime fetchedAt;

	private GooglePlaceResponse(Long exhibitionPlaceId, String rawJson, LocalDateTime fetchedAt) {
		this.exhibitionPlaceId = exhibitionPlaceId;
		this.rawJson = rawJson;
		this.fetchedAt = fetchedAt;
	}

	public static GooglePlaceResponse first(Long exhibitionPlaceId, String rawJson, LocalDateTime fetchedAt) {
		return new GooglePlaceResponse(exhibitionPlaceId, rawJson, fetchedAt);
	}

	/** 재조회 결과로 원본을 갱신한다(멱등 upsert — 같은 장소는 항상 1행). */
	public void refresh(String rawJson, LocalDateTime fetchedAt) {
		this.rawJson = rawJson;
		this.fetchedAt = fetchedAt;
	}
}
