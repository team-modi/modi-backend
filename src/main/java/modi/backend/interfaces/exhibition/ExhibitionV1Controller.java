package modi.backend.interfaces.exhibition;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import modi.backend.application.exhibition.ExhibitionCriteria;
import modi.backend.application.exhibition.ExhibitionFacade;
import modi.backend.application.exhibition.ExhibitionResult;
import modi.backend.interfaces.auth.Authentication;
import modi.backend.interfaces.auth.LoginUser;
import modi.backend.interfaces.auth.OptionalAuthentication;
import modi.backend.interfaces.exhibition.dto.ExhibitionDto;
import modi.backend.interfaces.record.dto.PageResponse;
import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;
import modi.backend.support.response.ApiResponse;

/**
 * 전시 API(03_전시.md). 목록·상세는 공개(로그인 시 CUSTOM 반영 = 선택 인증), 개인 전시 등록은 인증 필수.
 * (프로젝트 컨벤션: 성공 200 — 등록도 200으로 응답. 04_전시_구현.md 결정사항 참고.)
 */
@RestController
@RequestMapping("/api/v1/exhibitions")
@RequiredArgsConstructor
public class ExhibitionV1Controller implements ExhibitionV1ApiSpec {

	private final ExhibitionFacade exhibitionFacade;

	/** 목록/탐색. keyword·date·region·category 필터 + sort(latest|ending|popular, 기본 latest) + 페이지네이션. */
	@Override
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<ExhibitionDto.ListItemResponse>>> list(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String date,
			@RequestParam(required = false) String region,
			@RequestParam(required = false) String category,
			@RequestParam(defaultValue = "latest") String sort,
			@ParameterObject @PageableDefault(size = 20) Pageable pageable,
			@OptionalAuthentication Optional<LoginUser> loginUser) {
		ExhibitionCriteria.Search criteria = new ExhibitionCriteria.Search(
				keyword, parseDate(date), region, category, sort, requesterId(loginUser));
		PageResponse<ExhibitionDto.ListItemResponse> data = PageResponse.from(
				exhibitionFacade.search(criteria, pageable).map(ExhibitionDto.ListItemResponse::from));
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	/** [API-SYNC E2E 데모용 임시 엔드포인트 — 검증 후 제거] 추천 전시(고정 응답). */
	@GetMapping("/featured")
	public ResponseEntity<ApiResponse<String>> featured() {
		return ResponseEntity.ok(ApiResponse.success("featured exhibitions (demo)"));
	}

	/** 상세. CATALOG 공개 / CUSTOM은 등록자 본인만(타인 접근 403). */
	@Override
	@GetMapping("/{exhibitionId}")
	public ResponseEntity<ApiResponse<ExhibitionDto.DetailResponse>> detail(
			@PathVariable Long exhibitionId,
			@OptionalAuthentication Optional<LoginUser> loginUser) {
		ExhibitionResult.Detail result = exhibitionFacade.getDetail(
				new ExhibitionCriteria.Detail(exhibitionId, requesterId(loginUser)));
		return ResponseEntity.ok(ApiResponse.success(ExhibitionDto.DetailResponse.from(result)));
	}

	/** 개인 전시 등록. 인증 필수. 제목 필수·기간 규칙은 도메인에서 검증. */
	@Override
	@PostMapping("/custom")
	public ResponseEntity<ApiResponse<ExhibitionDto.CreatedResponse>> registerCustom(
			@Authentication LoginUser user,
			@Valid @RequestBody ExhibitionDto.CustomCreateRequest request) {
		ExhibitionResult.Created result = exhibitionFacade.registerCustom(new ExhibitionCriteria.CustomCreate(
				user.userId(), request.title(), request.place(), parseDate(request.startDate()),
				parseDate(request.endDate()), request.region(), request.category(), request.posterUrl()));
		return ResponseEntity.ok(ApiResponse.success(ExhibitionDto.CreatedResponse.from(result)));
	}

	private static Long requesterId(Optional<LoginUser> loginUser) {
		return loginUser.map(LoginUser::userId).orElse(null);
	}

	/** YYYY-MM-DD 문자열 → LocalDate. 빈 값은 null, 형식 오류는 400 INVALID_INPUT. */
	private static LocalDate parseDate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.trim());
		} catch (DateTimeParseException e) {
			throw new CoreException(ErrorType.INVALID_INPUT, "날짜 형식은 YYYY-MM-DD여야 합니다: " + value);
		}
	}
}
