package modi.backend.domain.exhibition.catalog;

import modi.backend.domain.exhibition.hours.PlaceKey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import modi.backend.support.entity.BaseEntity;

/**
 * 작가/주최(도메인) — {@code artists} 매핑. 자연키 = 정규화한 이름(UK, {@link #normalize} — {@link PlaceKey}와 같은 규율:
 * trim·연속공백 1개화). 같은 작가를 여러 전시가 공유할 수 있으므로 전시와는 {@link ExhibitionArtist} 조인으로 N:M이다.
 *
 * <p><b>왜 별도 테이블인가</b>: 작가는 향후 설명·부가 정보가 붙을 독립 개념이라 전시 detail에 문자열로 묻어두지 않는다.
 * 지금은 이름만 둔다(YAGNI — 부가 필드는 필요해질 때 추가). 공공데이터(CATALOG) Item에는 작가가 없어, 이 테이블 데이터는
 * 사용자 등록(CUSTOM)에서만 채워진다.
 */
@Entity
@Table(name = "artists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Artist extends BaseEntity {

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	private Artist(String name) {
		this.name = name;
	}

	/** 정규화한 이름으로 작가 행을 만든다. 자연키(UK)라 같은 정규화 이름은 하나로 수렴한다. */
	public static Artist create(String rawName) {
		return new Artist(normalize(rawName));
	}

	/**
	 * 이름 정규화(자연키 규칙) — 앞뒤 공백 제거 + 연속 공백 1개화. {@link PlaceKey#of}와 같은 규율이며,
	 * MySQL 백필(V31)의 {@code REGEXP_REPLACE(TRIM(artist), '\\s+', ' ')}와 일치해야 한다. 비면 null.
	 */
	public static String normalize(String rawName) {
		if (rawName == null) {
			return null;
		}
		String normalized = rawName.trim().replaceAll("\\s+", " ");
		return normalized.isEmpty() ? null : normalized;
	}
}
