package modi.backend.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import modi.backend.support.entity.BaseEntity;
import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * 유저(애그리거트 루트) — 프로필. 소셜 연결은 {@link SocialAccount}가 user_id로 참조한다(1:N).
 * 가입 직후엔 nickname만 채우고 나머지 프로필 항목(프로필 이미지·연령대·거주지역)은
 * 사용자가 원할 때 프로필 수정에서 채운다(02_유저.md 결정사항 2).
 * id·생성/수정/삭제 시각은 {@link BaseEntity}.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

	/** 닉네임 RULE: 1~20자, 공백만으로 구성 불가. */
	private static final int NICKNAME_MAX_LENGTH = 20;

	@Column(nullable = false)
	private String nickname;

	@Column(nullable = false)
	private boolean profileCompleted;

	@Column(name = "profile_image_url")
	private String profileImageUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "age_group", nullable = false, length = 20)
	private AgeGroup ageGroup;

	@Enumerated(EnumType.STRING)
	@Column(name = "residence_region", length = 20)
	private ResidenceRegion residenceRegion;

	@Column(name = "residence_district", length = 50)
	private String residenceDistrict;

	/** 출생연도(소셜 동의항목에서 받아온다. 미동의/미지원 시 null). */
	@Column(name = "birth_year")
	private Integer birthYear;

	private User(String nickname, AgeGroup ageGroup, Integer birthYear) {
		this.nickname = nickname;
		this.profileCompleted = false;
		this.ageGroup = ageGroup == null ? AgeGroup.UNSPECIFIED : ageGroup;
		this.birthYear = birthYear;
	}

	/** 소셜 가입 시(연령대·출생연도 미제공): nickname만 채우고 연령대 UNSPECIFIED로 생성. */
	public static User createFromSocial(String nickname) {
		return createFromSocial(nickname, AgeGroup.UNSPECIFIED, null);
	}

	/** 소셜 가입 시: nickname(없으면 기본값)·연령대·출생연도를 소셜 프로필에서 받아 채운다. 프로필은 미완 상태. */
	public static User createFromSocial(String nickname, AgeGroup ageGroup, Integer birthYear) {
		return new User(nickname == null || nickname.isBlank() ? "사용자" : nickname, ageGroup, birthYear);
	}

	/**
	 * 프로필 부분 갱신 — null이 아닌 필드만 반영한다(전달된 필드만 갱신).
	 * 닉네임은 {@code RULE: 닉네임}, 거주지역은 {@code residenceDistrict} 단독 입력 불가 불변식을 검증한다.
	 * 갱신 완료 시 {@code profileCompleted=true}.
	 */
	public void updateProfile(String nickname, String profileImageUrl, AgeGroup ageGroup,
			ResidenceRegion residenceRegion, String residenceDistrict) {
		if (nickname != null) {
			applyNickname(nickname);
		}
		if (profileImageUrl != null) {
			this.profileImageUrl = profileImageUrl;
		}
		if (ageGroup != null) {
			this.ageGroup = ageGroup;
		}
		if (residenceRegion != null) {
			this.residenceRegion = residenceRegion;
		}
		if (residenceDistrict != null) {
			this.residenceDistrict = residenceDistrict;
		}
		validateResidence();
		this.profileCompleted = true;
	}

	/** 닉네임 RULE 검증 후 반영: 1~20자, 공백만 불가. 위반 시 {@link UserErrorCode#INVALID_NICKNAME}. */
	private void applyNickname(String nickname) {
		if (nickname.isBlank() || nickname.length() > NICKNAME_MAX_LENGTH) {
			throw new CoreException(UserErrorCode.INVALID_NICKNAME, "닉네임 규칙 위반: " + nickname);
		}
		this.nickname = nickname;
	}

	/** 거주지역 불변식: 구/군만 있고 시/도가 없는 조합은 불가 → {@link ErrorType#INVALID_INPUT}. */
	private void validateResidence() {
		if (residenceDistrict != null && residenceRegion == null) {
			throw new CoreException(ErrorType.INVALID_INPUT, "거주지역(시/도) 없이 구/군만 입력할 수 없습니다.");
		}
	}
}
