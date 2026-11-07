package com.gooodhandletruck.zippick.domain.device.dto;

import com.gooodhandletruck.zippick.domain.device.entity.Device;
import com.gooodhandletruck.zippick.global.util.KstClock;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "기기 정보")
public record DeviceResponse(
        @Schema(description = "익명 ID. 앱에 저장해 두고 X-Anonymous-Id 헤더로 보내요",
                example = "7f3c2a9e-1b4d-4c8e-9f21-0a6b5d4e3c21")
        String anonymousId,

        @Schema(description = "등록 시각", example = "2026-11-05T10:00:00+09:00")
        OffsetDateTime createdAt
) {

    public static DeviceResponse from(Device device) {
        OffsetDateTime createdAt = device.getCreatedAt() == null
                ? null
                : device.getCreatedAt().atZone(KstClock.KST).toOffsetDateTime();
        return new DeviceResponse(device.getAnonymousId(), createdAt);
    }
}
