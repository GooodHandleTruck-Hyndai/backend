package com.gooodhandletruck.zippick.domain.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "기기 등록 요청")
public record DeviceRegisterRequest(
        @Schema(description = "앱 버전", example = "0.1.0", nullable = true)
        @Size(max = 20)
        String appVersion
) {
}
