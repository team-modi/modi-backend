package modi.backend.domain.auth;

/**
 * provider별 OAuth 클라이언트 포트(전략 패턴). kakao/google 구현체를 infra에 둔다.
 * Facade는 {@link #provider()} 키로 구현체를 선택한다.
 */
public interface OAuthClient {

	/** 이 클라이언트가 담당하는 provider (KAKAO | GOOGLE | NAVER). */
	Provider provider();

	/**
	 * code → (provider 토큰 교환 → userinfo 조회) → 공통 사용자 정보.
	 * kakao/google은 토큰 교환에 redirectUri를 쓰고 state를 무시한다.
	 * naver는 반대로 토큰 교환에 state를 쓰고(redirect_uri 미사용) redirectUri를 무시한다.
	 */
	OAuthUserInfo fetchUserInfo(String code, String redirectUri, String state);
}
