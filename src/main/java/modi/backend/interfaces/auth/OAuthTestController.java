package modi.backend.interfaces.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import modi.backend.application.auth.AuthFacade;
import modi.backend.application.auth.AuthResult;
import modi.backend.config.CookieProperties;
import modi.backend.config.OAuthProperties;

/**
 * ⚠️ 로컬 테스트/데모 전용 컨트롤러. (resources/static/index.html에서 호출)
 * BE가 직접 authorize로 보내고 콜백을 받아 결과 HTML을 보여준다.
 * 실제 운영 플로우는 FE 주도 + {@link AuthV1Controller}(/api/v1/auth).
 */
@RestController
@RequiredArgsConstructor
public class OAuthTestController {

	private final AuthFacade authFacade;
	private final OAuthProperties oauthProperties;
	private final CookieProperties cookieProperties;

	/** 로그인 시작: provider authorize로 리다이렉트. */
	@GetMapping("/oauth/{provider}/login")
	public ResponseEntity<Void> start(@PathVariable String provider) {
		String authorizeUrl = authFacade.authorizeUrl(provider, oauthProperties.redirectUri());
		return ResponseEntity.status(HttpStatus.FOUND).header("Location", authorizeUrl).build();
	}

	/** 카카오·구글 공통 콜백: 자체 JWT 발급 후 결과 HTML. */
	@GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
	@ResponseBody
	public String callback(@RequestParam(required = false) String code,
						   @RequestParam(required = false) String state,
						   @RequestParam(required = false) String error,
						   HttpServletResponse response) {
		if (error != null) {
			return page("로그인 취소/오류: " + error, null);
		}
		try {
			AuthResult.Login result = authFacade.completeLogin(state, code, oauthProperties.redirectUri());
			response.addHeader("Set-Cookie", RefreshCookie.build(
					result.refreshToken(), authFacade.refreshTtlSeconds(),
					cookieProperties.secure(), cookieProperties.sameSite()));
			return page(null, result);
		} catch (RuntimeException e) {
			return page("로그인 실패: " + e.getMessage(), null);
		}
	}

	private String page(String message, AuthResult.Login result) {
		StringBuilder sb = new StringBuilder();
		sb.append("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><title>로그인 결과</title>")
				.append("<style>body{font-family:system-ui,sans-serif;max-width:760px;margin:40px auto;padding:0 16px}")
				.append("pre{background:#f4f4f5;border-radius:6px;padding:8px;word-break:break-all;white-space:pre-wrap}")
				.append("button{padding:8px 14px;margin:4px 0;cursor:pointer}.box{border:1px solid #e4e4e7;border-radius:10px;padding:16px;margin:12px 0}")
				.append("</style></head><body>");
		if (message != null) {
			sb.append("<h2>⚠️ ").append(escape(message)).append("</h2><p><a href=\"/\">← 처음으로</a></p>");
		} else {
			sb.append("<h2>✅ 로그인 성공 (").append(escape(result.provider())).append(")</h2>")
					.append("<div class=\"box\"><b>사용자</b><br>userId: ").append(result.userId())
					.append("<br>email: ").append(escape(result.email() == null ? "(없음/비동의)" : result.email()))
					.append("<br>nickname: ").append(escape(result.nickname() == null ? "(없음)" : result.nickname()))
					.append("<br>profile_completed: ").append(result.profileCompleted())
					.append("</div>")
					.append("<div class=\"box\"><b>access token</b> (15분)<pre id=\"at\">").append(escape(result.accessToken())).append("</pre>")
					.append("<small>refresh token은 HttpOnly 쿠키로 저장됨</small></div>")
					.append("<div class=\"box\"><button onclick=\"callMe()\">/api/v1/auth/me</button> ")
					.append("<button onclick=\"doRefresh()\">/api/v1/auth/refresh</button><pre id=\"out\"></pre></div>")
					.append("<p><a href=\"/\">← 처음으로</a></p>")
					.append("<script>let at=document.getElementById('at').textContent.trim();")
					.append("function show(t){document.getElementById('out').textContent=t;}")
					.append("async function callMe(){const r=await fetch('/api/v1/auth/me',{headers:{Authorization:'Bearer '+at}});show('/me '+r.status+'\\n'+await r.text());}")
					.append("async function doRefresh(){const r=await fetch('/api/v1/auth/refresh',{method:'POST',credentials:'include'});const t=await r.text();show('/refresh '+r.status+'\\n'+t);try{const j=JSON.parse(t);if(j.data&&j.data.accessToken){at=j.data.accessToken;document.getElementById('at').textContent=at;}}catch(e){}}")
					.append("</script>");
		}
		return sb.append("</body></html>").toString();
	}

	private String escape(String s) {
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
