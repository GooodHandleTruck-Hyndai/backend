package com.gooodhandletruck.zippick.domain.trade.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gooodhandletruck.zippick.domain.trade.entity.CollectRun;
import com.gooodhandletruck.zippick.domain.trade.service.TradeIngestService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;

/**
 * 여러 구 × 최근 N개월 수집 (API 5·12번 공통). 적재 서비스는 가짜로 바꿔서 "어떤 달을 어떤 순서로 부르는지"만 봐요. (#17 에서 추가)
 */
class TradeCollectBatchTest {

    private TradeIngestService ingestService;
    private TradeCollectBatch batch;

    @BeforeEach
    void setUp() {
        ingestService = mock(TradeIngestService.class);
        when(ingestService.collectMonth(anyString(), anyString()))
                .thenAnswer(inv -> CollectRun.start(inv.getArgument(0), inv.getArgument(1)));
        batch = new TradeCollectBatch(ingestService);
    }

    // ---- 기간 계산 ----

    @ParameterizedTest(name = "{0} 기준 {1}개월 → {2}")
    @CsvSource(delimiter = '|', value = {
            "2026-11-07 | 3  | 202609,202610,202611",
            "2026-11-30 | 1  | 202611",
            "2026-01-15 | 3  | 202511,202512,202601",   // 해 넘김
            "2026-03-31 | 2  | 202602,202603",          // 말일
            "2026-11-07 | 12 | 202512,202601,202602,202603,202604,202605,202606,202607,202608,202609,202610,202611"
    })
    void 이번_달_포함_최근_N개월을_오래된_달부터_만든다(LocalDate today, int months, String expected) {
        assertThat(TradeCollectBatch.recentMonths(today, months)).containsExactly(expected.split(","));
    }

    // ---- 실행 ----

    @Test
    void 구마다_오래된_달부터_한_달씩_적재한다() {
        List<CollectRun> runs = batch.collectRecent(List.of("11440", "11410"), 2, LocalDate.of(2026, 11, 7));

        assertThat(runs).hasSize(4);
        InOrder order = inOrder(ingestService);
        order.verify(ingestService).collectMonth("11440", "202610");
        order.verify(ingestService).collectMonth("11440", "202611");
        order.verify(ingestService).collectMonth("11410", "202610");
        order.verify(ingestService).collectMonth("11410", "202611");
    }

    @Test
    void 한_달에서_예외가_나도_나머지_달은_계속한다() {
        // 이력 저장까지 실패하는 경우(DB 끊김 등) — collectMonth 가 예외를 던져도 배치는 멈추지 않아요
        when(ingestService.collectMonth("11440", "202610")).thenThrow(new IllegalStateException("DB 끊김"));

        List<CollectRun> runs = batch.collectRecent(List.of("11440"), 3, LocalDate.of(2026, 11, 7));

        assertThat(runs).extracting(CollectRun::getDealYm).containsExactly("202609", "202611");
        verify(ingestService).collectMonth("11440", "202611");
    }

    @Test
    void 구_목록이_비면_아무것도_부르지_않는다() {
        assertThat(batch.collectRecent(List.of(), 3, LocalDate.of(2026, 11, 7))).isEmpty();
        verify(ingestService, never()).collectMonth(anyString(), anyString());
    }
}
