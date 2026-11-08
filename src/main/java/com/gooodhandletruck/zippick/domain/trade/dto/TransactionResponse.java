package com.gooodhandletruck.zippick.domain.trade.dto;

import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 실거래 한 건 응답. 건물 상세(7번)의 recentTransactions 와 실거래 내역(8번)에서 같이 써요. (#6 에서 추가)
 * #33 변경: Swagger 에 칸마다 설명·예시가 보이도록 @Schema 추가. 예시 값은 실제 건물 21 의 최근 거래.
 */
@Schema(description = "실거래 한 건")
public record TransactionResponse(
        @Schema(description = "계약일", example = "2026-09-30")
        LocalDate dealDate,

        @Schema(description = "전용면적 ㎡ (소수 2자리)", example = "36.49")
        BigDecimal areaM2,

        @Schema(description = "전용면적 평 (소수 1자리, 1평 = 3.3058㎡)", example = "11.0")
        BigDecimal areaPyeong,

        @Schema(description = "층", example = "15")
        Integer floor,

        @Schema(description = "동 번호 (국토부 자료 그대로, \"동\" 글자 없음). 자료에 없으면 null", example = "101", nullable = true)
        String dong,

        @Schema(description = "거래금액, 만원 단위 (59700 = 5억 9,700만 원)", example = "59700")
        Integer price) {

    /** 1평 = 3.3058㎡ (명세 예시 84.97㎡ → 25.7평, 59.98㎡ → 18.1평과 같은 기준) */
    private static final BigDecimal SQUARE_METERS_PER_PYEONG = new BigDecimal("3.3058");

    public static TransactionResponse from(Trade trade) {
        return new TransactionResponse(
                trade.getDealDate(),
                trade.getExcluUseAr().setScale(2, RoundingMode.HALF_UP),
                toPyeong(trade.getExcluUseAr()),
                trade.getFloor(),
                trade.getAptDong(),
                trade.getDealAmount());
    }

    static BigDecimal toPyeong(BigDecimal areaM2) {
        // 바로 소수 1자리로 나누면 중간 반올림이 생겨서, 넉넉히 나눈 뒤 마지막에 한 번만 반올림해요
        return areaM2.divide(SQUARE_METERS_PER_PYEONG, 10, RoundingMode.HALF_UP).setScale(1, RoundingMode.HALF_UP);
    }
}
