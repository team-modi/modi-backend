package modi.backend.domain.auth;

import modi.backend.support.error.CoreException;

/**
 * 지원 소셜 provider(화이트리스트). "kakao"/"google" 문자열이 코드 곳곳에 흩어지지 않게 한곳으로 모은다.
 * 외부 경계(요청 path·DB 저장값)와는 {@link #code()} 문자열로 주고받는다.
 */
public enum Provider {

	KAKAO("kakao"),
	GOOGLE("google");

	private final String code;

	Provider(String code) {
		this.code = code;
	}

	/** 경계(요청/저장)에서 쓰는 소문자 코드. */
	public String code() {
		return code;
	}

	/** 화이트리스트 검증 겸 변환. 미지원이면 {@link AuthErrorCode#UNSUPPORTED_PROVIDER}. */
	public static Provider from(String code) {
		for (Provider provider : values()) {
			if (provider.code.equals(code)) {
				return provider;
			}
		}
		throw new CoreException(AuthErrorCode.UNSUPPORTED_PROVIDER, "지원하지 않는 provider: " + code);
	}
}
