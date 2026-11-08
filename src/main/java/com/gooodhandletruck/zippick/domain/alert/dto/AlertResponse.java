package com.gooodhandletruck.zippick.domain.alert.dto;

import com.gooodhandletruck.zippick.domain.alert.entity.Alert;
import com.gooodhandletruck.zippick.global.util.KstClock;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 알림 한 건 (API 18번). 필드 이름은 명세와 같아요 */
@Schema(description = "관심 건물 알림")
public record AlertResponse(
        @Schema(description = "알림 ID", example = "7")
        Long alertId,

        @Schema(description = "건물 ID", example = "101")
        Long buildingId,

        @Schema(description = "단지명", example = "상암월드컵파크7단지")
        String buildingName,

        @Schema(description = "새 거래")
        Deal deal,

        @Schema(description = "읽었는지. 읽은 알림은 배지 개수에서 빠져요", example = "false")
        boolean read,

        @Schema(description = "알림이 만들어진 시각", example = "2026-11-03T06:00:00+09:00")
        OffsetDateTime createdAt
) {

    /** 1평 = 3.3058㎡ (TransactionResponse 와 같은 기준) */
    private static final BigDecimal SQUARE_METERS_PER_PYEONG = new BigDecimal("3.3058");

    @Schema(description = "알림을 만든 거래")
    public record Deal(
            @Schema(description = "거래금액, 만원", example = "125000")
            Integer price,

            @Schema(description = "계약일", example = "2026-10-12")
            LocalDate dealDate,

            @Schema(description = "전용면적 ㎡ (소수 2자리)", example = "84.97")
            BigDecimal areaM2,

            @Schema(description = "전용면적 평 (소수 1자리)", example = "25.7")
            BigDecimal areaPyeong
    ) {
    }

    public static AlertResponse from(Alert alert) {
        BigDecimal area = alert.getExcluUseAr();
        Deal deal = new Deal(
                alert.getDealAmount(),
                alert.getDealDate(),
                area.setScale(2, RoundingMode.HALF_UP),
                area.divide(SQUARE_METERS_PER_PYEONG, 10, RoundingMode.HALF_UP).setScale(1, RoundingMode.HALF_UP));
        OffsetDateTime createdAt = alert.getCreatedAt() == null
                ? null
                : alert.getCreatedAt().atZone(KstClock.KST).toOffsetDateTime();
        return new AlertResponse(alert.getId(), alert.getBuilding().getId(), alert.getBuilding().getAptNm(),
                deal, alert.isRead(), createdAt);
    }
}
