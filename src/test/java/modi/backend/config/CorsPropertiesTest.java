package modi.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

	@Test
	void uses_localhost_default_when_allowed_origin_patterns_are_null() {
		CorsProperties properties = new CorsProperties(null);

		assertThat(properties.allowedOriginPatterns()).containsExactly("http://localhost:3000");
	}

	@Test
	void uses_localhost_default_when_allowed_origin_patterns_are_empty() {
		CorsProperties properties = new CorsProperties(List.of());

		assertThat(properties.allowedOriginPatterns()).containsExactly("http://localhost:3000");
	}

	@Test
	void keeps_configured_allowed_origin_patterns() {
		CorsProperties properties = new CorsProperties(List.of(
				"http://localhost:5173",
				"https://mmodi.vercel.app"));

		assertThat(properties.allowedOriginPatterns()).containsExactly(
				"http://localhost:5173",
				"https://mmodi.vercel.app");
	}
}
