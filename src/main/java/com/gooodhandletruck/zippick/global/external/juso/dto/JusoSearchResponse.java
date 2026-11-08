package com.gooodhandletruck.zippick.global.external.juso.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 도로명주소 검색 API 응답 (resultType=json) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JusoSearchResponse(Results results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Results(JusoCommon common, List<Juso> juso) {
    }

    /**
     * 주소 한 건. 좌표제공 API 에 admCd ~ buldSlno 를 그대로 넘겨요.
     *
     * @param admCd    법정동코드 10자리
     * @param rnMgtSn  도로명코드
     * @param udrtYn   지하 여부 (0 지상, 1 지하)
     * @param buldMnnm 건물 본번
     * @param buldSlno 건물 부번
     * @param mtYn     산 여부 (0 대지, 1 산)
     * @param lnbrMnnm 지번 본번
     * @param lnbrSlno 지번 부번 (없으면 0)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Juso(
            String roadAddr,
            String jibunAddr,
            String bdNm,
            String admCd,
            String rnMgtSn,
            String udrtYn,
            String buldMnnm,
            String buldSlno,
            String mtYn,
            String lnbrMnnm,
            String lnbrSlno
    ) {
    }
}
