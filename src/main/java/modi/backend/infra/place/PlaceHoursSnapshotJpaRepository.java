package modi.backend.infra.place;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.PlaceHoursSnapshot;

/** 구글 영업시간 원본 스테이징 Spring Data JPA. */
public interface PlaceHoursSnapshotJpaRepository extends JpaRepository<PlaceHoursSnapshot, Long> {
}
