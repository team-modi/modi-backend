package modi.backend.infra.auth;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import modi.backend.config.OAuthProperties;
import modi.backend.domain.auth.AuthErrorCode;
import modi.backend.domain.auth.OAuthUserInfo;
import modi.backend.domain.auth.Provider;
import modi.backend.domain.user.AgeGroup;
import modi.backend.support.error.CoreException;

/**
 * 네이버 OAuth 클라이언트. HTTP 호출은 {@link NaverApi}(HTTP Interface) 위임.
 * 카카오/구글과 두 가지가 다르다:
 * (1) 토큰 교환에 redirect_uri 대신 state를 보낸다.
 * (2) userinfo가 {@code response} 아래 중첩 구조이고, 실패해도 HTTP 200 + resultcode로 내려온다("00"이 성공).
 */
@Component
public class NaverOAuthClient extends AbstractOAuthClient {

	private final OAuthProperties.Provider props;
	private final NaverApi naverApi;

	public NaverOAuthClient(OAuthProperties properties, NaverApi naverApi) {
		this.props = properties.naver();
		this.naverApi = naverApi;
	}

	@Override
	public Provider provider() {
		return Provider.NAVER;
	}

	@Override
	@SuppressWarnings("unchecked")
	public OAuthUserInfo fetchUserInfo(String code, String redirectUri, String state) {
		// 네이버 토큰 교환은 redirect_uri를 받지 않고 state를 검증 파라미터로 쓴다 → 폼을 직접 구성.
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", props.clientId());
		form.add("client_secret", props.clientSecret());
		form.add("code", code);
		form.add("state", state);

		Map<String, Object> token = naverApi.getToken(form);
		Map<String, Object> body = naverApi.getUserInfo("Bearer " + extractAccessToken(token));

		// 네이버는 프로필 조회 실패도 HTTP 200으로 내려온다 — resultcode "00"만 성공.
		if (!"00".equals(body.get("resultcode"))) {
			throw new CoreException(AuthErrorCode.OAUTH_COMMUNICATION_FAILED,
					"네이버 프로필 조회 실패: " + body.get("message"));
		}

		Map<String, Object> response = (Map<String, Object>) body.getOrDefault("response", Map.of());
		String sub = String.valueOf(response.get("id"));
		String email = (String) response.get("email"); // 비동의 시 null
		String name = (String) response.get("name"); // 이름 동의항목, 미동의 시 null
		String nickname = (String) response.get("nickname");
		if (nickname == null) {
			nickname = name; // 닉네임 미동의 시 이름으로 폴백
		}
		// 동의항목: 연령대(age "20-29") + 출생연도(birthyear "1998"). 미동의 시 각각 null.
		AgeGroup ageGroup = AgeGroup.fromNaverAge((String) response.get("age"));
		Integer birthYear = parseBirthYear((String) response.get("birthyear"));
		return new OAuthUserInfo(sub, email, name, nickname, ageGroup, birthYear);
	}

	/** 네이버 birthyear("1998") → Integer. 미동의/파싱 불가 시 null. */
	private static Integer parseBirthYear(String birthyear) {
		if (birthyear == null || birthyear.isBlank()) {
			return null;
		}
		try {
			return Integer.valueOf(birthyear.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
