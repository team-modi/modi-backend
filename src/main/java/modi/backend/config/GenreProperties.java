package modi.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 전시 장르 분류 설정. {@code app.exhibition.genre.*} 바인딩.
 * classifier: 주 분류기 선택({@code gemini} | {@code random}) — 랜덤/AI 두 구현이 빈으로 공존하고 이 값으로 하나가 선택된다.
 * init-on-boot: 부팅 시 미분류 CATALOG(공공데이터)에 장르를 채우는 초기화 백필 수행 여부.
 * init-max-per-run: 초기화 1회당 분류 상한(무료 한도·부팅 지연 보호).
 */
@ConfigurationProperties(prefix = "app.exhibition.genre")
public record GenreProperties(String classifier, Boolean initOnBoot, Integer initMaxPerRun) {

	public GenreProperties {
		if (classifier == null || classifier.isBlank()) {
			classifier = "gemini";
		}
		if (initOnBoot == null) {
			initOnBoot = Boolean.TRUE;
		}
		if (initMaxPerRun == null || initMaxPerRun <= 0) {
			initMaxPerRun = 20;
		}
	}

	/** 주 분류기로 Gemini(AI)를 쓰는지. 그 외 값은 랜덤으로 취급. */
	public boolean useGemini() {
		return "gemini".equalsIgnoreCase(classifier);
	}
}
