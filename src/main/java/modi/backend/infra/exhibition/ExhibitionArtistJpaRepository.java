package modi.backend.infra.exhibition;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import modi.backend.domain.exhibition.ExhibitionArtist;

/** Spring Data JPA — 전시-작가 조인. 이름 조립은 작가 테이블과의 조인 JPQL로 한다. */
public interface ExhibitionArtistJpaRepository extends JpaRepository<ExhibitionArtist, Long> {

	boolean existsByExhibitionIdAndArtistId(Long exhibitionId, Long artistId);

	/** 한 전시의 작가 이름을 조인 순서(조인 id)대로. 작가가 soft-delete되지 않은 것만. */
	@Query("""
			select a.name from Artist a, ExhibitionArtist ea
			where ea.artistId = a.id and a.deletedAt is null and ea.exhibitionId = :exhibitionId
			order by ea.id""")
	List<String> findArtistNames(@Param("exhibitionId") Long exhibitionId);
}
