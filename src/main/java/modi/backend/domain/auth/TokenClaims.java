package modi.backend.domain.auth;

/**
 * 자체 JWT에서 파싱한 클레임(VO). type = access | refresh.
 * access엔 nickname·profileCompleted 포함(FE 라우팅용), refresh엔 식별 위주.
 */
public record TokenClaims(Long userId, String type, String provider, String nickname, Boolean profileCompleted) {

	public boolean isAccess() {
		return "access".equals(type);
	}

	public boolean isRefresh() {
		return "refresh".equals(type);
	}
}
