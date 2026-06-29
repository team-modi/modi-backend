package modi.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * refresh 쿠키 속성 바인딩. {@code app.auth.cookie.*}.
 * 로컬(http) = secure false / SameSite Lax. 운영(https, Vercel 프록시로 same-origin) = secure true / Lax.
 */
@ConfigurationProperties(prefix = "app.auth.cookie")
public record CookieProperties(boolean secure, String sameSite) {

	public CookieProperties {
		if (sameSite == null || sameSite.isBlank()) {
			sameSite = "Lax";
		}
	}
}
