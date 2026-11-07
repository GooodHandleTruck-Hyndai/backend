package com.gooodhandletruck.zippick.domain.trade.dto;

import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 실거래 한 건 응답. 건물 상세(7번)의 recentTransactions 와 실거래 내역(8번)에서 같이 써요. (#6 에서 추가)
 *
 * @param dealDate   계약일
 * @param areaM2     전용면적 ㎡ (소수 2자리)
 * @param areaPyeong 평 (소수 1자리)
 * @param floor      층
 * @param dong       동. 등기 전 거래는 null
 * @param price      거래금액, 만원
 */
public record TransactionResponse(
        LocalDate dealDate,
        BigDecimal areaM2,
        BigDecimal areaPyeong,
        Integer floor,
        String dong,
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
