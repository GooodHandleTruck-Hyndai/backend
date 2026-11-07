package com.gooodhandletruck.zippick.domain.building.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 주변 건물 (API 2번) 응답 data. 필드 이름은 노션 API 명세 2번과 같아요. (#35 에서 추가)
 * 예시 값은 로컬 실데이터(마포 도화동 한화오벨리스크, 건물 21) 기준이에요.
 */
@Schema(description = "주변 건물 목록")
public record NearbyBuildingsResponse(
        @Schema(description = "자료 기준일. 서버가 국토부 실거래 자료를 마지막으로 받은 날짜 (목록에 나온 구 중 가장 오래된 날짜). 목록이 비면 null",
                example = "2026-11-07", nullable = true)
        LocalDate dataBaseDate,

        @Schema(description = "반경 안의 건물, 가까운 순. 없으면 [] → 「주변에 거래된 건물이 없어요. 반경을 넓혀 보세요」")
        List<NearbyBuilding> buildings) {

    @Schema(description = "주변 건물 한 곳")
    public record NearbyBuilding(
            @Schema(description = "건물 ID. 건물 상세 API 에 그대로 넣으면 돼요", example = "21")
            Long buildingId,

            @Schema(description = "단지명 (국토부 실거래 자료의 단지명)", example = "한화오벨리스크")
            String name,

            @Schema(description = "건물 유형. APT 아파트, OFFICETEL 오피스텔", example = "APT",
                    allowableValues = {"APT", "OFFICETEL"})
            String type,

            @Schema(description = "지번 주소 (시도는 줄임말)", example = "서울 마포구 도화동 555")
            String address,

            @Schema(description = "건물 위도", example = "37.54004")
            double lat,

            @Schema(description = "건물 경도", example = "126.94547")
            double lon,

            @Schema(description = "내 위치에서 거리 (m, 반올림)", example = "180")
            int distanceM,

            @Schema(description = "건물 사진 URL. 출처 미정이라 지금은 항상 null → 기본 이미지 (예시는 형식만 보여 주는 가짜 주소)",
                    example = "https://example.com/buildings/21.jpg", nullable = true)
            String imageUrl,

            @Schema(description = "최근 1년 거래 중 가장 최근 1건 (해제 거래 제외). 없으면 null → 「최근 거래 없음」", nullable = true)
            LatestDeal latestDeal) {
    }

    @Schema(description = "최근 거래 1건")
    public record LatestDeal(
            @Schema(description = "거래금액, 만원 단위 (59700 = 5억 9,700만 원)", example = "59700")
            Integer price,

            @Schema(description = "계약일", example = "2026-09-30")
            LocalDate dealDate,

            @Schema(description = "전용면적 ㎡ (소수 2자리)", example = "36.49")
            BigDecimal areaM2,

            @Schema(description = "전용면적 평 (소수 1자리, 1평 = 3.3058㎡)", example = "11.0")
            BigDecimal areaPyeong) {
    }
}
