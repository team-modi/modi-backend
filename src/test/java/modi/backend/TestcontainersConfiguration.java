package modi.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	public MySQLContainer mysqlContainer() {
		// 전체 스위트는 여러 @SpringBootTest 컨텍스트(각자 Hikari 풀)를 캐시해 동시에 살려둔다 —
		// 단일 컨테이너의 기본 max_connections(151)가 병목이 되어 간헐적 커넥션 타임아웃이 나므로 상한을 올린다(용량만 확대).
		return new MySQLContainer(DockerImageName.parse("mysql:latest"))
				.withCommand("--max-connections=1000", "--default-time-zone=+09:00");
	}

}
