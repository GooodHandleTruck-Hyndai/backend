package com.gooodhandletruck.zippick.global.external.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 카카오 주소 검색 응답.
 * 실시간 응답용으로만 쓰고 DB·캐시·로그에 남기지 않음 (카카오 정책)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoAddressResponse(List<Document> documents) {

    /** x = 경도, y = 위도 (문자열로 옴) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
        @JsonProperty("address_name") String addressName,
        @JsonProperty("x") String x,
        @JsonProperty("y") String y,
        @JsonProperty("address") JibunAddress address
    ) {

        /** 지번 주소 쪽에만 법정동 코드(b_code)가 있음 */
        public String legalDongCode() {
            return address == null ? null : address.legalDongCode();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JibunAddress(
        @JsonProperty("b_code") String legalDongCode
    ) {
    }
}
