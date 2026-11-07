package com.gooodhandletruck.zippick.domain.building.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record BuildingCoordinateResponse(
    @Schema(description = "건물 ID", example = "11680-역삼동-736-1-래미안")
    String buildingId,

    @Schema(description = "좌표를 찾았는지", example = "true")
    boolean found,

    @Schema(description = "위도", example = "37.5009", nullable = true)
    Double lat,

    @Schema(description = "경도", example = "127.0364", nullable = true)
    Double lng
) {
    public static  BuildingCoordinateResponse found(String buildingId, double lat, double lng){
        return new BuildingCoordinateResponse(buildingId, true, lat, lng);
    }

    public static  BuildingCoordinateResponse notFound(String buildingId){
        return new BuildingCoordinateResponse(buildingId, false, null, null);
    }

}
