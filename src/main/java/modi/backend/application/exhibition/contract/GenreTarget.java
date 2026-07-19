package modi.backend.application.exhibition.contract;

import modi.backend.domain.exhibition.genre.GenreClassification;

/**
 * 장르 백필의 한 분류 단위(= 전시 1건). {@link PlaceHoursTarget}과 동형 — 조회 트랜잭션이 <b>엔티티가 아니라 값</b>을 내보내
 * 트랜잭션 밖(AI 호출) 구간에 detached 엔티티가 떠다니지 않게 한다. 저장 트랜잭션은 {@code exhibitionId}로 다시 읽는다.
 *
 * @param exhibitionId   분류 결과를 반영할 전시 id.
 * @param classification 분류기에 넘길 입력(분류 근거 텍스트만).
 */
public record GenreTarget(Long exhibitionId, GenreClassification classification) {
}
