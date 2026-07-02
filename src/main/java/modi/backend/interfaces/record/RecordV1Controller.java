package modi.backend.interfaces.record;

import java.time.LocalDate;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import modi.backend.application.record.RecordService;
import modi.backend.domain.record.WriteMode;
import modi.backend.interfaces.auth.Authentication;
import modi.backend.interfaces.auth.LoginUser;
import modi.backend.interfaces.record.dto.BookmarkResponse;
import modi.backend.interfaces.record.dto.PageResponse;
import modi.backend.interfaces.record.dto.RecordCreateRequest;
import modi.backend.interfaces.record.dto.RecordCreateResponse;
import modi.backend.interfaces.record.dto.RecordDetailResponse;
import modi.backend.interfaces.record.dto.RecordListItemResponse;
import modi.backend.interfaces.record.dto.RecordUpdateRequest;
import modi.backend.support.response.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/records")
@SecurityRequirement(name = "bearerAuth")
public class RecordV1Controller {

	private final RecordService recordService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "기록 작성", description = "전시 관람 기록을 작성한다. 개발 중에는 X-User-Id 헤더로 사용자를 식별한다.")
	public ApiResponse<RecordCreateResponse> create(
			@Parameter(hidden = true) @Authentication LoginUser loginUser,
			@Valid @RequestBody RecordCreateRequest request) {
		return ApiResponse.success(recordService.create(loginUser.userId(), request));
	}

	@GetMapping
	@Operation(summary = "내 기록 목록 조회", description = "아카이브 목록을 조회한다. 본인 기록만 반환한다.")
	public ApiResponse<PageResponse<RecordListItemResponse>> search(
			@Parameter(hidden = true) @Authentication LoginUser loginUser,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String emotion,
			@RequestParam(required = false) Long exhibitionId,
			@RequestParam(required = false) Boolean bookmarked,
			@RequestParam(required = false) WriteMode writeMode,
			@RequestParam(required = false) LocalDate fromViewedAt,
			@RequestParam(required = false) LocalDate toViewedAt,
			@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.success(recordService.search(loginUser.userId(), keyword, emotion, exhibitionId, bookmarked,
				writeMode, fromViewedAt, toViewedAt, pageable));
	}

	@GetMapping("/exhibitions/visited")
	@Operation(summary = "내가 다녀온 전시 목록", description = "본인이 기록을 남긴 전시 목록을 조회한다. 기존 기록 목록 조회와 동일한 파라미터/응답을 사용한다.")
	public ApiResponse<PageResponse<RecordListItemResponse>> visitedExhibitions(
			@Parameter(hidden = true) @Authentication LoginUser loginUser,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String emotion,
			@RequestParam(required = false) Long exhibitionId,
			@RequestParam(required = false) Boolean bookmarked,
			@RequestParam(required = false) WriteMode writeMode,
			@RequestParam(required = false) LocalDate fromViewedAt,
			@RequestParam(required = false) LocalDate toViewedAt,
			@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.success(recordService.search(loginUser.userId(), keyword, emotion, exhibitionId, bookmarked,
				writeMode, fromViewedAt, toViewedAt, pageable));
	}

	@GetMapping("/{recordId}")
	@Operation(summary = "기록 상세 조회", description = "본인 기록 상세를 조회한다.")
	public ApiResponse<RecordDetailResponse> get(
			@Parameter(hidden = true) @Authentication LoginUser loginUser,
			@Parameter(description = "기록 ID") @PathVariable Long recordId) {
		return ApiResponse.success(recordService.get(loginUser.userId(), recordId));
	}

	@PutMapping("/{recordId}")
	@Operation(summary = "기록 수정", description = "본인 기록의 감상, 감정, 키워드, 미디어를 교체한다.")
	public ApiResponse<RecordDetailResponse> update(
			@Parameter(hidden = true) @Authentication LoginUser loginUser,
			@PathVariable Long recordId,
			@Valid @RequestBody RecordUpdateRequest request) {
		return ApiResponse.success(recordService.update(loginUser.userId(), recordId, request));
	}

	@DeleteMapping("/{recordId}")
	@Operation(summary = "기록 삭제", description = "본인 기록을 소프트 삭제한다.")
	public ApiResponse<Object> delete(
			@Parameter(hidden = true) @Authentication LoginUser loginUser,
			@PathVariable Long recordId) {
		recordService.delete(loginUser.userId(), recordId);
		return ApiResponse.success();
	}

	@PostMapping("/{recordId}/bookmark")
	@Operation(summary = "기록 북마크", description = "본인 기록을 북마크한다. 이미 북마크된 경우에도 성공한다.")
	public ApiResponse<BookmarkResponse> bookmark(
			@Parameter(hidden = true) @Authentication LoginUser loginUser,
			@PathVariable Long recordId) {
		return ApiResponse.success(recordService.bookmark(loginUser.userId(), recordId));
	}

	@DeleteMapping("/{recordId}/bookmark")
	@Operation(summary = "기록 북마크 해제", description = "본인 기록의 북마크를 해제한다. 이미 해제된 경우에도 성공한다.")
	public ApiResponse<BookmarkResponse> unbookmark(
			@Parameter(hidden = true) @Authentication LoginUser loginUser,
			@PathVariable Long recordId) {
		return ApiResponse.success(recordService.unbookmark(loginUser.userId(), recordId));
	}
}
