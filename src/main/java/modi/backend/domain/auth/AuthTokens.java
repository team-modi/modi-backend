package modi.backend.domain.auth;

/**
 * 자체 발급 토큰 쌍(VO). access는 짧게, refresh는 길게.
 */
public record AuthTokens(String accessToken, String refreshToken) {
}
