package modi.backend.domain.exhibition;

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
 * 전시-작가 조인(N:M) — {@code exhibition_artists} 매핑. 한 전시에 작가 여럿, 한 작가가 전시 여럿.
 *
 * <p>{@link Exhibition}·{@link Artist} 모두 <b>ID 논리 참조</b>다(경계 넘는 @ManyToOne·FK 금지 — {@link ExhibitionGenre}
 * 선례). UK(exhibition_id, artist_id)로 같은 조합이 중복 생기지 않는다(멱등 조인). 감사 컬럼이 필요 없어 {@code BaseEntity}를
 * 상속하지 않는다.
 */
@Entity
@Table(name = "exhibition_artists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExhibitionArtist {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "exhibition_id", nullable = false)
	private Long exhibitionId;

	@Column(name = "artist_id", nullable = false)
	private Long artistId;

	private ExhibitionArtist(Long exhibitionId, Long artistId) {
		this.exhibitionId = exhibitionId;
		this.artistId = artistId;
	}

	public static ExhibitionArtist of(Long exhibitionId, Long artistId) {
		return new ExhibitionArtist(exhibitionId, artistId);
	}
}
