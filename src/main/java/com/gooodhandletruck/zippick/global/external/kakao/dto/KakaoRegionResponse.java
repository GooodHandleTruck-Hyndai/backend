package com.gooodhandletruck.zippick.global.external.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 카카오 coord2regioncode 응답.
 * 실시간 응답용으로만 쓰고 DB·캐시·로그에 남기지 않음 (카카오 정책)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoRegionResponse(List<Document> documents) {

    /** region_type: B = 법정동, H = 행정동 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
        @JsonProperty("region_type") String regionType,
        @JsonProperty("address_name") String addressName,
        @JsonProperty("region_1depth_name") String region1DepthName,
        @JsonProperty("region_2depth_name") String region2DepthName,
        @JsonProperty("region_3depth_name") String region3DepthName,
        @JsonProperty("code") String code
    ) {
    }
}
