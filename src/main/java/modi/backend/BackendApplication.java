package modi.backend;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;
import modi.backend.support.time.AppTime;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@PostConstruct
	public void started() {
		// JVM 기본 타임존을 KST로 고정한다 — DB에 한국 시간이 그대로 찍히게 하기 위함.
		// 컨테이너 TZ env에 의존하지 않고 코드로 보장한다(env 누락 시 조용히 UTC로 되돌아가는 것 방지).
		//
		// 이전에는 UTC로 고정돼 있었다. 사유는 "KST로 두면 Hibernate가 LocalDate를 java.sql.Date로 언랩 →
		// 드라이버가 UTC로 포맷하며 DATE 컬럼(관람일·전시 기간)이 하루 밀린다"였는데, 현재 스택
		// (Hibernate 7.4 / connector-j 9.x)에서는 LocalDate가 java.time 타입 그대로 바인딩돼 재현되지 않는다.
		// TimeZoneStorageTest가 실제 저장값으로 두 가지(DATE 안 밀림 / datetime KST 저장)를 고정한다.
		//
		// ⚠️ datetime은 이것만으로 부족하다 — Hibernate는 ZonedDateTime을 JVM 타임존과 무관하게 UTC로
		//    정규화하므로 application.yaml의 hibernate.timezone.default_storage=NORMALIZE 가 함께 있어야 한다.
		TimeZone.setDefault(TimeZone.getTimeZone(AppTime.KST));
	}

}
