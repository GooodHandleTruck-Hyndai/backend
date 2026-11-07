package com.gooodhandletruck.zippick.domain.building.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 좌표를 찾을 건물. 실거래가 API 응답 필드를 그대로 넣으면 됨 */
@Schema(description = "좌표를 찾을 건물 (실거래가 API 필드)")
public record BuildingAddress(
    @Schema(description = "건물 ID (구·동·지번·단지명 조합). 응답에 그대로 돌려줌", example = "11680-역삼동-736-1-래미안")
    @NotBlank
    String buildingId,

    @Schema(description = "시군구 코드 5자리", example = "11680")
    @NotBlank
    @Pattern(regexp = "\\d{5}", message = "sggCd 는 숫자 5자리여야 해요")
    String sggCd,

    @Schema(description = "도로명", example = "테헤란로", nullable = true)
    String roadNm,

    @Schema(description = "도로명 건물 본번 (0 채움 가능)", example = "00427", nullable = true)
    String roadNmBonbun,

    @Schema(description = "도로명 건물 부번 (0 채움 가능)", example = "00000", nullable = true)
    String roadNmBubun,

    @Schema(description = "법정동 이름", example = "역삼동", nullable = true)
    String umdNm,

    @Schema(description = "지번", example = "736-1", nullable = true)
    String jibun
) {
}
