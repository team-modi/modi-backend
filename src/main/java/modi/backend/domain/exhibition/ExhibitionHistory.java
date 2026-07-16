package modi.backend.domain.exhibition;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사람이 전시 필드를 수정한 이력(append-only) — {@code exhibition_history} 매핑.
 *
 * <p><b>행 = 필드 변경 1건.</b> 한 번의 수정이 여러 필드를 바꾸면 <b>같은 {@code edited_at}</b>을 가진 여러 행이 되어
 * "이건 한 액션이었다"가 보존된다(field_edits가 필드별로 쪼개 잃던 묶음). {@code old_value → new_value}로
 * "무엇이 어떻게 바뀌었나"까지 남는다(감사).
 *
 * <p>{@code edited_by}(누가)는 두지 않는다 — 외부에서 수정 작업이 이뤄지는 운영 특성상 주체 식별이 의미가 없다(2026-07-16 결정).
 *
 * <p>ERD의 {@code exhibition_field_edits}(보호 가드)를 대신한다. 그쪽이 막으려던 "재수집 덮어쓰기"는 현재 일어나지
 * 않고(syncCatalog가 목록 필드를 갱신하지 않음), 실재하는 필요는 "외부 수정의 감사"라 이력으로 방향을 바꿨다.
 *
 * <p>{@link Exhibition}과 FK로 묶인다 — 전시가 소유한 자식이라 전시가 사라지면 의미가 없다(정준·벤더층과 달리 재생성 대상 아님).
 */
@Entity
@Table(name = "exhibition_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExhibitionHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "exhibition_id", nullable = false)
	private Long exhibitionId;

	@Column(name = "field_name", nullable = false, length = 50)
	private String fieldName;

	@Column(name = "old_value", columnDefinition = "text")
	private String oldValue;

	@Column(name = "new_value", columnDefinition = "text")
	private String newValue;

	/** 이 변경이 속한 수정 이벤트 시각 — 같은 수정의 여러 필드 변경이 이 값으로 묶인다. */
	@Column(name = "edited_at")
	private LocalDateTime editedAt;

	private ExhibitionHistory(Long exhibitionId, String fieldName, String oldValue, String newValue,
			LocalDateTime editedAt) {
		this.exhibitionId = exhibitionId;
		this.fieldName = fieldName;
		this.oldValue = oldValue;
		this.newValue = newValue;
		this.editedAt = editedAt;
	}

	/** 한 필드 변경을 이력 행으로. {@code editedAt}은 같은 수정의 다른 필드 변경들과 공유해 이벤트를 묶는다. */
	public static ExhibitionHistory of(Long exhibitionId, FieldChange change, LocalDateTime editedAt) {
		return new ExhibitionHistory(exhibitionId, change.fieldName(), change.oldValue(), change.newValue(), editedAt);
	}
}
