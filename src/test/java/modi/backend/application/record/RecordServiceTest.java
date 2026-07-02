package modi.backend.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import modi.backend.application.exhibition.ExhibitionFacade;
import modi.backend.application.exhibition.ExhibitionResult;
import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.domain.record.Record;
import modi.backend.domain.record.WriteMode;
import modi.backend.infra.record.RecordJpaRepository;
import modi.backend.interfaces.record.dto.RecordCreateRequest;
import modi.backend.support.error.CoreException;

@ExtendWith(MockitoExtension.class)
class RecordServiceTest {

	@Mock
	RecordJpaRepository recordRepository;

	@Mock
	ExhibitionFacade exhibitionFacade;

	@InjectMocks
	RecordService service;

	@Test
	@DisplayName("create — 전시를 조회해 스냅샷을 박제한 뒤 저장한다")
	void create_전시조회해_스냅샷박제() {
		given(exhibitionFacade.getForSnapshot(any(), any())).willReturn(detailResult("모네전", "CATALOG", "SEOUL"));
		given(recordRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		service.create(1L, createReq(10L));

		ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
		verify(recordRepository).save(captor.capture());
		assertThat(captor.getValue().getExhibitionTitle()).isEqualTo("모네전");
		assertThat(captor.getValue().getExhibitionRegion()).isEqualTo("SEOUL");
	}

	@Test
	@DisplayName("create — 전시가 없으면 404를 전파하고 저장하지 않는다")
	void create_전시없으면_404전파() {
		given(exhibitionFacade.getForSnapshot(any(), any()))
				.willThrow(new CoreException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND));

		assertThatThrownBy(() -> service.create(1L, createReq(999L))).isInstanceOf(CoreException.class);

		verify(recordRepository, never()).save(any());
	}

	private RecordCreateRequest createReq(Long exhibitionId) {
		return new RecordCreateRequest(exhibitionId, WriteMode.DIRECT, LocalDate.now(), "감상", List.of("MOVED"), null,
				null, null, null, null, null);
	}

	private ExhibitionResult.Detail detailResult(String title, String type, String region) {
		return new ExhibitionResult.Detail(1L, type, title, "http://p", LocalDate.now(), null, "예술의전당", region,
				"PAINTING", null, null, null, List.of(), List.of(), null, null, null, null, null, null, null, 0L,
				null, null);
	}
}
