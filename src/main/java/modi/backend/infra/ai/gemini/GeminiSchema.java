package modi.backend.infra.ai.gemini;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Java record/POJO 타입을 Gemini 구조화 출력용 {@code responseSchema}(OpenAPI subset)로 변환한다.
 * {@code completeStructured}가 "정확한 형태"를 강제하도록 type/properties/required/propertyOrdering을 만든다.
 * 지원: String·정수·실수·boolean·enum·List/배열·중첩 record(OBJECT). record면 컴포넌트, 아니면 선언 필드 순서를 따른다.
 */
final class GeminiSchema {

	private GeminiSchema() {
	}

	static Map<String, Object> of(Class<?> type) {
		return objectSchema(type);
	}

	private static Map<String, Object> objectSchema(Class<?> type) {
		Map<String, Object> properties = new LinkedHashMap<>();
		List<String> order = new ArrayList<>();
		if (type.isRecord()) {
			for (RecordComponent rc : type.getRecordComponents()) {
				properties.put(rc.getName(), fieldSchema(rc.getType(), rc.getGenericType(),
						recordDescription(type, rc)));
				order.add(rc.getName());
			}
		} else {
			for (var field : type.getDeclaredFields()) {
				if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				properties.put(field.getName(), fieldSchema(field.getType(), field.getGenericType(),
						field.getAnnotation(JsonPropertyDescription.class)));
				order.add(field.getName());
			}
		}
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "OBJECT");
		schema.put("properties", properties);
		schema.put("required", List.copyOf(order));
		schema.put("propertyOrdering", List.copyOf(order));
		return schema;
	}

	/**
	 * record 컴포넌트의 {@link JsonPropertyDescription}을 찾는다.
	 * 이 애노테이션의 {@code @Target}에는 RECORD_COMPONENT가 없어 컴포넌트에서 바로 읽히지 않으므로
	 * accessor 메서드 → 백킹 필드 순으로 조회한다.
	 */
	private static JsonPropertyDescription recordDescription(Class<?> owner, RecordComponent rc) {
		JsonPropertyDescription onComponent = rc.getAnnotation(JsonPropertyDescription.class);
		if (onComponent != null) {
			return onComponent;
		}
		JsonPropertyDescription onAccessor = rc.getAccessor().getAnnotation(JsonPropertyDescription.class);
		if (onAccessor != null) {
			return onAccessor;
		}
		try {
			return owner.getDeclaredField(rc.getName()).getAnnotation(JsonPropertyDescription.class);
		} catch (NoSuchFieldException e) {
			return null;
		}
	}

	private static Map<String, Object> fieldSchema(Class<?> raw, Type generic, JsonPropertyDescription desc) {
		Map<String, Object> schema = typeSchema(raw, generic);
		if (desc != null && !desc.value().isBlank()) {
			schema.put("description", desc.value());
		}
		return schema;
	}

	private static Map<String, Object> typeSchema(Class<?> raw, Type generic) {
		Map<String, Object> schema = new LinkedHashMap<>();
		if (raw == String.class || raw == CharSequence.class || raw == Character.class || raw == char.class) {
			schema.put("type", "STRING");
		} else if (raw == boolean.class || raw == Boolean.class) {
			schema.put("type", "BOOLEAN");
		} else if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class
				|| raw == short.class || raw == Short.class || raw == byte.class || raw == Byte.class) {
			schema.put("type", "INTEGER");
		} else if (raw == double.class || raw == Double.class || raw == float.class || raw == Float.class) {
			schema.put("type", "NUMBER");
		} else if (raw.isEnum()) {
			schema.put("type", "STRING");
			List<String> values = new ArrayList<>();
			for (Object constant : raw.getEnumConstants()) {
				values.add(((Enum<?>) constant).name());
			}
			schema.put("enum", values);
		} else if (Iterable.class.isAssignableFrom(raw) || raw.isArray()) {
			schema.put("type", "ARRAY");
			schema.put("items", typeSchema(elementType(raw, generic), null));
		} else {
			return objectSchema(raw);
		}
		return schema;
	}

	private static Class<?> elementType(Class<?> raw, Type generic) {
		if (raw.isArray()) {
			return raw.getComponentType();
		}
		if (generic instanceof ParameterizedType parameterized
				&& parameterized.getActualTypeArguments().length > 0
				&& parameterized.getActualTypeArguments()[0] instanceof Class<?> elementClass) {
			return elementClass;
		}
		return String.class; // 알 수 없으면 문자열로 안전 기본
	}
}
