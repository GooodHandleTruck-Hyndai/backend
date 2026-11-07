package com.gooodhandletruck.zippick.domain.building.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "건물 좌표 조회 요청")
public record BuildingCoordinateRequest(
    @Schema(description = "건물 목록 (최대 30개)")
    @NotEmpty
    @Size(max = 30, message = "한 번에 30개까지 조회할 수 있어요")
    List<@Valid BuildingAddress> buildings
) {
}
