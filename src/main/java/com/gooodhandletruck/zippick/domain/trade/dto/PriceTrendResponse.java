package com.gooodhandletruck.zippick.domain.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 가격 추이 (API 13번) 응답. 오래된 달부터 항상 12개예요. (#26 에서 추가)
 * #33 변경: Swagger 에 칸마다 설명·예시가 보이도록 @Schema 추가. 예시 값은 실제 건물 21 의 2025-12.
 */
@Schema(description = "가격 추이 (최근 12개월)")
public record PriceTrendResponse(
        @Schema(description = "달별 평균가. 이번 달 포함 최근 12개월, 오래된 달부터 항상 12개")
        List<MonthlyPrice> months) {

    @Schema(description = "한 달 평균가")
    public record MonthlyPrice(
            @Schema(description = "계약월 (yyyy-MM)", example = "2025-12")
            String yearMonth,

            @Schema(description = "그 달 평균 거래가, 만원 단위(반올림). 해제 거래 제외. 거래가 없는 달은 null → 그래프 선을 끊어요",
                    example = "83700", nullable = true)
            Integer avgPrice,

            @Schema(description = "그 달 거래 수 (해제 거래 제외)", example = "10")
            int count) {
    }
}
