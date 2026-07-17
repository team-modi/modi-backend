package modi.backend.infra.exhibition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import modi.backend.domain.exhibition.ExhibitionPlace;

/** Spring Data JPA — 전시장. */
public interface ExhibitionPlaceJpaRepository extends JpaRepository<ExhibitionPlace, Long> {

	Optional<ExhibitionPlace> findByPlaceKeyAndDeletedAtIsNull(String placeKey);

	/**
	 * 영업시간 보강 대상 — 주소가 있고, 영업시간 정준행이 없거나(부재) staleBefore 이전 동기화된 전시장.
	 * 다른 엔티티(place_hours) 부재/조건이라 JPQL {@code not exists}로 명시한다. id asc로 결정적 정렬.
	 */
	@Query("""
			select p from ExhibitionPlace p
			where p.deletedAt is null and p.address is not null
			  and not exists (
			      select 1 from PlaceHours h
			      where h.exhibitionPlaceId = p.id and h.syncedAt is not null and h.syncedAt >= :staleBefore)
			order by p.id asc""")
	List<ExhibitionPlace> findPlacesNeedingHours(@Param("staleBefore") LocalDateTime staleBefore, Pageable pageable);
}
