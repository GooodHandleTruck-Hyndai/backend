package com.gooodhandletruck.zippick.domain.building.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 앞쪽 건물 찾기 (API 20번) 응답 data. (#50 에서 추가)
 *
 * 후보 한 개의 필드는 주변 건물(API 2번) 의 건물 한 곳과 이름·의미가 같아요 — 프론트가 주변 건물 응답을 카드로 바꾸는
 * 변환(NearbyBuildingDto.toSummary)을 그대로 쓸 수 있게 맞췄어요. 거기에 side·direction·saved, latestDeal.floor 를 더했어요.
 * 예시 값은 로컬 실데이터(마포 도화동 한화오벨리스크, 건물 21) 기준이에요.
 */
@Schema(description = "앞쪽 건물 후보")
public record BuildingIdentifyResponse(
        @Schema(description = "자료 기준일 (후보에 나온 구 중 가장 오래된 날짜, 주변 건물과 같은 규칙). 후보가 없으면 null",
                example = "2026-11-07", nullable = true)
        LocalDate dataBaseDate,

        @Schema(description = "차 앞쪽(진행 방향 좌우 90°, 1000m 안) 건물, 가까운 순 최대 3개. 없으면 []")
        List<Candidate> candidates,

        @Schema(description = "첫 번째 후보 기준 안내 문장. TTS 에 그대로 넣어도 돼요. 후보가 없으면 「주변에서 거래된 건물을 찾지 못했어요.」",
                example = "왼쪽 앞, 한화오벨리스크. 전용 36제곱미터, 최근 거래 5억 9,700만 원이에요. 저장할까요?")
        String speechText) {

    @Schema(description = "앞쪽 건물 한 곳")
    public record Candidate(
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

            @Schema(description = "차 위치에서 거리 (m, 반올림)", example = "180")
            int distanceM,

            @Schema(description = "건물 사진 URL. 출처 미정이라 지금은 항상 null (주변 건물과 같아요)", nullable = true)
            String imageUrl,

            @Schema(description = "최근 1년 거래 중 가장 최근 1건 (해제 거래 제외). 없으면 null", nullable = true)
            LatestDeal latestDeal,

            @Schema(description = "차에서 본 쪽. AHEAD 정면(±22.5°), RIGHT 오른쪽, LEFT 왼쪽", example = "LEFT",
                    allowableValues = {"AHEAD", "RIGHT", "LEFT"})
            String side,

            @Schema(description = "8방향 (앱 방향 칩과 같은 45° 칸). 앞쪽만 오니까 FRONT·FRONT_RIGHT·RIGHT·FRONT_LEFT·LEFT 중 하나",
                    example = "FRONT_LEFT", allowableValues = {"FRONT", "FRONT_RIGHT", "RIGHT", "FRONT_LEFT", "LEFT"})
            String direction,

            @Schema(description = "이 기기(X-Anonymous-Id)가 저장한 건물인지", example = "false")
            boolean saved) {
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
            BigDecimal areaPyeong,

            @Schema(description = "층", example = "12", nullable = true)
            Integer floor) {
    }
}
