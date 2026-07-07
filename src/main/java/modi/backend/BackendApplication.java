package modi.backend;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@PostConstruct
	public void started() {
		// JVM 기본 타임존을 UTC로 고정해 DB 서버(UTC)와 정렬한다.
		// KST로 두면 Hibernate가 LocalDate를 java.sql.Date(JVM 자정 KST)로 언랩 → 드라이버가 UTC로 포맷하며
		// DATE 컬럼(관람일·전시 기간)이 전날로 하루 밀린다. datetime 컬럼은 이미 UTC로 저장되므로 값 변화 없음.
		// "한국 기준 오늘/지금"이 필요한 곳은 modi.backend.support.time.AppTime.KST를 명시적으로 사용한다.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

}
