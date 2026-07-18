package modi.backend.infra.exhibition.sync.mock;

import modi.backend.infra.exhibition.sync.gemini.GeminiGenreClassifier;

import org.springframework.stereotype.Component;

import modi.backend.domain.exhibition.sync.data.GenreClassification;
import modi.backend.domain.exhibition.sync.port.GenreClassifier;
import modi.backend.domain.exhibition.genre.GenreKeyword;
import modi.backend.domain.exhibition.sync.data.GenreResult;

/**
 * 마스터에서 무작위로 1개를 뽑는 "가짜" 장르 분류기(기존 임시 전략). 입력을 실제로 분석하지 않는다.
 * AI 분류기({@link GeminiGenreClassifier})와 함께 빈으로 공존하며, {@code app.exhibition.genre.classifier=random}일 때
 * 주 분류기로 선택된다. AI 분류기의 미설정·429·오류 폴백 대상이기도 하다.
 * <p>
 * 산출물엔 항상 {@code provider=RANDOM}이 붙는다 — 이 표식이 있어야 나중에 "실제로 분류된 적 없는 행"만 골라 재분류할 수 있다.
 */
@Component
public class RandomGenreClassifier implements GenreClassifier {

	@Override
	public GenreResult classify(GenreClassification input) {
		return GenreResult.random(GenreKeyword.random());
	}
}
