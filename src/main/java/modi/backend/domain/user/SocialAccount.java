package modi.backend.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import modi.backend.support.entity.BaseEntity;

/**
 * 소셜 연결. 한 User가 여러 개 보유(카카오/구글/네이버 …). (provider + providerUserId)로 유일.
 * User는 경계 참조라 @ManyToOne 대신 userId(값)로 참조한다(컨벤션).
 * id·생성/수정/삭제 시각은 {@link BaseEntity}.
 */
@Entity
@Table(name = "social_accounts",
		uniqueConstraints = @UniqueConstraint(name = "uq_provider_provider_user_id", columnNames = {"provider", "providerUserId"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseEntity {

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private String provider; // kakao | google | naver

	@Column(nullable = false)
	private String providerUserId; // provider 내 고유 식별자(=sub)

	@Column // provider 제공 이메일 (카카오 비동의 시 null)
	private String email;

	private SocialAccount(Long userId, String provider, String providerUserId, String email) {
		this.userId = userId;
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.email = email;
	}

	public static SocialAccount create(Long userId, String provider, String providerUserId, String email) {
		return new SocialAccount(userId, provider, providerUserId, email);
	}

	/** 재로그인 시 provider 이메일 최신화. */
	public void updateEmail(String email) {
		this.email = email;
	}
}
