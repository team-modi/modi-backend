package modi.backend.application.record;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.application.exhibition.ExhibitionFacade;
import modi.backend.application.exhibition.ExhibitionResult;
import modi.backend.domain.record.AiStatus;
import modi.backend.domain.record.ExhibitionSnapshot;
import modi.backend.domain.record.KeywordSource;
import modi.backend.domain.record.Record;
import modi.backend.domain.record.RecordEmotion;
import modi.backend.domain.record.RecordErrorCode;
import modi.backend.domain.record.RecordKeyword;
import modi.backend.domain.record.RecordMedia;
import modi.backend.domain.record.RecordMediaType;
import modi.backend.domain.record.WriteMode;
import modi.backend.infra.record.RecordJpaRepository;
import modi.backend.interfaces.record.dto.BookmarkResponse;
import modi.backend.interfaces.record.dto.PageResponse;
import modi.backend.interfaces.record.dto.RecordCreateRequest;
import modi.backend.interfaces.record.dto.RecordCreateResponse;
import modi.backend.interfaces.record.dto.RecordDetailResponse;
import modi.backend.interfaces.record.dto.RecordListItemResponse;
import modi.backend.interfaces.record.dto.RecordMediaRequest;
import modi.backend.interfaces.record.dto.RecordMediaResponse;
import modi.backend.interfaces.record.dto.RecordUpdateRequest;
import modi.backend.support.error.CoreException;

@Service
@RequiredArgsConstructor
public class RecordService {

	private static final int MAX_MEDIA_COUNT = 3;
	private static final long PHOTO_MAX_BYTES = 10L * 1024 * 1024;
	private static final long VIDEO_MAX_BYTES = 100L * 1024 * 1024;

	private final RecordJpaRepository recordRepository;
	private final ExhibitionFacade exhibitionFacade;
	private final Clock clock = Clock.systemDefaultZone();

	@Transactional
	public RecordCreateResponse create(Long userId, RecordCreateRequest request) {
		LocalDate viewedAt = resolveViewedAt(request.viewedAt());
		validateContentRules(viewedAt, request.emotionCodes(), request.media());

		ExhibitionResult.Detail detail = exhibitionFacade.getForSnapshot(request.exhibitionId(), userId);
		ExhibitionSnapshot snapshot = new ExhibitionSnapshot(detail.title(), detail.type(), detail.posterUrl(),
				detail.place(), detail.region(), detail.category(), detail.startDate(), detail.endDate());

		AiStatus aiStatus = resolveAiStatus(request);
		Record record = Record.create(userId, request.exhibitionId(), snapshot, request.writeMode(), viewedAt,
				request.content(), request.aiSummary(), request.representativeEmotion(), request.cardPhrase(), aiStatus);
		record.replaceEmotions(toEmotions(request.emotionCodes()));
		record.replaceKeywords(toKeywords(request.userKeywords(), request.aiKeywords()));
		record.replaceMedia(toMedia(request.media()));

		Record saved = recordRepository.save(record);
		return new RecordCreateResponse(saved.getId(), saved.getExhibitionId(), saved.getWriteMode(), saved.getViewedAt(),
				saved.getAiStatus(), saved.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public PageResponse<RecordListItemResponse> search(Long userId, String keyword, String emotion, Long exhibitionId,
			Boolean bookmarked, WriteMode writeMode, LocalDate fromViewedAt, LocalDate toViewedAt, Pageable pageable) {
		Page<RecordListItemResponse> page = recordRepository.search(userId, blankToNull(keyword), blankToNull(emotion),
						exhibitionId, bookmarked, writeMode, fromViewedAt, toViewedAt, pageable)
				.map(this::toListItem);
		return PageResponse.from(page);
	}

	@Transactional(readOnly = true)
	public RecordDetailResponse get(Long userId, Long recordId) {
		Record record = getExisting(recordId);
		validateOwner(record, userId);
		return toDetail(record);
	}

	@Transactional
	public RecordDetailResponse update(Long userId, Long recordId, RecordUpdateRequest request) {
		Record record = getExisting(recordId);
		validateOwner(record, userId);

		LocalDate viewedAt = resolveViewedAt(request.viewedAt());
		validateContentRules(viewedAt, request.emotionCodes(), request.media());
		AiStatus aiStatus = hasText(request.aiSummary()) ? AiStatus.READY : AiStatus.PENDING;

		record.replaceContent(viewedAt, request.content(), request.aiSummary(), request.representativeEmotion(),
				request.cardPhrase(), aiStatus);
		record.replaceEmotions(toEmotions(request.emotionCodes()));
		record.replaceKeywords(toKeywords(request.userKeywords(), request.aiKeywords()));
		record.replaceMedia(toMedia(request.media()));

		recordRepository.flush();
		return toDetail(record);
	}

	@Transactional
	public void delete(Long userId, Long recordId) {
		Record record = getExisting(recordId);
		validateOwner(record, userId);
		record.delete();
	}

	@Transactional
	public BookmarkResponse bookmark(Long userId, Long recordId) {
		Record record = getExisting(recordId);
		validateOwner(record, userId);
		record.bookmark();
		return new BookmarkResponse(record.getId(), record.isBookmarked());
	}

	@Transactional
	public BookmarkResponse unbookmark(Long userId, Long recordId) {
		Record record = getExisting(recordId);
		validateOwner(record, userId);
		record.unbookmark();
		return new BookmarkResponse(record.getId(), record.isBookmarked());
	}

	private Record getExisting(Long recordId) {
		return recordRepository.findByIdAndDeletedAtIsNull(recordId)
				.orElseThrow(() -> new CoreException(RecordErrorCode.RECORD_NOT_FOUND));
	}

	private void validateOwner(Record record, Long userId) {
		if (!Objects.equals(record.getUserId(), userId)) {
			throw new CoreException(RecordErrorCode.FORBIDDEN_RECORD);
		}
	}

	private LocalDate resolveViewedAt(LocalDate requestedViewedAt) {
		LocalDate viewedAt = requestedViewedAt == null ? LocalDate.now(clock) : requestedViewedAt;
		if (viewedAt.isAfter(LocalDate.now(clock))) {
			throw new CoreException(RecordErrorCode.INVALID_RECORD_INPUT, "관람일은 미래 날짜일 수 없습니다.");
		}
		return viewedAt;
	}

	private void validateContentRules(LocalDate viewedAt, List<String> emotionCodes, List<RecordMediaRequest> media) {
		if (viewedAt == null) {
			throw new CoreException(RecordErrorCode.INVALID_RECORD_INPUT);
		}
		if (emotionCodes == null || emotionCodes.isEmpty()) {
			throw new CoreException(RecordErrorCode.INVALID_RECORD_INPUT, "감정을 1개 이상 선택해 주세요.");
		}
		validateMedia(media);
	}

	private void validateMedia(List<RecordMediaRequest> media) {
		if (media == null || media.isEmpty()) {
			return;
		}
		if (media.size() > MAX_MEDIA_COUNT) {
			throw new CoreException(RecordErrorCode.INVALID_RECORD_MEDIA, "미디어는 최대 3개까지 첨부할 수 있습니다.");
		}
		long distinctSortOrders = media.stream().map(RecordMediaRequest::sortOrder).distinct().count();
		if (distinctSortOrders != media.size()) {
			throw new CoreException(RecordErrorCode.INVALID_RECORD_MEDIA, "미디어 정렬 순서가 중복되었습니다.");
		}
		for (RecordMediaRequest item : media) {
			if (item.type() == RecordMediaType.PHOTO && item.sizeBytes() > PHOTO_MAX_BYTES) {
				throw new CoreException(RecordErrorCode.INVALID_RECORD_MEDIA, "사진은 10MB 이하만 첨부할 수 있습니다.");
			}
			if (item.type() == RecordMediaType.VIDEO && item.sizeBytes() > VIDEO_MAX_BYTES) {
				throw new CoreException(RecordErrorCode.INVALID_RECORD_MEDIA, "영상은 100MB 이하만 첨부할 수 있습니다.");
			}
		}
	}

	private AiStatus resolveAiStatus(RecordCreateRequest request) {
		if (request.writeMode() == WriteMode.DIRECT && !hasText(request.aiSummary())) {
			return AiStatus.PENDING;
		}
		return hasText(request.aiSummary()) ? AiStatus.READY : AiStatus.PENDING;
	}

	private List<RecordEmotion> toEmotions(List<String> emotionCodes) {
		return emotionCodes.stream()
				.filter(RecordService::hasText)
				.distinct()
				.map(RecordEmotion::create)
				.toList();
	}

	private List<RecordKeyword> toKeywords(List<String> userKeywords, List<String> aiKeywords) {
		return Stream.concat(
						toKeywordStream(userKeywords, KeywordSource.USER),
						toKeywordStream(aiKeywords, KeywordSource.AI))
				.toList();
	}

	private Stream<RecordKeyword> toKeywordStream(List<String> keywords, KeywordSource source) {
		if (keywords == null) {
			return Stream.empty();
		}
		return keywords.stream()
				.filter(RecordService::hasText)
				.distinct()
				.map(keyword -> RecordKeyword.create(keyword, source));
	}

	private List<RecordMedia> toMedia(List<RecordMediaRequest> media) {
		if (media == null) {
			return List.of();
		}
		return media.stream()
				.sorted(Comparator.comparingInt(RecordMediaRequest::sortOrder))
				.map(item -> RecordMedia.create(item.type(), item.url(), item.sortOrder(), item.sizeBytes()))
				.toList();
	}

	private RecordListItemResponse toListItem(Record record) {
		String thumbnailUrl = record.getMedia().stream()
				.sorted(Comparator.comparingInt(RecordMedia::getSortOrder))
				.map(RecordMedia::getUrl)
				.findFirst()
				.orElse(null);
		return new RecordListItemResponse(record.getId(), record.getExhibitionId(), thumbnailUrl, record.getAiSummary(),
				record.getRepresentativeEmotion(), record.isBookmarked(), record.getWriteMode(), record.getViewedAt(),
				record.getCreatedAt(), record.getExhibitionTitle(), record.getExhibitionType(),
				record.getExhibitionPosterUrl(), record.getExhibitionPlace(), record.getExhibitionRegion(),
				record.getExhibitionCategory(), record.getExhibitionStartDate(), record.getExhibitionEndDate());
	}

	private RecordDetailResponse toDetail(Record record) {
		List<String> userKeywords = record.getKeywords().stream()
				.filter(keyword -> keyword.getSource() == KeywordSource.USER)
				.map(RecordKeyword::getKeyword)
				.toList();
		List<String> aiKeywords = record.getKeywords().stream()
				.filter(keyword -> keyword.getSource() == KeywordSource.AI)
				.map(RecordKeyword::getKeyword)
				.toList();
		List<String> emotionCodes = record.getEmotions().stream()
				.map(RecordEmotion::getEmotionCode)
				.toList();
		List<RecordMediaResponse> media = record.getMedia().stream()
				.sorted(Comparator.comparingInt(RecordMedia::getSortOrder))
				.map(item -> new RecordMediaResponse(item.getId(), item.getType(), item.getUrl(), item.getSortOrder(),
						item.getSizeBytes()))
				.toList();
		return new RecordDetailResponse(record.getId(), record.getExhibitionId(), record.getWriteMode(),
				record.getAiStatus(), record.getViewedAt(), record.getContent(), record.getAiSummary(), aiKeywords,
				userKeywords, record.getRepresentativeEmotion(), record.getCardPhrase(), emotionCodes,
				record.isBookmarked(), media, record.getCreatedAt(), record.getUpdatedAt(), record.getExhibitionTitle(),
				record.getExhibitionType(), record.getExhibitionPosterUrl(), record.getExhibitionPlace(),
				record.getExhibitionRegion(), record.getExhibitionCategory(), record.getExhibitionStartDate(),
				record.getExhibitionEndDate());
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
