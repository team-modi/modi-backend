package modi.backend.application.exhibition;

import modi.backend.application.exhibition.contract.GenreTarget;
import modi.backend.application.exhibition.contract.ExhibitionBackfill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.exhibition.catalog.Exhibition;
import modi.backend.ingestion.domain.port.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.catalog.ExhibitionCategory;
import modi.backend.domain.exhibition.catalog.ExhibitionGenre;
import modi.backend.infra.exhibition.catalog.ExhibitionGenreJpaRepository;
import modi.backend.domain.exhibition.catalog.ExhibitionRegion;
import modi.backend.domain.exhibition.catalog.ExhibitionRepository;
import modi.backend.domain.exhibition.genre.GenreClassification;
import modi.backend.domain.exhibition.genre.GenreKeyword;
import modi.backend.domain.exhibition.genre.GenreProvider;
import modi.backend.domain.exhibition.genre.GenreResult;

/**
 * 장르 <b>쓰기</b> 검증(@SpringBootTest + Testcontainers-MySQL).
 * <p>
 * 이관 2단계엔 "쓰기 이중화"(레거시 컬럼 + 정준층)였으나 <b>7단계에서 레거시 컬럼(exhibitions.genre_keyword)을 제거</b>해
 * 이제 정준층({@code exhibition_genre})이 <b>유일한 저장소</b>다. 그래서 두 위치를 함께 단언하던 것이 정준층 단독 단언으로 바뀌었다
 * — 검증하던 동작(항상 마스터 값 반환·provider 계보·재분류·사라진 전시 건너뜀)은 그대로다.
 * {@link ExhibitionCatalogClient}는 목으로 두어 부팅 동기화가 외부 공공데이터를 건드리지 않게 한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.exhibition.enrich.scheduling-enabled=false")
class ExhibitionGenreWriteTest {

	private static final AtomicInteger SEQ = new AtomicInteger(1);

	@Autowired
	ExhibitionFacade exhibitionFacade;

	@Autowired
	ExhibitionBackfill exhibitionBackfill;

	@Autowired
	ExhibitionRepository exhibitionRepository;

	@Autowired
	modi.backend.domain.exhibition.catalog.ExhibitionPlaceRepository exhibitionPlaceRepository;

	@Autowired
	ExhibitionGenreJpaRepository exhibitionGenreRepository;

	@MockitoBean
	ExhibitionCatalogClient exhibitionCatalogClient;

	@Test
	@DisplayName("CUSTOM 등록(장르 직접 지정) — 정준층에 값+출처(USER)를 남긴다")
	void registerCustom_지정장르_provider_USER() {
		ExhibitionResult.Created created = exhibitionFacade.registerCustom(customCreate("사진"));

		ExhibitionGenre canonical = canonical(created.exhibitionId());
		assertThat(canonical.getGenreKeyword()).isEqualTo("사진");
		assertThat(canonical.getProvider()).isEqualTo(GenreProvider.USER.name());
		assertThat(canonical.getModel()).isNull(); // 사람이 고른 값엔 모델이 없다
		assertThat(canonical.getClassifiedAt()).isNotNull();
		assertThat(canonical.isFallback()).isFalse();
	}

	@Test
	@DisplayName("CUSTOM 등록(장르 미지정) — 기본 분류기(mock)의 결정적 산출은 provider=MOCK으로 드러난다(ADR-11)")
	void registerCustom_미지정_provider_MOCK() {
		// 기본 프로파일의 주 분류기는 mock이다(외부 호출 0·결정적). 계보로 실 AI 분류와 구분된다.
		ExhibitionResult.Created created = exhibitionFacade.registerCustom(customCreate(null));

		ExhibitionGenre canonical = canonical(created.exhibitionId());
		assertThat(GenreKeyword.all()).contains(canonical.getGenreKeyword());
		assertThat(canonical.getProvider()).isEqualTo(GenreProvider.MOCK.name());
		assertThat(canonical.isFallback()).isFalse(); // 폴백값이 아니라 의도된 mock 산출 — 재분류 표식은 레거시 RANDOM만
	}

	@Test
	@DisplayName("CATALOG 배치 백필 — 정준층에 AI 계보(provider·model)와 함께 쓴다")
	void applyGenres_배치_계보기록() {
		Exhibition seeded = seedCatalog();
		List<GenreTarget> targets = List.of(new GenreTarget(seeded.getId(), GenreClassification.from(seeded)));

		int applied = exhibitionBackfill.applyGenres(targets,
				List.of(GenreResult.ai("미디어아트", GenreProvider.GEMINI, "gemini-2.5-flash-002")),
				LocalDateTime.now());

		assertThat(applied).isEqualTo(1);
		ExhibitionGenre canonical = canonical(seeded.getId());
		assertThat(canonical.getGenreKeyword()).isEqualTo("미디어아트");
		assertThat(canonical.getProvider()).isEqualTo(GenreProvider.GEMINI.name());
		// 요청 모델(설정값)이 아니라 응답 modelVersion — 모델 업그레이드 시 구모델 산출분만 골라내는 근거다.
		assertThat(canonical.getModel()).isEqualTo("gemini-2.5-flash-002");
	}

	@Test
	@DisplayName("재분류 — 같은 전시를 다시 반영하면 행을 늘리지 않고 갱신한다(UK 1행 유지, RANDOM → GEMINI 복구)")
	void applyGenres_재분류_행추가없이_갱신() {
		Exhibition seeded = seedCatalog();
		List<GenreTarget> targets = List.of(new GenreTarget(seeded.getId(), GenreClassification.from(seeded)));
		exhibitionBackfill.applyGenres(targets, List.of(GenreResult.mock("회화·드로잉")), LocalDateTime.now());

		exhibitionBackfill.applyGenres(targets,
				List.of(GenreResult.ai("사진", GenreProvider.GEMINI, "gemini-2.5-flash")), LocalDateTime.now());

		assertThat(exhibitionGenreRepository.findAllByExhibitionIdIn(List.of(seeded.getId()))).hasSize(1);
		ExhibitionGenre canonical = canonical(seeded.getId());
		assertThat(canonical.getGenreKeyword()).isEqualTo("사진");
		assertThat(canonical.getProvider()).isEqualTo(GenreProvider.GEMINI.name());
		assertThat(canonical.isFallback()).isFalse();
	}

	@Test
	@DisplayName("조회~반영 사이 사라진 전시는 건너뛴다(정준층에도 남기지 않는다)")
	void applyGenres_사라진전시_건너뜀() {
		Exhibition seeded = seedCatalog();
		List<GenreTarget> targets = List.of(new GenreTarget(seeded.getId(), GenreClassification.from(seeded)));
		seeded.delete();
		exhibitionRepository.save(seeded);

		int applied = exhibitionBackfill.applyGenres(targets, List.of(GenreResult.mock("사진")), LocalDateTime.now());

		assertThat(applied).isZero();
		assertThat(exhibitionGenreRepository.findByExhibitionId(seeded.getId())).isEmpty();
	}

	private ExhibitionCriteria.CustomCreate customCreate(String genreKeyword) {
		return new ExhibitionCriteria.CustomCreate(9_900_000L + SEQ.getAndIncrement(), "장르 쓰기 전시", null,
				"한가람미술관", null, null, "SEOUL", "PAINTING", null, null, null, genreKeyword);
	}

	private Exhibition seedCatalog() {
		Long placeId = modi.backend.domain.exhibition.catalog.ExhibitionTestFactory.placeId(
				exhibitionPlaceRepository, "시립미술관", ExhibitionRegion.SEOUL);
		Exhibition e = Exhibition.createCatalog("GENRE-WRITE-" + SEQ.getAndIncrement(), "장르 미분류 전시", placeId,
				null, null, ExhibitionCategory.PAINTING, null, null, "기관");
		return exhibitionRepository.save(e);
	}

	private ExhibitionGenre canonical(Long exhibitionId) {
		return exhibitionGenreRepository.findByExhibitionId(exhibitionId).orElseThrow();
	}
}
