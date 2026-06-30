package modi.backend.infra.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import modi.backend.config.JwtProperties;
import modi.backend.domain.auth.AuthTokens;
import modi.backend.domain.auth.TokenClaims;
import modi.backend.domain.auth.TokenProvider;
import modi.backend.domain.user.User;

/**
 * {@link TokenProvider} 구현(infra). HMAC-SHA256 서명 JWT.
 * access = userId+provider+nickname+profileCompleted, refresh = userId+provider.
 */
@Component
public class JwtTokenProvider implements TokenProvider {

	private final SecretKey key;
	private final long accessTtlSeconds;
	private final long refreshTtlSeconds;

	public JwtTokenProvider(JwtProperties props) {
		this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
		this.accessTtlSeconds = props.accessTtlSeconds();
		this.refreshTtlSeconds = props.refreshTtlSeconds();
	}

	@Override
	public AuthTokens issue(User user, String provider) {
		Instant now = Instant.now();
		String access = Jwts.builder()
				.subject(String.valueOf(user.getId()))
				.claim("type", "access")
				.claim("provider", provider)
				.claim("nickname", user.getNickname())
				.claim("profileCompleted", user.isProfileCompleted())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
				.signWith(key)
				.compact();
		String refresh = Jwts.builder()
				.subject(String.valueOf(user.getId()))
				.claim("type", "refresh")
				.claim("provider", provider)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(refreshTtlSeconds)))
				.signWith(key)
				.compact();
		return new AuthTokens(access, refresh);
	}

	@Override
	public Optional<TokenClaims> parse(String token) {
		try {
			Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
			return Optional.of(new TokenClaims(
					Long.valueOf(c.getSubject()),
					c.get("type", String.class),
					c.get("provider", String.class),
					c.get("nickname", String.class),
					c.get("profileCompleted", Boolean.class)));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	@Override
	public long accessTtlSeconds() {
		return accessTtlSeconds;
	}

	@Override
	public long refreshTtlSeconds() {
		return refreshTtlSeconds;
	}
}
