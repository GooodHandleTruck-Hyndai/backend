package com.gooodhandletruck.zippick.domain.interest.dto;

import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 관심 조건 저장 요청 (API 16번). 전체 값을 보내면 그대로 바꿔요 */
@Schema(description = "관심 조건 저장 요청")
public record InterestConditionRequest(
        @Schema(description = "관심 지역 시군구 코드 5자리 (1~10개)", example = "[\"11440\"]")
        @NotNull
        @Size(min = 1, max = 10, message = "regionCodes 는 1~10개여야 해요")
        List<@NotNull @Pattern(regexp = "\\d{5}", message = "regionCodes 는 숫자 5자리여야 해요") String> regionCodes,

        @Schema(description = "최소 가격 (만원). 없으면 제한 없음", example = "80000", nullable = true)
        @PositiveOrZero
        Integer priceMin,

        @Schema(description = "최대 가격 (만원). 없으면 제한 없음", example = "150000", nullable = true)
        @PositiveOrZero
        Integer priceMax,

        @Schema(description = "건물 유형 (1개 이상)", example = "[\"APT\"]")
        @NotNull
        @Size(min = 1, message = "types 는 1개 이상이어야 해요")
        List<@NotNull BuildingType> types,

        @Schema(description = "최소 전용면적 (㎡). 없으면 제한 없음", example = "59", nullable = true)
        @PositiveOrZero
        Integer areaMinM2,

        @Schema(description = "최대 전용면적 (㎡). 없으면 제한 없음", example = "85", nullable = true)
        @PositiveOrZero
        Integer areaMaxM2,

        @Schema(description = "알림 켜짐 여부", example = "true")
        @NotNull
        Boolean alertEnabled
) {
}
