package modi.backend.domain.exhibition.genre;

import modi.backend.domain.exhibition.sync.port.GenreClassifier;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 전시 장르 키워드 마스터(회화·드로잉/사진/… 10종). 분류 결과가 이 집합 안의 값이 되도록 강제하는 단일 진실 원천이다.
 * {@link GenreClassifier} 구현이 이 마스터를 사용한다 — {@code random()}은 랜덤 분류기의 선택지,
 * {@code all()}은 AI 분류기(Gemini)의 허용 enum, {@code contains()}는 AI 응답 검증용이다.
 */
public final class GenreKeyword {

	/** 한국어 장르 키워드 마스터. */
	private static final List<String> MASTER = List.of(
			"회화·드로잉", "사진", "미디어아트", "조각·설치", "디자인",
			"공예", "건축", "공연", "현대미술", "일러스트레이션");

	private GenreKeyword() {
	}

	/** 마스터에서 무작위로 1개 선택(랜덤 분류기·AI 폴백용). */
	public static String random() {
		return MASTER.get(ThreadLocalRandom.current().nextInt(MASTER.size()));
	}

	/** 마스터 전체(AI 분류기의 허용 enum·검증용). */
	public static List<String> all() {
		return MASTER;
	}

	/** 주어진 값이 마스터에 속하는지(AI 응답이 마스터를 벗어났는지 검증). */
	public static boolean contains(String keyword) {
		return keyword != null && MASTER.contains(keyword);
	}
}
