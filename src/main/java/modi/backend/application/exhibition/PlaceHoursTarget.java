package modi.backend.application.exhibition;

import java.util.List;

/**
 * 영업시간 보강의 한 조회 단위(= 한 장소). 같은 {@code placeAddr}를 가진 전시들을 묶어 <b>장소당 1콜</b>로 조회하기 위한 값.
 *
 * @param placeName     대표 장소명(질의 정확도용, 그룹 내 전시 중 하나).
 * @param placeAddr     조회 주소(그룹 키).
 * @param exhibitionIds 이 장소의 전시 id들(조회 결과를 여기 전시들에 반영).
 */
public record PlaceHoursTarget(String placeName, String placeAddr, List<Long> exhibitionIds) {
}
