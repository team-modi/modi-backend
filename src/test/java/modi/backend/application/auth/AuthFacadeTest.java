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
import modi.backend.domain.auth.TokenProvider;
import modi.backend.domain.user.AgeGroup;
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
		RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);

		given(tokenProvider.issue(any(), anyString())).willReturn(new AuthTokens("access", "refresh"));

		authFacade = new AuthFacade(List.of(kakaoClient), userRepository, socialAccountRepository,
				tokenProvider, refreshTokenStore);
	}

	@Test
	@DisplayName("login: 기존 연결 없으면 User+SocialAccount 신규 생성")
	void login_신규가입() {
		given(kakaoClient.fetchUserInfo(anyString(), anyString(), any()))
				.willReturn(new OAuthUserInfo("sub-1", "a@b.com", "홍길동", "진", AgeGroup.TWENTIES, 1993));
		given(socialAccountRepository.findByProviderAndProviderUserId("kakao", "sub-1"))
				.willReturn(Optional.empty());
		given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
		given(socialAccountRepository.save(any(SocialAccount.class))).willAnswer(inv -> inv.getArgument(0));

		AuthResult.Login result = authFacade.login(new AuthCriteria.Login("kakao", "code", "https://app/cb", "state"));

		assertThat(result.provider()).isEqualTo("kakao");
		assertThat(result.email()).isEqualTo("a@b.com");
		assertThat(result.accessToken()).isEqualTo("access");
		verify(userRepository).save(any(User.class));
		verify(socialAccountRepository).save(any(SocialAccount.class));
	}

	@Test
	@DisplayName("login: 기존 연결 있으면 그 User로 로그인, 신규 가입 없음")
	void login_기존연결() {
		given(kakaoClient.fetchUserInfo(anyString(), anyString(), any()))
				.willReturn(new OAuthUserInfo("sub-1", "new@b.com", "홍길동", "진", AgeGroup.UNSPECIFIED, null));
		SocialAccount existing = SocialAccount.create(5L, "kakao", "sub-1", "old@b.com");
		given(socialAccountRepository.findByProviderAndProviderUserId("kakao", "sub-1"))
				.willReturn(Optional.of(existing));
		given(socialAccountRepository.save(existing)).willReturn(existing);
		given(userRepository.findById(5L)).willReturn(Optional.of(User.createFromSocial("진")));

		AuthResult.Login result = authFacade.login(new AuthCriteria.Login("kakao", "code", "https://app/cb", "state"));

		assertThat(result.provider()).isEqualTo("kakao");
		assertThat(existing.getEmail()).isEqualTo("new@b.com"); // 이메일 최신화
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	@DisplayName("login: 미지원 provider면 UNSUPPORTED_PROVIDER")
	void login_미지원provider() {
		assertThatThrownBy(() -> authFacade.login(new AuthCriteria.Login("facebook", "code", "https://app/cb", "state")))
				.isInstanceOf(CoreException.class)
				.extracting(e -> ((CoreException) e).errorCode())
				.isEqualTo(AuthErrorCode.UNSUPPORTED_PROVIDER);
	}

	@Test
	@DisplayName("guestPhoneLogin: 처음 보는 번호면 게스트 User 생성 + phone SocialAccount 연결(정규화된 번호)")
	void 전화게스트_신규번호_가입() {
		given(socialAccountRepository.findByProviderAndProviderUserId("phone", "01012345678"))
				.willReturn(Optional.empty());
		given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
		given(socialAccountRepository.save(any(SocialAccount.class))).willAnswer(inv -> inv.getArgument(0));

		AuthResult.Login result = authFacade.guestPhoneLogin("010-1234-5678"); // 하이픈 입력도 정규화

		assertThat(result.provider()).isEqualTo(AuthFacade.GUEST_PROVIDER);
		assertThat(result.accessToken()).isEqualTo("access");
		verify(userRepository).save(any(User.class));
		verify(socialAccountRepository).save(any(SocialAccount.class));
	}

	@Test
	@DisplayName("guestPhoneLogin: 이미 연결된 번호면 같은 사용자로 재로그인(신규 가입 없음)")
	void 전화게스트_재로그인_같은계정() {
		SocialAccount existing = SocialAccount.create(7L, "phone", "01012345678", null);
		given(socialAccountRepository.findByProviderAndProviderUserId("phone", "01012345678"))
				.willReturn(Optional.of(existing));
		given(userRepository.findById(7L)).willReturn(Optional.of(User.createGuest()));

		AuthResult.Login result = authFacade.guestPhoneLogin("010 1234 5678"); // 공백 입력도 같은 번호로 정규화

		assertThat(result.provider()).isEqualTo(AuthFacade.GUEST_PROVIDER);
		verify(userRepository, never()).save(any(User.class));
		verify(socialAccountRepository, never()).save(any(SocialAccount.class));
	}

	@Test
	@DisplayName("guestPhoneLogin: 휴대폰 형식이 아니면 INVALID_INPUT(가입/토큰 발급 없음)")
	void 전화게스트_형식오류_400() {
		assertThatThrownBy(() -> authFacade.guestPhoneLogin("02-123-4567"))
				.isInstanceOf(CoreException.class);
		verify(userRepository, never()).save(any(User.class));
	}

	/*
	 * 탈퇴(soft-delete) 후 재로그인.
	 *
	 * 탈퇴는 User만 soft-delete하고 SocialAccount 연결은 그대로 남긴다. 그래서 같은 소셜로 다시 로그인하면
	 * 연결은 살아 있는 채로 조회되는데(userRepository.findById는 살아있는 행만 반환) 사용자가 없어
	 * SOCIAL_ACCOUNT_LINK_BROKEN(500)으로 영구히 재가입이 막혔다(2026-07-19 운영 리포트).
	 *
	 * 기대 동작: 탈퇴한 계정의 잔존 연결은 "가입 이력 없음"과 같게 보고 새 계정으로 재가입시킨다.
	 * (탈퇴한 기존 사용자의 데이터는 되살리지 않는다 — 탈퇴 의미 보존)
	 */

	@Test
	@DisplayName("login: 탈퇴한 사용자가 같은 소셜로 다시 로그인하면 새 계정으로 재가입된다")
	void login_탈퇴후_재로그인_새계정으로_재가입() {
		SocialAccount orphaned = SocialAccount.create(42L, "kakao", "sub-1", "a@b.com");
		given(kakaoClient.fetchUserInfo(anyString(), anyString(), any()))
				.willReturn(new OAuthUserInfo("sub-1", "a@b.com", "홍길동", "진", AgeGroup.TWENTIES, 1993));
		given(socialAccountRepository.findByProviderAndProviderUserId("kakao", "sub-1"))
				.willReturn(Optional.of(orphaned));
		given(userRepository.findById(42L)).willReturn(Optional.empty()); // 탈퇴 → 살아있는 행 없음
		given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
		given(socialAccountRepository.save(any(SocialAccount.class))).willAnswer(inv -> inv.getArgument(0));

		AuthResult.Login result = authFacade
				.login(new AuthCriteria.Login("kakao", "code", "https://app/cb", "state"));

		assertThat(result.accessToken()).isEqualTo("access");
		verify(userRepository).save(any(User.class)); // 새 사용자 생성
		// 유니크 제약(provider, providerUserId) 때문에 새 행을 넣을 수 없다 → 기존 연결을 새 사용자로 재연결
		assertThat(orphaned.getUserId())
				.as("잔존 연결이 새 사용자로 재연결돼야 한다")
				.isNotEqualTo(42L);
	}

	@Test
	@DisplayName("guestPhoneLogin: 탈퇴한 사용자가 같은 번호로 다시 로그인하면 새 계정으로 재가입된다")
	void 전화게스트_탈퇴후_재로그인_새계정으로_재가입() {
		SocialAccount orphaned = SocialAccount.create(42L, "phone", "01012345678", null);
		given(socialAccountRepository.findByProviderAndProviderUserId("phone", "01012345678"))
				.willReturn(Optional.of(orphaned));
		given(userRepository.findById(42L)).willReturn(Optional.empty()); // 탈퇴 → 살아있는 행 없음
		given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
		given(socialAccountRepository.save(any(SocialAccount.class))).willAnswer(inv -> inv.getArgument(0));

		AuthResult.Login result = authFacade.guestPhoneLogin("010-1234-5678");

		assertThat(result.accessToken()).isEqualTo("access");
		verify(userRepository).save(any(User.class));
		assertThat(orphaned.getUserId())
				.as("잔존 연결이 새 사용자로 재연결돼야 한다")
				.isNotEqualTo(42L);
	}
}
