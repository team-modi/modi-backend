package modi.backend.domain.exhibition;

/**
 * 구글 영업시간 원본 스테이징({@link PlaceHoursSnapshot}) 영속화 포트(도메인 소유, 구현 infra·DIP).
 * per-run 스테이징이라 조회 포트는 두지 않는다 — 저장과 전체 비움(초기화)만 제공한다.
 */
public interface PlaceHoursSnapshotRepository {

	PlaceHoursSnapshot save(PlaceHoursSnapshot snapshot);

	/** 영업시간 보강 실행 시작 시 스테이징 전체를 비운다("호출 시 초기화"). */
	void deleteAllSnapshots();
}
