package modi.backend.interfaces.admin;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import modi.backend.application.admin.AdminExhibitionFacade;
import modi.backend.application.admin.AdminExhibitionResult;
import modi.backend.support.response.ApiResponse;

/**
 * 관리자 전시 유지보수 API. `/api-admin/**` 게이트는 {@code AdminAuthInterceptor}가 담당.
 * 프론트가 쓰지 않는 내부 운영용이라 {@link Hidden}으로 Swagger 문서에서 제외하고 별도 ApiSpec도 두지 않는다.
 */
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/exhibitions")
public class AdminExhibitionV1Controller {

	private final AdminExhibitionFacade adminExhibitionFacade;

	/** 저장된 전시 설명을 재파싱해 HTML/워드프레스 마크업을 벗긴다(기존 수집분 정리, 멱등). */
	@PostMapping("/descriptions/reparse")
	public ApiResponse<AdminExhibitionResult.DescriptionReparse> reparseDescriptions() {
		return ApiResponse.success(adminExhibitionFacade.reparseDescriptions());
	}
}
