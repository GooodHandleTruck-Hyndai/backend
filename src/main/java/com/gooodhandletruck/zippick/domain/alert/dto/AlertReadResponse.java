package com.gooodhandletruck.zippick.domain.alert.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** 알림 읽음 (API 19번) */
@Schema(description = "알림 읽음 결과")
public record AlertReadResponse(
        @Schema(description = "알림 ID", example = "42")
        Long alertId,

        @Schema(description = "읽음 여부 (항상 true)", example = "true")
        boolean read,

        @Schema(description = "처음 읽은 시각", example = "2026-11-03T10:12:00+09:00")
        OffsetDateTime readAt
) {
}
