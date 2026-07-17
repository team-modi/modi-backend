package modi.backend.application.admin;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.domain.exhibition.ExhibitionHistory;
import modi.backend.domain.exhibition.ExhibitionHistoryRepository;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.domain.exhibition.FieldChange;
import modi.backend.support.error.CoreException;
import modi.backend.support.text.HtmlTextExtractor;

/**
 * 관리자 전시 유지보수(내부 운영용). 프론트 비노출 — 수집 파이프라인과 별개로 기존 데이터를 손보는 일회성/운영성 작업을 담는다.
 */
@Service
@RequiredArgsConstructor
public class AdminExhibitionFacade {

	private static final Logger log = LoggerFactory.getLogger(AdminExhibitionFacade.class);

	private final ExhibitionRepository exhibitionRepository;
	/** 사람 수정 이력(감사) — 실제로 바뀐 필드를 old→new로 남긴다. */
	private final ExhibitionHistoryRepository exhibitionHistoryRepository;

	/**
	 * 저장된 CATALOG 전시 설명을 재파싱해 남아 있는 HTML/워드프레스 마크업을 벗긴다(최초 수집 파싱과 동일 규칙 {@link HtmlTextExtractor}).
	 * 외부 재조회 없이 저장값만 정리하며, 실질 변경이 있는 행만 저장한다(멱등 — 이미 깨끗한 행은 건너뜀).
	 */
	@Transactional
	public AdminExhibitionResult.DescriptionReparse reparseDescriptions() {
		List<Exhibition> rows = exhibitionRepository.findCatalogWithDescription();
		int updated = 0;
		for (Exhibition exhibition : rows) {
			String cleaned = HtmlTextExtractor.toPlainText(exhibition.getDescription());
			if (!Objects.equals(cleaned, exhibition.getDescription())) {
				exhibition.reparseDescription(cleaned);
				exhibitionRepository.save(exhibition);
				updated++;
			}
		}
		log.info("전시 설명 재파싱: 검사 {}건 / 갱신 {}건", rows.size(), updated);
		return new AdminExhibitionResult.DescriptionReparse(rows.size(), updated);
	}

	/**
	 * 전시 필드를 수정하고, <b>실제로 바뀐 필드마다 이력을 남긴다</b>(감사). 상태 변경은 {@link Exhibition#applyAdminEdit}이
	 * 판단·수행하고(무엇이 바뀌었나 = 도메인 규칙), 이 Facade는 그 변경 목록을 {@code exhibition_history}에 적재하는
	 * 조율만 한다(CLAUDE.md: Facade는 load·조율·save).
	 * <p>
	 * 한 번의 수정으로 여러 필드가 바뀌면 <b>같은 {@code editedAt}</b>으로 묶어 "한 액션이었다"를 이력에 보존한다.
	 * 값이 그대로면 변경 목록이 비어 이력이 생기지 않는다(멱등).
	 */
	@Transactional
	public AdminExhibitionResult.Edited editExhibition(Long exhibitionId, String title, String place, String price,
			String description) {
		Exhibition exhibition = exhibitionRepository.findById(exhibitionId)
				.orElseThrow(() -> new CoreException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND));
		List<FieldChange> changes = exhibition.applyAdminEdit(title, place, price, description);
		if (changes.isEmpty()) {
			return new AdminExhibitionResult.Edited(exhibitionId, 0); // 변경 없음 — 저장·이력 없음
		}
		exhibitionRepository.save(exhibition);
		LocalDateTime editedAt = LocalDateTime.now(); // 이 수정의 모든 필드 변경이 공유하는 이벤트 묶음키
		for (FieldChange change : changes) {
			exhibitionHistoryRepository.save(ExhibitionHistory.of(exhibitionId, change, editedAt));
		}
		log.info("전시 수정: id={} 변경필드 {}개", exhibitionId, changes.size());
		return new AdminExhibitionResult.Edited(exhibitionId, changes.size());
	}
}
