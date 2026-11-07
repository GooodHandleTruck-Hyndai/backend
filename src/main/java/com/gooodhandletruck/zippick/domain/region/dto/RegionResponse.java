package com.gooodhandletruck.zippick.domain.region.dto;

import com.gooodhandletruck.zippick.global.external.kakao.dto.KakaoRegionResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 위치의 지역 정보")
public record RegionResponse(
    @Schema(description = "시군구 코드 5자리. 실거래가 API 의 LAWD_CD", example = "11680")
    String lawdCd,

    @Schema(description = "법정동 코드 10자리", example = "1168010100")
    String legalDongCode,

    @Schema(description = "시도", example = "서울특별시")
    String sidoName,

    @Schema(description = "시군구. 세종시처럼 없으면 빈 문자열", example = "강남구")
    String sigunguName,

    @Schema(description = "법정동", example = "역삼동")
    String dongName,

    @Schema(description = "전체 주소", example = "서울특별시 강남구 역삼동")
    String addressName
) {

    public static RegionResponse from(KakaoRegionResponse.Document document) {
        String code = document.code();
        return new RegionResponse(
            code.substring(0, 5),
            code,
            document.region1DepthName(),
            document.region2DepthName(),
            document.region3DepthName(),
            document.addressName()
        );
    }
}
