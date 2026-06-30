package modi.backend.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.domain.auth.AuthErrorCode;
import modi.backend.domain.auth.AuthTokens;
import modi.backend.domain.auth.OAuthClient;
import modi.backend.domain.auth.OAuthUserInfo;
import modi.backend.domain.auth.Provider;
import modi.backend.domain.auth.RefreshTokenStore;
import modi.backend.domain.auth.StateStore;
import modi.backend.domain.auth.TokenProvider;
import modi.backend.domain.user.SocialAccount;
import modi.backend.domain.user.SocialAccountRepository;
import modi.backend.domain.user.User;
import modi.backend.domain.user.UserRepository;
import modi.backend.support.error.CoreException;

/**
 * AuthFacade 유스케이스 단위 검증. 외부 OAuth HTTP·저장소는 모킹해 분기/정책만 본다.
 * (실 OAuth 호출이 끼어 @SpringBootTest가 부적합한 케이스 — 도메인 정책에 집중)
 */
class AuthFacadeTest {

	private OAuthClient kakaoClient;
	private UserRepository userRepository;
	private SocialAccountRepository socialAccountRepository;
	private TokenProvider tokenProvider;
	private AuthFacade authFacade;

	@BeforeEach
	void setUp() {
		kakaoClient = mock(OAuthClient.class);
		given(kakaoClient.provider()).willReturn(Provider.KAKAO);
		userRepository = mock(UserRepository.class);
		socialAccountRepository = mock(SocialAccountRepository.class);
		tokenProvider = mock(TokenProvider.class);
		StateStore stateStore = mock(StateStore.class);
		RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);

		given(tokenProvider.issue(any(), anyString())).willReturn(new AuthTokens("access", "refresh"));

		authFacade = new AuthFacade(List.of(kakaoClient), userRepository, socialAccountRepository,
				tokenProvider, stateStore, refreshTokenStore);
	}

	@Test
	@DisplayName("login: 기존 연결 없으면 User+SocialAccount 신규 생성")
	void login_신규가입() {
		given(kakaoClient.fetchUserInfo(anyString(), anyString()))
				.willReturn(new OAuthUserInfo("sub-1", "a@b.com", "진"));
		given(socialAccountRepository.findByProviderAndProviderUserId("kakao", "sub-1"))
				.willReturn(Optional.empty());
		given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
		given(socialAccountRepository.save(any(SocialAccount.class))).willAnswer(inv -> inv.getArgument(0));

		AuthResult.Login result = authFacade.login(new AuthCriteria.Login("kakao", "code", "https://app/cb"));

		assertThat(result.provider()).isEqualTo("kakao");
		assertThat(result.email()).isEqualTo("a@b.com");
		assertThat(result.accessToken()).isEqualTo("access");
		verify(userRepository).save(any(User.class));
		verify(socialAccountRepository).save(any(SocialAccount.class));
	}

	@Test
	@DisplayName("login: 기존 연결 있으면 그 User로 로그인, 신규 가입 없음")
	void login_기존연결() {
		given(kakaoClient.fetchUserInfo(anyString(), anyString()))
				.willReturn(new OAuthUserInfo("sub-1", "new@b.com", "진"));
		SocialAccount existing = SocialAccount.create(5L, "kakao", "sub-1", "old@b.com");
		given(socialAccountRepository.findByProviderAndProviderUserId("kakao", "sub-1"))
				.willReturn(Optional.of(existing));
		given(socialAccountRepository.save(existing)).willReturn(existing);
		given(userRepository.findById(5L)).willReturn(Optional.of(User.createFromSocial("진")));

		AuthResult.Login result = authFacade.login(new AuthCriteria.Login("kakao", "code", "https://app/cb"));

		assertThat(result.provider()).isEqualTo("kakao");
		assertThat(existing.getEmail()).isEqualTo("new@b.com"); // 이메일 최신화
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	@DisplayName("link: (provider,sub)가 다른 유저 소유면 SOCIAL_ACCOUNT_ALREADY_LINKED")
	void link_다른유저소유_충돌() {
		given(userRepository.findById(1L)).willReturn(Optional.of(User.createFromSocial("나")));
		given(kakaoClient.fetchUserInfo(anyString(), anyString()))
				.willReturn(new OAuthUserInfo("sub-9", "x@b.com", "남"));
		given(socialAccountRepository.findByProviderAndProviderUserId("kakao", "sub-9"))
				.willReturn(Optional.of(SocialAccount.create(2L, "kakao", "sub-9", "x@b.com")));

		assertThatThrownBy(() -> authFacade.link(new AuthCriteria.Link(1L, "kakao", "code", "https://app/cb")))
				.isInstanceOf(CoreException.class)
				.extracting(e -> ((CoreException) e).errorCode())
				.isEqualTo(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);

		verify(socialAccountRepository, never()).save(any(SocialAccount.class));
	}

	@Test
	@DisplayName("link: 미연결이면 로그인 유저에 신규 연결")
	void link_신규연결() {
		given(userRepository.findById(1L)).willReturn(Optional.of(User.createFromSocial("나")));
		given(kakaoClient.fetchUserInfo(anyString(), anyString()))
				.willReturn(new OAuthUserInfo("sub-9", "x@b.com", "남"));
		given(socialAccountRepository.findByProviderAndProviderUserId("kakao", "sub-9"))
				.willReturn(Optional.empty());
		given(socialAccountRepository.save(any(SocialAccount.class))).willAnswer(inv -> inv.getArgument(0));

		AuthResult.Link result = authFacade.link(new AuthCriteria.Link(1L, "kakao", "code", "https://app/cb"));

		assertThat(result.userId()).isEqualTo(1L);
		assertThat(result.provider()).isEqualTo("kakao");
		verify(socialAccountRepository).save(any(SocialAccount.class));
	}

	@Test
	@DisplayName("login: 미지원 provider면 UNSUPPORTED_PROVIDER")
	void login_미지원provider() {
		assertThatThrownBy(() -> authFacade.login(new AuthCriteria.Login("naver", "code", "https://app/cb")))
				.isInstanceOf(CoreException.class)
				.extracting(e -> ((CoreException) e).errorCode())
				.isEqualTo(AuthErrorCode.UNSUPPORTED_PROVIDER);
	}
}
