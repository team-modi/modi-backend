package modi.backend.domain.auth;

/**
 * provider userinfo에서 추출한 공통 사용자 정보(VO). provider별 응답 차이를 흡수한다.
 *
 * @param sub      provider 내 고유 식별자
 * @param email    이메일(카카오 비동의 시 null 가능)
 * @param nickname 닉네임/이름
 */
public record OAuthUserInfo(String sub, String email, String nickname) {
}
