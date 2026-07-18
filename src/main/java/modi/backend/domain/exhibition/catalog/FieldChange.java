package modi.backend.domain.exhibition.catalog;

/**
 * 전시 필드 하나의 변경(도메인 값). 사람 수정으로 값이 실제로 바뀐 필드만 만들어진다 —
 * {@link Exhibition#applyAdminEdit}가 "무엇이 바뀌었나"를 판단해 이 목록을 돌려주고, Facade가 이력으로 남긴다.
 *
 * @param fieldName 바뀐 필드의 이름(이력 컬럼 field_name). 응답 필드명과 무관한 도메인 어휘다.
 * @param oldValue  변경 전 값(없었으면 null).
 * @param newValue  변경 후 값(비우는 수정이면 null).
 */
public record FieldChange(String fieldName, String oldValue, String newValue) {
}
