package modi.backend.support.text;

import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

/**
 * HTML/워드프레스 마크업이 섞인 텍스트를 사람이 읽기 좋은 평문으로 정리한다.
 * <p>
 * 공공데이터(한눈에보는문화정보) 전시 설명(contents1)이 워드프레스 블록(예: {@code <!-- wp:paragraph --><p style="…">…</p>})으로
 * 내려오므로, 엔티티를 완전히 해제한 뒤 주석·태그를 제거해 문단/줄바꿈만 개행으로 보존한다.
 * 이미 평문인 값에는 사실상 무변경(멱등)이라, 최초 수집 파싱과 기존 데이터 재파싱 양쪽에서 같은 규칙으로 재사용한다.
 */
public final class HtmlTextExtractor {

	private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL); // 워드프레스 블록 주석
	private static final Pattern LINE_BREAK_TAG = Pattern
			.compile("(?i)<br\\s*/?>|</(p|div|li|tr|h[1-6]|blockquote)\\s*>"); // 줄바꿈 유발 태그
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>"); // 나머지 여는/닫는 태그(<p …>, <span …> 등)
	private static final Pattern BLANK_LINE_RUN = Pattern.compile("\\n{3,}"); // 과한 빈 줄 축약
	private static final Pattern SPACE_BEFORE_NEWLINE = Pattern.compile("[ \\t\\x{00a0}]+\\n");

	private HtmlTextExtractor() {
	}

	/**
	 * 마크업 섞인 텍스트 → 평문. null·정리 후 빈 문자열이면 null.
	 * (1) 이스케이프 완전 해제 → (2) 주석 제거 → (3) 문단/줄바꿈 태그를 개행으로 → (4) 나머지 태그 제거 → (5) 공백 정리.
	 */
	public static String toPlainText(String value) {
		if (value == null) {
			return null;
		}
		String text = unescapeFully(value); // 1~2중 이스케이프 완전 해제 → 실제 HTML
		text = HTML_COMMENT.matcher(text).replaceAll(""); // 워드프레스 블록 주석 제거
		text = LINE_BREAK_TAG.matcher(text).replaceAll("\n"); // <br>·</p> 등 → 개행
		text = HTML_TAG.matcher(text).replaceAll(""); // 남은 태그(<p …>·<span …> 등) 제거
		text = text.replace('\u00A0', ' '); // nbsp(\u00A0) → 일반 공백
		text = SPACE_BEFORE_NEWLINE.matcher(text).replaceAll("\n"); // 줄 끝 공백 정리
		text = BLANK_LINE_RUN.matcher(text).replaceAll("\n\n"); // 빈 줄 3개 이상 → 2개
		text = text.strip();
		return text.isEmpty() ? null : text;
	}

	/**
	 * 공공데이터가 본문을 XML-escape해 내려주는데(XML 파싱이 1단계, 값이 한 번 더 이스케이프된 경우도 있어) 더 이상 바뀌지 않을 때까지 해제한다(최대 3회).
	 * 예: 원본 {@code &amp;lt;p&amp;gt;} → XML 파싱 후 {@code &lt;p&gt;} → 재해제 {@code <p>}.
	 */
	private static String unescapeFully(String value) {
		String current = value;
		for (int i = 0; i < 3; i++) {
			String next = HtmlUtils.htmlUnescape(current);
			if (next.equals(current)) {
				break;
			}
			current = next;
		}
		return current;
	}
}
