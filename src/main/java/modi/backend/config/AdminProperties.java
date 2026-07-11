package modi.backend.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관리자 콘솔 로그인 자격. 비기술 팀원도 쉽게 쓰도록 <b>이메일 화이트리스트 + 공용 비밀번호</b> 방식.
 * ⚠️ 레포가 PUBLIC이라 이메일·비밀번호는 절대 코드/yaml에 두지 않는다 → env/Secret으로만 주입
 *   (application.yaml은 {@code ${ADMIN_EMAILS:}} / {@code ${ADMIN_PASSWORD:}}로 참조만).
 * 미설정(빈 값)이면 아무도 로그인 못 함(안전 기본값).
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(List<String> emails, String password) {

	public AdminProperties {
		emails = emails == null ? List.of()
				: emails.stream().map(e -> e == null ? "" : e.trim().toLowerCase()).filter(e -> !e.isBlank()).toList();
		password = password == null ? "" : password;
	}

	public boolean isAllowedEmail(String email) {
		return email != null && emails.contains(email.trim().toLowerCase());
	}

	/** 상수시간 비교(타이밍 공격 완화). 비번 미설정이면 항상 거부. */
	public boolean passwordMatches(String candidate) {
		if (password.isEmpty() || candidate == null) {
			return false;
		}
		return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),
				candidate.getBytes(StandardCharsets.UTF_8));
	}

	public boolean configured() {
		return !emails.isEmpty() && !password.isEmpty();
	}
}
