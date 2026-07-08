package modi.backend.application.exhibition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import modi.backend.config.GenreProperties;

/**
 * 부팅 시 공공데이터(CATALOG) 전시 장르 초기화 백필. {@code app.exhibition.genre.init-on-boot=true}(기본)일 때 실행한다.
 * {@link ExhibitionCatalogBootSync}(HIGHEST_PRECEDENCE)가 카탈로그를 먼저 적재한 뒤 이 러너(LOWEST_PRECEDENCE)가
 * 미분류 전시를 최대 {@code init-max-per-run}건 분류기(랜덤/AI)로 채운다. 분류기가 폴백을 보장하므로 예외는 나지 않지만,
 * 예기치 못한 오류가 부팅을 막지 않도록 전체를 방어적으로 감싼다(장르는 부가 기능).
 */
@Component
@ConditionalOnProperty(name = "app.exhibition.genre.init-on-boot", havingValue = "true", matchIfMissing = true)
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class ExhibitionGenreInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ExhibitionGenreInitializer.class);

	private final ExhibitionFacade exhibitionFacade;
	private final GenreProperties genreProperties;

	@Override
	public void run(ApplicationArguments args) {
		try {
			int classified = exhibitionFacade.initGenres(genreProperties.initMaxPerRun());
			if (classified > 0) {
				log.info("부팅 시 전시 장르 초기화 {}건(classifier={})", classified, genreProperties.classifier());
			}
		} catch (RuntimeException e) {
			log.warn("부팅 시 전시 장르 초기화 스킵(오류) — {}", e.getMessage());
		}
	}
}
