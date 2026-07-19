package modi.backend.support.db;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 스크립트를 문장(statement) 단위로 나눈다 — <b>mysqldump 출력을 안전하게 분리</b>하기 위한 전용 스플리터.
 *
 * <p>Spring {@code ScriptUtils}로는 부족한 이유: mysqldump는 문자열 안의 따옴표를 <b>백슬래시로 이스케이프</b>({@code \'})하고
 * payload(JSON 등)에 세미콜론·백슬래시가 그대로 들어간다. 기본 스플리터가 {@code \'}를 문자열 종료로 오해하면 그 뒤 {@code ;}를
 * 문장 경계로 잘못 잘라 적재가 깨진다. 그래서 다음을 직접 처리한다:
 * <ul>
 *   <li>단일 따옴표 문자열 안에서 {@code \\}·{@code \'} 등 <b>백슬래시 이스케이프</b>와 {@code ''}(따옴표 중첩) 인식</li>
 *   <li>{@code -- } 및 {@code #} 라인 주석, {@code /* ... *&#47;} 블록 주석(mysqldump {@code /*!...*&#47;} 세션 지시 포함) 제거</li>
 *   <li>문자열 밖 {@code ;}에서만 분리</li>
 * </ul>
 * 반환된 문장들은 <b>한 커넥션에서 순서대로</b> 실행해야 세션 변수({@code SET NAMES}·FK 체크)가 일관되게 적용된다.
 */
public final class SqlScriptSplitter {

	private SqlScriptSplitter() {
	}

	public static List<String> split(String script) {
		List<String> statements = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		int i = 0;
		int n = script.length();
		boolean inString = false;
		while (i < n) {
			char c = script.charAt(i);
			if (inString) {
				sb.append(c);
				if (c == '\\' && i + 1 < n) { // 백슬래시 이스케이프 — 다음 문자는 리터럴, 종료로 보지 않는다
					sb.append(script.charAt(i + 1));
					i += 2;
					continue;
				}
				if (c == '\'') {
					if (i + 1 < n && script.charAt(i + 1) == '\'') { // '' → 따옴표 리터럴, 문자열 유지
						sb.append('\'');
						i += 2;
						continue;
					}
					inString = false;
				}
				i++;
				continue;
			}
			// 문자열 밖 — 주석 처리 먼저
			if (c == '-' && i + 1 < n && script.charAt(i + 1) == '-'
					&& (i + 2 >= n || isSpace(script.charAt(i + 2)))) {
				i = skipToLineEnd(script, i);
				continue;
			}
			if (c == '#') {
				i = skipToLineEnd(script, i);
				continue;
			}
			if (c == '/' && i + 1 < n && script.charAt(i + 1) == '*') {
				i = skipBlockComment(script, i + 2);
				continue;
			}
			if (c == '\'') {
				inString = true;
				sb.append(c);
				i++;
				continue;
			}
			if (c == ';') {
				addIfNotBlank(statements, sb);
				sb.setLength(0);
				i++;
				continue;
			}
			sb.append(c);
			i++;
		}
		addIfNotBlank(statements, sb);
		return statements;
	}

	private static boolean isSpace(char c) {
		return c == ' ' || c == '\t' || c == '\r' || c == '\n';
	}

	private static int skipToLineEnd(String s, int i) {
		while (i < s.length() && s.charAt(i) != '\n') {
			i++;
		}
		return i;
	}

	private static int skipBlockComment(String s, int i) {
		int n = s.length();
		while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
			i++;
		}
		return Math.min(i + 2, n); // '*/' 다음으로
	}

	private static void addIfNotBlank(List<String> statements, StringBuilder sb) {
		String stmt = sb.toString().trim();
		if (!stmt.isEmpty()) {
			statements.add(stmt);
		}
	}
}
