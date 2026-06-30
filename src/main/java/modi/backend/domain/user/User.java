package modi.backend.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import modi.backend.support.entity.BaseEntity;

/**
 * 유저(애그리거트 루트) — 프로필. 소셜 연결은 {@link SocialAccount}가 user_id로 참조한다(1:N).
 * 가입 직후엔 nickname만 채우고 profileCompleted=false → 온보딩에서 프로필 보완.
 * id·생성/수정/삭제 시각은 {@link BaseEntity}.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

	@Column(nullable = false)
	private String nickname;

	@Column(nullable = false)
	private boolean profileCompleted;

	private User(String nickname) {
		this.nickname = nickname;
		this.profileCompleted = false;
	}

	/** 소셜 가입 시: nickname만(없으면 기본값) 채우고 프로필 미완 상태로 생성. */
	public static User createFromSocial(String nickname) {
		return new User(nickname == null || nickname.isBlank() ? "사용자" : nickname);
	}

	/** 온보딩에서 프로필 입력 완료 처리(프로필 항목은 추후 확장). */
	public void completeProfile(String nickname) {
		if (nickname != null && !nickname.isBlank()) {
			this.nickname = nickname;
		}
		this.profileCompleted = true;
	}
}
