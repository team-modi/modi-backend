package modi.backend.interfaces;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import modi.backend.TestcontainersConfiguration;

/**
 * springdoc(OpenAPI) 문서가 ApiSpec 어노테이션으로 정상 생성되는지 스모크 검증.
 * (개별 경로 열거는 라우트 변경마다 깨져 제거 — 생성 성공 + Bearer 스킴만 본다)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	@DisplayName("/v3/api-docs 문서가 생성되고 Bearer 스킴을 포함한다")
	void apiDocs_생성() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths").exists())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());
	}
}
