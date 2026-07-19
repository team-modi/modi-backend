package modi.backend.application.auth;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.auth.AuthErrorCode;
import modi.backend.domain.auth.AuthTokens;
import modi.backend.domain.auth.OAuthClient;
import modi.backend.domain.auth.OAuthUserInfo;
import modi.backend.domain.auth.Provider;
import modi.backend.domain.auth.RefreshTokenStore;
import modi.backend.domain.auth.TokenClaims;
import modi.backend.domain.auth.TokenProvider;
import modi.backend.domain.user.PhoneNumber;
import modi.backend.domain.user.SocialAccount;
import modi.backend.domain.user.SocialAccountRepository;
import modi.backend.domain.user.User;
import modi.backend.domain.user.UserErrorCode;
import modi.backend.domain.user.UserRepository;
import modi.backend.support.error.CoreException;

/**
 * 소셜 로그인 유스케이스 조율.
 * (provider, providerUserId)로 SocialAccount 조회 → 있으면 그 User / 없으면 User 생성 + SocialAccount 연결
 * → 자체 토큰 발급(refresh 저장). 재발급은 회전.
 * 상태 변경은 Entity 메서드, Facade는 load·조율·save만.
 */
@Service
@RequiredArgsConstructor
public class AuthFacade {

	/** 게스트 로그인 시 토큰 provider 클레임 값. 소셜(kakao/google)과 구분한다. */
	public static final String GUEST_PROVIDER = "guest";

	/** 휴대폰 식별 게스트의 SocialAccount provider 값(베타) — 소셜 provider들과 같은 연결 테이블을 공유한다. */
	public static final String PHONE_PROVIDER = "phone";

	private final List<OAuthClient> oauthClients; // provider 전략들(주입)
	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final TokenProvider tokenProvider;
	private final RefreshTokenStore refreshTokenStore;

	/** FE 주도 플로우용: provider·code로 로그인(state 검증은 FE 책임). */
	@Transactional
	public AuthResult.Login login(AuthCriteria.Login criteria) {
		Provider target = Provider.from(criteria.provider());
		OAuthUserInfo info = client(target).fetchUserInfo(criteria.code(), criteria.redirectUri(), criteria.state());

		// (provider, providerUserId)로 기존 연결 조회 → 있으면 그 User, 없으면 신규 가입
		SocialAccount social = socialAccountRepository
				.findByProviderAndProviderUserId(target.code(), info.sub())
				.map(existing -> {
					existing.updateEmail(info.email());
					return socialAccountRepository.save(existing);
				})
				.orElse(null);

		// 연결이 있어도 그 사용자가 탈퇴(soft-delete)했으면 살아있는 행이 없다 → "가입 이력 없음"과 같게 본다.
		// (예전엔 여기서 SOCIAL_ACCOUNT_LINK_BROKEN을 던져 탈퇴자가 영구히 재가입할 수 없었다)
		User user = social == null ? null : userRepository.findById(social.getUserId()).orElse(null);

		if (user == null) {
			// 신규 가입: 소셜 동의항목의 이름·연령대·출생연도까지 반영(재로그인 시엔 사용자 편집을 덮지 않도록 미반영).
			user = userRepository.save(
					User.createFromSocial(info.nickname(), info.name(), info.ageGroup(), info.birthYear()));
			// 탈퇴 후 재가입이면 유니크 제약(provider, providerUserId) 때문에 새 행을 못 넣는다 → 잔존 연결을 새 사용자로 이관.
			social = socialAccountRepository.save(social == null
					? SocialAccount.create(user.getId(), target.code(), info.sub(), info.email())
					: relinked(social, user.getId()));
		}

		AuthTokens tokens = tokenProvider.issue(user, target.code());
		refreshTokenStore.save(user.getId(), tokens.refreshToken());
		return AuthResult.Login.of(user, target.code(), social.getEmail(), tokens);
	}

	/**
	 * 게스트 로그인: 소셜 연결 없이 임시 사용자를 만들고 자체 토큰을 발급한다.
	 * 소셜 로그인과 동일한 토큰 체계를 쓰므로 발급된 access 토큰으로 기록 등 로그인 전용 API를 그대로 사용할 수 있다.
	 * refresh도 저장해 재발급(회전)을 지원한다. 소셜 계정이 없으므로 email은 null.
	 */
	@Transactional
	public AuthResult.Login guestLogin() {
		User user = userRepository.save(User.createGuest());
		AuthTokens tokens = tokenProvider.issue(user, GUEST_PROVIDER);
		refreshTokenStore.save(user.getId(), tokens.refreshToken());
		return AuthResult.Login.of(user, GUEST_PROVIDER, null, tokens);
	}

	/**
	 * 휴대폰 번호 식별 게스트 로그인(베타 전용, 기존 로그인 API 무수정 별도 유스케이스).
	 * 카카오 로그인이 테스트 앱 테스터 계정만 허용되는 베타 기간 동안, 재방문 사용자가 같은 계정을 이어 쓰도록
	 * 휴대폰 번호로 식별한다 — 소셜 로그인과 동일한 find-or-create: 정규화된 번호를
	 * (provider={@code phone}, providerUserId=번호)의 {@link SocialAccount}로 연결해
	 * 있으면 그 사용자, 없으면 게스트 사용자를 새로 만들어 연결한다. 토큰 체계는 게스트와 동일.
	 */
	@Transactional
	public AuthResult.Login guestPhoneLogin(String rawPhoneNumber) {
		String phone = PhoneNumber.of(rawPhoneNumber).value();
		SocialAccount social = socialAccountRepository
				.findByProviderAndProviderUserId(PHONE_PROVIDER, phone).orElse(null);
		// 소셜 로그인과 같은 규칙: 연결이 있어도 그 사용자가 탈퇴했으면 새 게스트로 재가입시킨다.
		User user = social == null ? null : userRepository.findById(social.getUserId()).orElse(null);

		if (user == null) {
			user = userRepository.save(User.createGuest());
			socialAccountRepository.save(social == null
					? SocialAccount.create(user.getId(), PHONE_PROVIDER, phone, null)
					: relinked(social, user.getId()));
		}
		AuthTokens tokens = tokenProvider.issue(user, GUEST_PROVIDER);
		refreshTokenStore.save(user.getId(), tokens.refreshToken());
		return AuthResult.Login.of(user, GUEST_PROVIDER, null, tokens);
	}

	/** refresh 검증 통과 시 access/refresh 재발급(회전). */
	@Transactional
	public AuthResult.Login reissue(String refreshToken) {
		TokenClaims claims = tokenProvider.parse(refreshToken)
				.filter(TokenClaims::isRefresh)
				.filter(c -> refreshTokenStore.matches(c.userId(), refreshToken))
				.orElseThrow(() -> new CoreException(AuthErrorCode.INVALID_REFRESH_TOKEN));

		User user = userRepository.findById(claims.userId())
				.orElseThrow(() -> new CoreException(UserErrorCode.USER_NOT_FOUND));
		String provider = claims.provider();
		String email = socialAccountRepository.findByUserIdAndProvider(user.getId(), provider)
				.map(SocialAccount::getEmail).orElse(null);

		AuthTokens tokens = tokenProvider.issue(user, provider);
		refreshTokenStore.save(user.getId(), tokens.refreshToken()); // 회전
		return AuthResult.Login.of(user, provider, email, tokens);
	}

	/** 로그아웃: refresh가 유효하면 저장소에서 폐기(쿠키 만료는 인터페이스 책임). 무효/없음은 무시(멱등). */
	public void logout(String refreshToken) {
		if (refreshToken == null) {
			return;
		}
		tokenProvider.parse(refreshToken)
				.filter(TokenClaims::isRefresh)
				.ifPresent(claims -> refreshTokenStore.remove(claims.userId()));
	}

	/** access 토큰 검증 → 클레임. 무효 시 {@link AuthErrorCode#INVALID_ACCESS_TOKEN}. */
	public TokenClaims requireAccess(String accessToken) {
		return tokenProvider.parse(accessToken)
				.filter(TokenClaims::isAccess)
				.orElseThrow(() -> new CoreException(AuthErrorCode.INVALID_ACCESS_TOKEN));
	}

	public long accessTtlSeconds() {
		return tokenProvider.accessTtlSeconds();
	}

	public long refreshTtlSeconds() {
		return tokenProvider.refreshTtlSeconds();
	}

	/** 탈퇴자의 잔존 연결을 새 사용자로 이관한다(상태 변경은 Entity 메서드에 위임). */
	private static SocialAccount relinked(SocialAccount social, Long newUserId) {
		social.relinkTo(newUserId);
		return social;
	}

	private OAuthClient client(Provider provider) {
		return oauthClients.stream()
				.filter(c -> c.provider() == provider)
				.findFirst()
				.orElseThrow(() -> new CoreException(AuthErrorCode.UNSUPPORTED_PROVIDER, "구현체 없음: " + provider));
	}
}
