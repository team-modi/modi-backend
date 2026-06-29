package modi.backend.domain.auth;

/**
 * provider별 OAuth 클라이언트 포트(전략 패턴). kakao/google 구현체를 infra에 둔다.
 * Facade는 {@link #provider()} 키로 구현체를 선택한다.
 */
public interface OAuthClient {

	/** 이 클라이언트가 담당하는 provider (KAKAO | GOOGLE). */
	Provider provider();

	/** provider 로그인 페이지로 보낼 authorize URL 생성. */
	String buildAuthorizeUrl(String state, String redirectUri);

	/** code → (provider 토큰 교환 → userinfo 조회) → 공통 사용자 정보. */
	OAuthUserInfo fetchUserInfo(String code, String redirectUri);
}
