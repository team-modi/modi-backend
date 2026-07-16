package modi.backend.infra.auth;

import java.util.Map;

import org.springframework.stereotype.Component;

import modi.backend.config.OAuthProperties;
import modi.backend.domain.auth.OAuthUserInfo;
import modi.backend.domain.auth.Provider;
import modi.backend.domain.user.AgeGroup;

/**
 * 카카오 OAuth 클라이언트. HTTP 호출은 {@link KakaoApi}(HTTP Interface) 위임.
 * userinfo가 중첩 구조(kakao_account.email, .profile.nickname).
 */
@Component
public class KakaoOAuthClient extends AbstractOAuthClient {

	private final OAuthProperties.Provider props;
	private final KakaoApi kakaoApi;

	public KakaoOAuthClient(OAuthProperties properties, KakaoApi kakaoApi) {
		this.props = properties.kakao();
		this.kakaoApi = kakaoApi;
	}

	@Override
	public Provider provider() {
		return Provider.KAKAO;
	}

	@Override
	@SuppressWarnings("unchecked")
	public OAuthUserInfo fetchUserInfo(String code, String redirectUri, String state) {
		Map<String, Object> token = kakaoApi.getToken(
				tokenForm(props.clientId(), props.clientSecret(), redirectUri, code));
		Map<String, Object> body = kakaoApi.getUserInfo("Bearer " + extractAccessToken(token));

		String sub = String.valueOf(body.get("id"));
		Map<String, Object> account = (Map<String, Object>) body.getOrDefault("kakao_account", Map.of());
		String email = (String) account.get("email"); // 비동의 시 null
		String name = (String) account.get("name"); // 이름(name) 동의항목, 미동의 시 null
		Map<String, Object> profile = (Map<String, Object>) account.getOrDefault("profile", Map.of());
		String nickname = (String) profile.get("nickname");
		if (nickname == null) {
			Map<String, Object> properties = (Map<String, Object>) body.getOrDefault("properties", Map.of());
			nickname = (String) properties.get("nickname");
		}
		// 동의항목: 연령대(age_range "20~29") + 출생연도(birthyear "1993"). 미동의 시 각각 null.
		AgeGroup ageGroup = AgeGroup.fromKakaoAgeRange((String) account.get("age_range"));
		Integer birthYear = parseBirthYear((String) account.get("birthyear"));
		return new OAuthUserInfo(sub, email, name, nickname, ageGroup, birthYear);
	}

	/** 카카오 birthyear("1993") → Integer. 미동의/파싱 불가 시 null. */
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
