package modi.backend.domain.record;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import modi.backend.support.entity.BaseEntity;

@Entity
@Table(name = "records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Record extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "exhibition_id", nullable = false)
	private Long exhibitionId;

	@Column(name = "exhibition_title", nullable = false, length = 100)
	private String exhibitionTitle;

	@Column(name = "exhibition_type", nullable = false, length = 20)
	private String exhibitionType;

	@Column(name = "exhibition_poster_url", length = 2048)
	private String exhibitionPosterUrl;

	@Column(name = "exhibition_place", length = 200)
	private String exhibitionPlace;

	@Column(name = "exhibition_region", length = 20)
	private String exhibitionRegion;

	@Column(name = "exhibition_category", length = 20)
	private String exhibitionCategory;

	@Column(name = "exhibition_start_date")
	private LocalDate exhibitionStartDate;

	@Column(name = "exhibition_end_date")
	private LocalDate exhibitionEndDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "write_mode", nullable = false, length = 20)
	private WriteMode writeMode;

	@Column(name = "viewed_at", nullable = false)
	private LocalDate viewedAt;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "ai_summary", columnDefinition = "text")
	private String aiSummary;

	@Column(name = "representative_emotion", length = 50)
	private String representativeEmotion;

	@Column(name = "card_phrase", length = 255)
	private String cardPhrase;

	@Enumerated(EnumType.STRING)
	@Column(name = "ai_status", nullable = false, length = 20)
	private AiStatus aiStatus;

	@Column(nullable = false)
	private boolean bookmarked;

	@OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
	private final List<RecordKeyword> keywords = new ArrayList<>();

	// 아카이브 목록은 한 페이지(기본 20건)의 감정 태그를 카드마다 읽는다 → 지연 로딩 N+1 방지로 IN 배치 조회.
	@BatchSize(size = 100)
	@OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
	private final List<RecordEmotion> emotions = new ArrayList<>();

	@OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
	private final List<RecordMedia> media = new ArrayList<>();

	private Record(Long userId, Long exhibitionId, ExhibitionSnapshot snapshot, WriteMode writeMode,
			LocalDate viewedAt, String content, String aiSummary, String representativeEmotion, String cardPhrase,
			AiStatus aiStatus) {
		this.userId = userId;
		this.exhibitionId = exhibitionId;
		this.exhibitionTitle = snapshot.title();
		this.exhibitionType = snapshot.type();
		this.exhibitionPosterUrl = snapshot.posterUrl();
		this.exhibitionPlace = snapshot.place();
		this.exhibitionRegion = snapshot.region();
		this.exhibitionCategory = snapshot.category();
		this.exhibitionStartDate = snapshot.startDate();
		this.exhibitionEndDate = snapshot.endDate();
		this.writeMode = writeMode;
		this.viewedAt = viewedAt;
		this.content = content;
		this.aiSummary = aiSummary;
		this.representativeEmotion = representativeEmotion;
		this.cardPhrase = cardPhrase;
		this.aiStatus = aiStatus;
	}

	public static Record create(Long userId, Long exhibitionId, ExhibitionSnapshot snapshot, WriteMode writeMode,
			LocalDate viewedAt, String content, String aiSummary, String representativeEmotion, String cardPhrase,
			AiStatus aiStatus) {
		return new Record(userId, exhibitionId, snapshot, writeMode, viewedAt, content, aiSummary,
				representativeEmotion, cardPhrase, aiStatus);
	}

	public void replaceContent(LocalDate viewedAt, String content, String aiSummary, String representativeEmotion,
			String cardPhrase, AiStatus aiStatus) {
		this.viewedAt = viewedAt;
		this.content = content;
		this.aiSummary = aiSummary;
		this.representativeEmotion = representativeEmotion;
		this.cardPhrase = cardPhrase;
		this.aiStatus = aiStatus;
	}

	public void replaceKeywords(List<RecordKeyword> newKeywords) {
		keywords.clear();
		newKeywords.forEach(this::addKeyword);
	}

	public void replaceEmotions(List<RecordEmotion> newEmotions) {
		emotions.clear();
		newEmotions.forEach(this::addEmotion);
	}

	public void replaceMedia(List<RecordMedia> newMedia) {
		media.clear();
		newMedia.forEach(this::addMedia);
	}

	public void bookmark() {
		this.bookmarked = true;
	}

	public void unbookmark() {
		this.bookmarked = false;
	}

	private void addKeyword(RecordKeyword keyword) {
		keyword.attach(this);
		keywords.add(keyword);
	}

	private void addEmotion(RecordEmotion emotion) {
		emotion.attach(this);
		emotions.add(emotion);
	}

	private void addMedia(RecordMedia mediaItem) {
		mediaItem.attach(this);
		media.add(mediaItem);
	}
}
