package com.gooodhandletruck.zippick.domain.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "건물 저장 요청")
public record FavoriteCreateRequest(
        @Schema(description = "건물 ID (buildings.id)", example = "1")
        @NotNull
        @Positive
        Long buildingId
) {
}
