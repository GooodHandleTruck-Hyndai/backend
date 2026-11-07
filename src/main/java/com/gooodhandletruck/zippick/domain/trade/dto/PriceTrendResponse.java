package com.gooodhandletruck.zippick.domain.trade.dto;

import java.util.List;

/**
 * 가격 추이 (API 13번) 응답. 오래된 달부터 항상 12개예요. (#26 에서 추가)
 *
 * @param months 달별 평균가
 */
public record PriceTrendResponse(List<MonthlyPrice> months) {

    /**
     * @param yearMonth 계약월 "2025-12"
     * @param avgPrice  해제 거래를 뺀 평균 거래가(만원, 반올림). 거래가 없는 달은 null → 앱이 선을 끊어요
     * @param count     그 달 거래 수
     */
    public record MonthlyPrice(String yearMonth, Integer avgPrice, int count) {
    }
}
