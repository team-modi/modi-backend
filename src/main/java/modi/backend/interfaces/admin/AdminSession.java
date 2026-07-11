package modi.backend.interfaces.admin;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import modi.backend.config.JwtProperties;

/**
 * 관리자 콘솔 세션 토큰(무상태). 로그인 성공 시 발급, 인터셉터가 검증한다.
 * 형식: {@code base64url(email|expiryMillis).base64url(HMAC-SHA256)}. 서버 상태 없이 서명·만료만으로 검증.
 * 서명 키는 JWT secret에서 파생("...:admin" 접미)해 사용자 JWT와 분리한다.
 */
@Component
public class AdminSession {

	public static final String SESSION_COOKIE = "admin_session";
	public static final Duration TTL = Duration.ofHours(12);
	private static final String HMAC_ALGO = "HmacSHA256";

	private final SecretKeySpec key;

	public AdminSession(JwtProperties jwtProperties) {
		this.key = new SecretKeySpec((jwtProperties.secret() + ":admin").getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
	}

	public String issue(String email) {
		String payload = email + "|" + (System.currentTimeMillis() + TTL.toMillis());
		return base64(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload);
	}

	/** 유효하면 email, 아니면 empty(서명 불일치·만료·형식오류 전부 empty). */
	public Optional<String> verify(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		int dot = token.lastIndexOf('.');
		if (dot <= 0) {
			return Optional.empty();
		}
		String payload;
		try {
			payload = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
		} catch (RuntimeException e) {
			return Optional.empty();
		}
		if (!constantTimeEquals(sign(payload), token.substring(dot + 1))) {
			return Optional.empty();
		}
		int bar = payload.lastIndexOf('|');
		if (bar <= 0) {
			return Optional.empty();
		}
		try {
			if (System.currentTimeMillis() > Long.parseLong(payload.substring(bar + 1))) {
				return Optional.empty();
			}
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
		return Optional.of(payload.substring(0, bar));
	}

	private String sign(String payload) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGO);
			mac.init(key);
			return base64(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("관리자 세션 서명 실패", e);
		}
	}

	private static boolean constantTimeEquals(String a, String b) {
		return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
				b.getBytes(StandardCharsets.UTF_8));
	}

	private static String base64(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
