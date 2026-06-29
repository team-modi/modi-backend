package modi.backend;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@PostConstruct
	public void started() {
		// JVM 기본 타임존을 KST로 고정 (BaseEntity의 ZonedDateTime.now() 등 시각 일관성)
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

}
