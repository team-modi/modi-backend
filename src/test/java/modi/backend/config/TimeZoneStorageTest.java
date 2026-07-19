package modi.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.record.AiStatus;
import modi.backend.domain.record.ExhibitionSnapshot;
import modi.backend.domain.record.Record;
import modi.backend.domain.record.WriteMode;

/**
 * JVM 타임존을 KST로 뒀을 때 <b>DATE 컬럼이 하루 밀리는지</b>를 실제 저장값으로 확인한다.
 *
 * <p>{@code BackendApplication}은 {@code TimeZone.setDefault(UTC)}로 JVM을 UTC에 못박고 있고,
 * 주석에 그 이유가 "KST로 두면 Hibernate가 LocalDate를 java.sql.Date로 언랩 → 드라이버가 UTC로 포맷하며
 * DATE 컬럼이 전날로 밀린다"고 적혀 있다. 이 테스트는 그 전제가 <b>현재 스택(Hibernate 7.4 / connector-j 9.x)에서도
 * 여전히 참인지</b>를 검증한다 — 참이면 KST 전환은 포기해야 하고, 거짓이면 UTC 못박기를 걷어낼 수 있다.
 *
 * <p>ORM으로 되읽으면 변환이 상쇄돼 어긋남을 못 잡으므로 <b>JDBC로 원시 값을 직접 읽는다</b>.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TimeZoneStorageTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("JVM 기본 타임존이 Asia/Seoul이다(실험 전제)")
	void jvm_default_timezone_is_kst() {
		assertThat(ZoneId.systemDefault()).isEqualTo(KST);
	}

	@Test
	@DisplayName("DATE 컬럼(관람일)이 하루 밀리지 않고 그대로 저장된다")
	@Transactional
	void date_column_is_not_shifted_by_a_day() {
		LocalDate viewedAt = LocalDate.of(2026, 7, 19);
		Record record = Record.create(1L, 1L,
				new ExhibitionSnapshot("전시", "CUSTOM", null, "장소", "서울", "전시",
						LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
				WriteMode.DIRECT, viewedAt, "본문", null, null, null, AiStatus.READY);
		entityManager.persist(record);
		entityManager.flush();

		String storedDate = jdbcTemplate.queryForObject(
				"select date_format(viewed_at, '%Y-%m-%d') from records where id = ?",
				String.class, record.getId());

		assertThat(storedDate)
				.as("KST 자정이 UTC로 포맷되면 2026-07-18로 하루 밀린다 — 이게 UTC 못박기의 이유였다")
				.isEqualTo("2026-07-19");
	}

	@Test
	@DisplayName("datetime 컬럼(created_at)이 KST 벽시계로 저장된다")
	@Transactional
	void datetime_column_is_stored_as_kst() {
		LocalDateTime beforeKst = LocalDateTime.now(KST);
		Record record = Record.create(1L, 1L,
				new ExhibitionSnapshot("전시", "CUSTOM", null, "장소", "서울", "전시", null, null),
				WriteMode.DIRECT, LocalDate.of(2026, 7, 19), "본문", null, null, null, AiStatus.READY);
		entityManager.persist(record);
		entityManager.flush();

		String raw = jdbcTemplate.queryForObject(
				"select date_format(created_at, '%Y-%m-%dT%H:%i:%s') from records where id = ?",
				String.class, record.getId());

		assertThat(LocalDateTime.parse(raw))
				.as("UTC로 저장되면 KST보다 9시간 이르게 찍힌다(raw=%s)", raw)
				.isBetween(beforeKst.minusMinutes(2), LocalDateTime.now(KST).plusMinutes(2));
	}
}
