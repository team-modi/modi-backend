package modi.backend.application.admin;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import modi.backend.config.AdminProperties;
import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * 관리자 콘솔 로그인 자격 검증. 이메일 화이트리스트 + 공용 비밀번호.
 * 실패는 이메일/비번 어느 쪽이 틀렸는지 구분하지 않는다(정보 노출·계정 열거 방지). 세션 토큰 발급은 인터페이스 계층이 담당.
 */
@Service
@RequiredArgsConstructor
public class AdminAuthFacade {

	private final AdminProperties adminProperties;

	/** 성공 시 정규화된 이메일 반환, 실패 시 401. */
	public String authenticate(String email, String password) {
		String normalized = email == null ? "" : email.trim().toLowerCase();
		// & (non-short-circuit): 이메일/비번 검사를 항상 둘 다 수행해 타이밍으로 실패 원인을 흘리지 않는다.
		if (!(adminProperties.isAllowedEmail(normalized) & adminProperties.passwordMatches(password))) {
			throw new CoreException(ErrorType.UNAUTHORIZED);
		}
		return normalized;
	}
}
