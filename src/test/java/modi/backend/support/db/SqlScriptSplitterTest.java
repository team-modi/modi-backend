package modi.backend.support.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SQL 스플리터 단위 검증 — mysqldump 출력의 함정(문자열 안 세미콜론·백슬래시 이스케이프·따옴표 중첩·주석)을
 * 문장 경계로 오인하지 않음을 증명한다. 이게 깨지면 스냅샷 적재가 조용히 잘려 데이터가 유실된다.
 */
class SqlScriptSplitterTest {

	@Test
	@DisplayName("문자열 안 세미콜론은 분리 경계가 아니다")
	void 문자열안_세미콜론_유지() {
		List<String> stmts = SqlScriptSplitter.split("INSERT INTO t VALUES ('a;b;c'); SELECT 1;");
		assertThat(stmts).hasSize(2);
		assertThat(stmts.get(0)).isEqualTo("INSERT INTO t VALUES ('a;b;c')");
		assertThat(stmts.get(1)).isEqualTo("SELECT 1");
	}

	@Test
	@DisplayName("백슬래시로 이스케이프된 따옴표를 문자열 종료로 오인하지 않는다")
	void 백슬래시_이스케이프_따옴표() {
		// 문자열 값은 it's; ok — 뒤의 세미콜론은 여전히 문자열 안이다.
		List<String> stmts = SqlScriptSplitter.split("INSERT INTO t VALUES ('it\\'s; ok'); SELECT 2;");
		assertThat(stmts).hasSize(2);
		assertThat(stmts.get(0)).isEqualTo("INSERT INTO t VALUES ('it\\'s; ok')");
	}

	@Test
	@DisplayName("이스케이프된 백슬래시(줄 끝) 이후 따옴표가 문자열을 정상 종료한다")
	void 이스케이프된_백슬래시() {
		List<String> stmts = SqlScriptSplitter.split("INSERT INTO t VALUES ('path\\\\'); SELECT 3;");
		assertThat(stmts).hasSize(2);
		assertThat(stmts.get(0)).isEqualTo("INSERT INTO t VALUES ('path\\\\')");
	}

	@Test
	@DisplayName("따옴표 중첩('')은 문자열을 종료하지 않는다")
	void 따옴표_중첩() {
		List<String> stmts = SqlScriptSplitter.split("INSERT INTO t VALUES ('a''b;c'); SELECT 4;");
		assertThat(stmts).hasSize(2);
		assertThat(stmts.get(0)).isEqualTo("INSERT INTO t VALUES ('a''b;c')");
	}

	@Test
	@DisplayName("라인 주석(-- , #)은 제거되고 그 안의 세미콜론은 무시된다")
	void 라인_주석_제거() {
		List<String> stmts = SqlScriptSplitter.split("-- comment; still comment\nSELECT 5;\n# another; comment\nSELECT 6;");
		assertThat(stmts).containsExactly("SELECT 5", "SELECT 6");
	}

	@Test
	@DisplayName("블록 주석(mysqldump /*!...*/ 세션 지시 포함)은 제거된다")
	void 블록_주석_제거() {
		List<String> stmts = SqlScriptSplitter.split(
				"/*!40101 SET @X=@@Y */;\nINSERT INTO t VALUES ('json: {\"a\";\"b\"}');\nSELECT 7;");
		assertThat(stmts).containsExactly(
				"INSERT INTO t VALUES ('json: {\"a\";\"b\"}')", "SELECT 7");
	}

	@Test
	@DisplayName("여러 문장은 세미콜론으로 나뉘고 빈 문장은 버린다")
	void 다중_문장() {
		assertThat(SqlScriptSplitter.split("A;;  ;B;C;")).containsExactly("A", "B", "C");
	}
}
