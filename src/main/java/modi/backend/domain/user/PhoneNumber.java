package modi.backend.domain.user;

import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * 휴대폰 번호 VO — 베타 게스트 식별용. 하이픈·공백 섞인 입력을 숫자만으로 정규화하고
 * {@code RULE: 휴대폰 번호} — 01로 시작하는 10~11자리만 허용한다(형식 오입력이 서로 다른 계정을 만드는 것 방지).
 * 영속화하지 않는 계산·검증 전용 VO(정규화 값은 SocialAccount.providerUserId에 원시값으로 저장).
 */
public record PhoneNumber(String value) {

	public static PhoneNumber of(String raw) {
		String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
		if (!digits.matches("01[0-9]{8,9}")) {
			throw new CoreException(ErrorType.INVALID_INPUT, "올바른 휴대폰 번호가 아닙니다: " + raw);
		}
		return new PhoneNumber(digits);
	}
}
