package com.gooodhandletruck.zippick.domain.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 실거래 내역(API 8번, 더보기) 응답. 쪽 단위로 내려줘요. (#16 에서 추가)
 * #33 변경: Swagger 에 칸마다 설명·예시가 보이도록 @Schema 추가.
 */
@Schema(description = "실거래 내역 (더보기)")
public record TransactionListResponse(
        @Schema(description = "이번 쪽 거래, 최신순. 해제(취소)된 거래는 빠져요. 항목 형식은 건물 상세의 recentTransactions 와 같아요")
        List<TransactionResponse> transactions,

        @Schema(description = "요청한 쪽 번호 (0부터)", example = "0")
        int page,

        @Schema(description = "요청한 쪽 크기", example = "20")
        int size,

        @Schema(description = "다음 쪽이 있는지. true 면 page + 1 로 다시 불러요", example = "true")
        boolean hasNext) {
}
