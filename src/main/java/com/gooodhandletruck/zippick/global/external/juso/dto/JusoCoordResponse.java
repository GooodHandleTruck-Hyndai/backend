package com.gooodhandletruck.zippick.global.external.juso.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 좌표제공 API 응답. entX, entY 는 UTM-K(EPSG:5179) 미터 좌표 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JusoCoordResponse(Results results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Results(JusoCommon common, List<Coord> juso) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Coord(String entX, String entY) {
    }
}
