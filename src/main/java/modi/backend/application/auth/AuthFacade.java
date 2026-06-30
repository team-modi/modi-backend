package modi.backend.application.auth;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.auth.AuthErrorCode;
import modi.backend.domain.auth.AuthTokens;
import modi.backend.domain.auth.OAuthClient;
import modi.backend.domain.auth.OAuthUserInfo;
import modi.backend.domain.auth.Provider;
import modi.backend.domain.auth.RefreshTokenStore;
import modi.backend.domain.auth.StateStore;
import modi.backend.domain.auth.TokenClaims;
import modi.backend.domain.auth.TokenProvider;
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

	private final List<OAuthClient> oauthClients; // provider 전략들(주입)
	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final TokenProvider tokenProvider;
	private final StateStore stateStore;
	private final RefreshTokenStore refreshTokenStore;

	/** provider 로그인 시작용 authorize URL (state는 provider prefix로 콜백에서 구분). */
	public String authorizeUrl(String provider, String redirectUri) {
		Provider target = Provider.from(provider);
		String state = target.code() + ":" + UUID.randomUUID();
		stateStore.save(state);
		return client(target).buildAuthorizeUrl(state, redirectUri);
	}

	/** state 검증(소비) → code 로그인 완료. provider는 state prefix에서 도출. */
	@Transactional
	public AuthResult.Login completeLogin(String state, String code, String redirectUri) {
		if (state == null || !state.contains(":") || !stateStore.consume(state)) {
			throw new CoreException(AuthErrorCode.INVALID_STATE, "유효하지 않은 state: " + state);
		}
		String provider = state.substring(0, state.indexOf(':'));
		return login(new AuthCriteria.Login(provider, code, redirectUri));
	}

	/** FE 주도 플로우용: provider·code로 로그인(state 검증은 FE 책임). */
	@Transactional
	public AuthResult.Login login(AuthCriteria.Login criteria) {
		Provider target = Provider.from(criteria.provider());
		OAuthUserInfo info = client(target).fetchUserInfo(criteria.code(), criteria.redirectUri());

		// (provider, providerUserId)로 기존 연결 조회 → 있으면 그 User, 없으면 신규 가입
		SocialAccount social = socialAccountRepository
				.findByProviderAndProviderUserId(target.code(), info.sub())
				.map(existing -> {
					existing.updateEmail(info.email());
					return socialAccountRepository.save(existing);
				})
				.orElse(null);

		User user;
		if (social != null) {
			Long userId = social.getUserId();
			user = userRepository.findById(userId)
					.orElseThrow(() -> new CoreException(AuthErrorCode.SOCIAL_ACCOUNT_LINK_BROKEN,
							"연결된 사용자 없음: " + userId));
		} else {
			user = userRepository.save(User.createFromSocial(info.nickname()));
			social = socialAccountRepository.save(
					SocialAccount.create(user.getId(), target.code(), info.sub(), info.email()));
		}

		AuthTokens tokens = tokenProvider.issue(user, target.code());
		refreshTokenStore.save(user.getId(), tokens.refreshToken());
		return AuthResult.Login.of(user, target.code(), social.getEmail(), tokens);
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

	/**
	 * 로그인 유저에 다른 provider 소셜 계정을 추가 연결한다.
	 * (provider, sub)가 이미 다른 유저 소유면 {@link AuthErrorCode#SOCIAL_ACCOUNT_ALREADY_LINKED},
	 * 본인 소유면 멱등(이메일만 최신화).
	 */
	@Transactional
	public AuthResult.Link link(AuthCriteria.Link criteria) {
		Long userId = criteria.userId();
		Provider target = Provider.from(criteria.provider());
		userRepository.findById(userId)
				.orElseThrow(() -> new CoreException(UserErrorCode.USER_NOT_FOUND));
		OAuthUserInfo info = client(target).fetchUserInfo(criteria.code(), criteria.redirectUri());

		SocialAccount social = socialAccountRepository
				.findByProviderAndProviderUserId(target.code(), info.sub())
				.map(existing -> {
					if (!existing.getUserId().equals(userId)) {
						throw new CoreException(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED,
								"이미 user=" + existing.getUserId() + "에 연결됨");
					}
					existing.updateEmail(info.email());
					return socialAccountRepository.save(existing);
				})
				.orElseGet(() -> socialAccountRepository.save(
						SocialAccount.create(userId, target.code(), info.sub(), info.email())));
		return AuthResult.Link.from(social);
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

	private OAuthClient client(Provider provider) {
		return oauthClients.stream()
				.filter(c -> c.provider() == provider)
				.findFirst()
				.orElseThrow(() -> new CoreException(AuthErrorCode.UNSUPPORTED_PROVIDER, "구현체 없음: " + provider));
	}
}
