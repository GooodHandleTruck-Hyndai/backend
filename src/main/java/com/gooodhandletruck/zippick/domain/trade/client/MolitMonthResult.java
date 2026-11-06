package com.gooodhandletruck.zippick.domain.trade.client;

import java.util.List;

/**
 * (구, 계약월) 한 달 치 응답을 모든 페이지에서 모은 결과. (#7 에서 추가)
 *
 * 받은 건수(items.size()) 와 totalCount 가 같은지는 적재 서비스(#8)가 확인해요 (안전 확인).
 *
 * @param sggCd      요청한 구 코드
 * @param dealYm     요청한 계약월 (YYYYMM)
 * @param totalCount 국토부가 알려 준 전체 건수
 * @param items      받은 거래 (응답 순서 그대로)
 */
public record MolitMonthResult(String sggCd, String dealYm, int totalCount, List<MolitTradeItem> items) {

    /** 국토부가 알려 준 건수만큼 다 받았는지 */
    public boolean isComplete() {
        return items.size() == totalCount;
    }
}
