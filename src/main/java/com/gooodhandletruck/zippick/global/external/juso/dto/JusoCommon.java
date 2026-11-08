package com.gooodhandletruck.zippick.global.external.juso.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 행안부 API 공통 결과. errorCode "0" 이 정상 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JusoCommon(String errorCode, String errorMessage, String totalCount) {
}
