package com.gooodhandletruck.zippick.domain.favorite.dto;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.favorite.entity.Favorite;
import com.gooodhandletruck.zippick.global.util.KstClock;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** 필드 이름은 건물 상세(BuildingDetailResponse)와 맞춤 */
@Schema(description = "저장한 건물")
public record FavoriteResponse(
        @Schema(description = "건물 ID", example = "1")
        Long buildingId,

        @Schema(description = "단지명", example = "마포래미안푸르지오1단지")
        String name,

        @Schema(description = "건물 유형", example = "APT")
        String type,

        @Schema(description = "지번 주소", example = "서울 마포구 아현동 777")
        String jibunAddress,

        @Schema(description = "위도. 좌표를 아직 못 찾았으면 null", example = "37.5520", nullable = true)
        Double lat,

        @Schema(description = "경도. 좌표를 아직 못 찾았으면 null", example = "126.9560", nullable = true)
        Double lon,

        @Schema(description = "저장한 시각", example = "2026-11-07T22:30:00+09:00")
        OffsetDateTime savedAt
) {

    public static FavoriteResponse of(Favorite favorite, String jibunAddress) {
        Building building = favorite.getBuilding();
        OffsetDateTime savedAt = favorite.getCreatedAt() == null
                ? null
                : favorite.getCreatedAt().atZone(KstClock.KST).toOffsetDateTime();
        return new FavoriteResponse(
                building.getId(),
                building.getAptNm(),
                building.getBuildingType().name(),
                jibunAddress,
                building.getLatitude(),
                building.getLongitude(),
                savedAt);
    }
}
