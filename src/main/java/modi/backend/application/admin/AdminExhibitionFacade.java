package modi.backend.application.admin;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.support.text.HtmlTextExtractor;

/**
 * 관리자 전시 유지보수(내부 운영용). 프론트 비노출 — 수집 파이프라인과 별개로 기존 데이터를 손보는 일회성/운영성 작업을 담는다.
 */
@Service
@RequiredArgsConstructor
public class AdminExhibitionFacade {

	private static final Logger log = LoggerFactory.getLogger(AdminExhibitionFacade.class);

	private final ExhibitionRepository exhibitionRepository;

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
}
