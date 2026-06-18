package modi.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영(prod) 프로파일의 CORS 정책 검증.
 * prod에서는 {@code https://modi.vercel.app} 만 허용하고, 로컬/프리뷰/기타 오리진은 차단해야 한다.
 * (실제 비즈니스 컨트롤러가 없어 테스트 전용 컨트롤러 /__cors-test 로 프리플라이트를 확인)
 */
@Import({ TestcontainersConfiguration.class, CorsConfigTest.CorsTestController.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class CorsConfigTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void preflight_allows_vercel_production_origin() throws Exception {
		mockMvc.perform(options("/__cors-test")
				.header("Origin", "https://modi.vercel.app")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "https://modi.vercel.app"));
	}

	@Test
	void preflight_blocks_vercel_preview_origin() throws Exception {
		// prod는 modi.vercel.app 만 허용 — 프리뷰 URL은 차단
		mockMvc.perform(options("/__cors-test")
				.header("Origin", "https://modi-git-feature-team.vercel.app")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden());
	}

	@Test
	void preflight_blocks_localhost_origin() throws Exception {
		// 운영에서는 로컬 오리진 차단(로컬 개발은 default 프로파일에서 허용)
		mockMvc.perform(options("/__cors-test")
				.header("Origin", "http://localhost:3000")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden());
	}

	@Test
	void preflight_blocks_unknown_origin() throws Exception {
		mockMvc.perform(options("/__cors-test")
				.header("Origin", "https://evil.example.com")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden());
	}

	@RestController
	static class CorsTestController {
		@GetMapping("/__cors-test")
		String ping() {
			return "ok";
		}
	}
}
