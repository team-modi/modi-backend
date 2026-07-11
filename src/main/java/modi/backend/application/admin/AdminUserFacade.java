package modi.backend.application.admin;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.activitylog.ActivityLog;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.record.Record;
import modi.backend.domain.remind.Remind;
import modi.backend.domain.user.SocialAccount;
import modi.backend.domain.user.User;
import modi.backend.infra.activitylog.ActivityLogJpaRepository;
import modi.backend.infra.bookmark.ExhibitionBookmarkJpaRepository;
import modi.backend.infra.exhibition.ExhibitionJpaRepository;
import modi.backend.infra.record.RecordJpaRepository;
import modi.backend.infra.remind.RemindJpaRepository;
import modi.backend.infra.user.SocialAccountJpaRepository;
import modi.backend.infra.user.UserJpaRepository;
import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * 관리자 사용자 조회. 여러 도메인 Repo를 조합해 읽기 전용 목록/상세를 만든다.
 * 목록의 사용자별 카운트는 페이지 유저ID들을 한 번에 집계(벌크 in 쿼리)해 N+1을 피한다.
 */
@Service
@RequiredArgsConstructor
public class AdminUserFacade {

	private static final int RECENT_LIMIT = 10;
	private static final int ACTIVITY_LIMIT = 20;
	private static final int TOP_PATHS = 10;
	private static final String PHONE_PROVIDER = "phone"; // 게스트 전화 로그인의 SocialAccount provider(= AuthFacade.PHONE_PROVIDER)

	private final UserJpaRepository userRepository;
	private final SocialAccountJpaRepository socialAccountRepository;
	private final RecordJpaRepository recordRepository;
	private final RemindJpaRepository remindRepository;
	private final ExhibitionBookmarkJpaRepository bookmarkRepository;
	private final ExhibitionJpaRepository exhibitionRepository;
	private final ActivityLogJpaRepository activityLogRepository;

	@Transactional(readOnly = true)
	public Page<AdminUserResult.UserListItem> list(String q, Pageable pageable) {
		Page<User> users = userRepository.searchForAdmin(blankToNull(q), pageable);
		List<Long> ids = users.getContent().stream().map(User::getId).toList();

		Map<Long, Long> recordCounts = countMap(ids, recordRepository::countByUserIds);
		Map<Long, Long> remindCounts = countMap(ids, remindRepository::countByUserIds);
		Map<Long, Long> bookmarkCounts = countMap(ids, bookmarkRepository::countByUserIds);
		Map<Long, Long> apiCounts = countMap(ids, activityLogRepository::countByUserIds);
		Map<Long, ZonedDateTime> lastActivity = timeMap(ids, activityLogRepository::lastActivityByUserIds);
		Map<Long, String> phones = phoneMap(ids);

		return users.map(u -> new AdminUserResult.UserListItem(
				u.getId(), u.getNickname(), u.getName(), phones.get(u.getId()), u.getCreatedAt(),
				recordCounts.getOrDefault(u.getId(), 0L),
				remindCounts.getOrDefault(u.getId(), 0L),
				bookmarkCounts.getOrDefault(u.getId(), 0L),
				apiCounts.getOrDefault(u.getId(), 0L),
				lastActivity.get(u.getId())));
	}

	@Transactional(readOnly = true)
	public AdminUserResult.UserDetail detail(Long userId) {
		User user = userRepository.findByIdAndDeletedAtIsNull(userId)
				.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND));

		List<SocialAccount> accounts = socialAccountRepository.findByUserIdAndDeletedAtIsNull(userId);
		String email = accounts.stream().map(SocialAccount::getEmail).filter(Objects::nonNull).findFirst().orElse(null);
		String phoneNumber = accounts.stream()
				.filter(a -> PHONE_PROVIDER.equals(a.getProvider()))
				.map(SocialAccount::getProviderUserId).map(AdminUserFacade::formatPhone).findFirst().orElse(null);

		List<Record> records = recordRepository
				.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, PageRequest.of(0, RECENT_LIMIT))
				.getContent();
		List<Remind> reminds = remindRepository
				.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, PageRequest.of(0, RECENT_LIMIT))
				.getContent();
		List<AdminUserResult.ExhibitionItem> bookmarks = toBookmarkItems(
				bookmarkRepository.findActiveExhibitionIdsOrderByRegisteredDesc(userId));
		List<ActivityLog> activity = activityLogRepository
				.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, ACTIVITY_LIMIT)).getContent();
		List<AdminUserResult.ApiPathCount> byPath = activityLogRepository.countByPathForUser(userId).stream()
				.limit(TOP_PATHS)
				.map(r -> new AdminUserResult.ApiPathCount((String) r[0], ((Number) r[1]).longValue()))
				.toList();
		ZonedDateTime lastActivityAt = activity.isEmpty() ? null : activity.get(0).getCreatedAt();

		return new AdminUserResult.UserDetail(
				user.getId(), user.getNickname(), user.getName(), email, phoneNumber,
				enumName(user.getAgeGroup()), enumName(user.getResidenceRegion()),
				user.getBirthYear(), user.isRemindEnabled(), user.getCreatedAt(),
				recordRepository.countByUserIdAndDeletedAtIsNull(userId),
				remindRepository.countByUserIdAndDeletedAtIsNull(userId),
				bookmarkRepository.countByUserIdAndDeletedAtIsNull(userId),
				recordRepository.countDistinctExhibitionByUserId(userId),
				activityLogRepository.countByUserId(userId),
				lastActivityAt,
				records.stream().map(this::toRecordItem).toList(),
				reminds.stream().map(this::toRemindItem).toList(),
				bookmarks, byPath,
				activity.stream().map(this::toActivityItem).toList());
	}

	private List<AdminUserResult.ExhibitionItem> toBookmarkItems(List<Long> exhibitionIds) {
		if (exhibitionIds.isEmpty()) {
			return List.of();
		}
		List<Long> recent = exhibitionIds.stream().limit(RECENT_LIMIT).toList();
		Map<Long, Exhibition> byId = new HashMap<>();
		for (Exhibition e : exhibitionRepository.findByIdInAndDeletedAtIsNull(recent)) {
			byId.put(e.getId(), e);
		}
		return recent.stream()
				.map(byId::get)
				.filter(Objects::nonNull)
				.map(e -> new AdminUserResult.ExhibitionItem(e.getId(), e.getTitle(), enumName(e.getType())))
				.toList();
	}

	private AdminUserResult.RecordItem toRecordItem(Record r) {
		return new AdminUserResult.RecordItem(r.getId(), r.getExhibitionId(), r.getExhibitionTitle(),
				r.getRepresentativeEmotion(), enumName(r.getAiStatus()), enumName(r.getWriteMode()),
				r.getViewedAt(), r.getCreatedAt());
	}

	private AdminUserResult.RemindItem toRemindItem(Remind rm) {
		return new AdminUserResult.RemindItem(rm.getId(), rm.getRecordId(), rm.getReflection(),
				enumName(rm.getAiStatus()), rm.getCreatedAt());
	}

	private AdminUserResult.ActivityItem toActivityItem(ActivityLog a) {
		return new AdminUserResult.ActivityItem(a.getMethod(), a.getPath(), a.getStatus(), a.getDurationMs(),
				a.getCreatedAt());
	}

	private Map<Long, Long> countMap(List<Long> ids, Function<List<Long>, List<Object[]>> query) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, Long> map = new HashMap<>();
		for (Object[] row : query.apply(ids)) {
			map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
		}
		return map;
	}

	private Map<Long, ZonedDateTime> timeMap(List<Long> ids, Function<List<Long>, List<Object[]>> query) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, ZonedDateTime> map = new HashMap<>();
		for (Object[] row : query.apply(ids)) {
			map.put(((Number) row[0]).longValue(), toZonedDateTime(row[1]));
		}
		return map;
	}

	// max(createdAt)의 반환 타입이 드라이버/하이버네이트 버전에 따라 다를 수 있어 방어적으로 변환.
	private static ZonedDateTime toZonedDateTime(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof ZonedDateTime z) {
			return z;
		}
		if (value instanceof java.time.OffsetDateTime o) {
			return o.toZonedDateTime();
		}
		if (value instanceof java.time.Instant i) {
			return i.atZone(java.time.ZoneOffset.UTC);
		}
		if (value instanceof java.sql.Timestamp t) {
			return t.toInstant().atZone(java.time.ZoneOffset.UTC);
		}
		if (value instanceof java.time.LocalDateTime l) {
			return l.atZone(java.time.ZoneOffset.UTC);
		}
		return null;
	}

	private Map<Long, String> phoneMap(List<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, String> map = new HashMap<>();
		for (SocialAccount account : socialAccountRepository
				.findByProviderAndUserIdInAndDeletedAtIsNull(PHONE_PROVIDER, ids)) {
			map.putIfAbsent(account.getUserId(), formatPhone(account.getProviderUserId()));
		}
		return map;
	}

	/** 정규화된 숫자(01012345678)를 010-1234-5678로 표기. 길이가 예상과 다르면 원본 그대로. */
	private static String formatPhone(String digits) {
		if (digits == null) {
			return null;
		}
		if (digits.length() == 11) {
			return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
		}
		if (digits.length() == 10) {
			return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
		}
		return digits;
	}

	private static String enumName(Enum<?> value) {
		return value == null ? null : value.name();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
