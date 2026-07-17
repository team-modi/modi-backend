package modi.backend.infra.exhibition;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.Artist;

/** Spring Data JPA — 작가. 자연키(정규화 이름)로 조회. */
public interface ArtistJpaRepository extends JpaRepository<Artist, Long> {

	Optional<Artist> findByNameAndDeletedAtIsNull(String name);
}
