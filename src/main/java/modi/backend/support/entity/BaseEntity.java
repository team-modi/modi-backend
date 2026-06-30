package modi.backend.support.entity;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

/**
 * 모든 엔티티 공통 베이스. id + 생성/수정/삭제 시각(soft delete) + 유효성 훅(guard).
 */
@MappedSuperclass
@Getter
public abstract class BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private final Long id = 0L;

	@Column(name = "created_at", nullable = false, updatable = false)
	private ZonedDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private ZonedDateTime updatedAt;

	@Column(name = "deleted_at")
	private ZonedDateTime deletedAt;

	/**
	 * 엔티티의 유효성을 검증한다.
	 * 이 메소드는 PrePersist 및 PreUpdate 시점에 호출된다.
	 */
	protected void guard() {
	}

	@PrePersist
	private void prePersist() {
		guard();

		ZonedDateTime now = ZonedDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	private void preUpdate() {
		guard();

		this.updatedAt = ZonedDateTime.now();
	}

	/**
	 * delete 연산은 멱등하게 동작할 수 있도록 한다. (삭제된 엔티티를 다시 삭제해도 동일한 결과가 나오도록)
	 */
	public void delete() {
		if (this.deletedAt == null) {
			this.deletedAt = ZonedDateTime.now();
		}
	}

	/**
	 * restore 연산은 멱등하게 동작할 수 있도록 한다. (삭제되지 않은 엔티티를 복원해도 동일한 결과가 나오도록)
	 */
	public void restore() {
		if (this.deletedAt != null) {
			this.deletedAt = null;
		}
	}
}
