package com.gooodhandletruck.zippick.domain.interest.dto;

import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import com.gooodhandletruck.zippick.domain.interest.entity.InterestCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 관심 조건 (API 15·16번 응답). 필드 이름은 명세와 같아요 */
@Schema(description = "관심 조건")
public record InterestConditionResponse(
        @Schema(description = "관심 지역 시군구 코드", example = "[\"11440\"]")
        List<String> regionCodes,

        @Schema(description = "최소 가격 (만원)", example = "80000", nullable = true)
        Integer priceMin,

        @Schema(description = "최대 가격 (만원)", example = "150000", nullable = true)
        Integer priceMax,

        @Schema(description = "건물 유형", example = "[\"APT\"]")
        List<BuildingType> types,

        @Schema(description = "최소 전용면적 (㎡)", example = "59", nullable = true)
        Integer areaMinM2,

        @Schema(description = "최대 전용면적 (㎡)", example = "85", nullable = true)
        Integer areaMaxM2,

        @Schema(description = "알림 켜짐 여부", example = "true")
        boolean alertEnabled
) {

    public static InterestConditionResponse from(InterestCondition condition) {
        return new InterestConditionResponse(
                condition.getRegionCodes(),
                condition.getPriceMin(),
                condition.getPriceMax(),
                condition.getTypes(),
                condition.getAreaMinM2(),
                condition.getAreaMaxM2(),
                condition.isAlertEnabled());
    }
}
