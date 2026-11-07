package com.gooodhandletruck.zippick.domain.building.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record BuildingCoordinateResponse(
    @Schema(description = "단지 일련번호", example = "11680-3482")
    String aptSeq,

    @Schema(description = "좌표를 찾았는지", example = "true")
    boolean found,

    @Schema(description = "위도", example = "37.5009", nullable = true)
    Double lat,

    @Schema(description = "경도", example = "127.0364", nullable = true)
    Double lng
) {
    public static  BuildingCoordinateResponse found(String aptSeq, double lat, double lng){
        return new BuildingCoordinateResponse(aptSeq, true, lat, lng);
    }

    public static  BuildingCoordinateResponse notFound(String aptSeq){
        return new BuildingCoordinateResponse(aptSeq, false, null, null);
    }

}
