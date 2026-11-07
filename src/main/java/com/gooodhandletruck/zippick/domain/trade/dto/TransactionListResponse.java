package com.gooodhandletruck.zippick.domain.trade.dto;

import java.util.List;

/**
 * 실거래 내역(API 8번, 더보기) 응답. 쪽 단위로 내려줘요. (#16 에서 추가)
 *
 * @param transactions 이번 쪽 거래 (최신순, 해제 거래 제외). 항목 형식은 건물 상세 recentTransactions 와 같아요
 * @param page         요청한 쪽 (0부터)
 * @param size         요청한 쪽 크기
 * @param hasNext      다음 쪽이 있는지
 */
public record TransactionListResponse(
        List<TransactionResponse> transactions,
        int page,
        int size,
        boolean hasNext) {
}
