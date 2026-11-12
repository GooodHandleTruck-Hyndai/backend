package com.gooodhandletruck.zippick.domain.building.dto;

import com.gooodhandletruck.zippick.domain.trade.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 건물 상세(7번) 응답 data. 필드 이름은 API 명세와 같아요. (#6 에서 추가)
 * #33 변경: Swagger 에 칸마다 설명·예시가 보이도록 @Schema 추가 (팀원 DeviceResponse 형식). 예시 값은 실제 건물 21 응답.
 * #37 변경: lat·lon 이 채워져서 설명 수정. 예시는 건물 21 의 실제 카카오 좌표.
 * #42 변경: saved 설명 수정 (실제 저장 여부 연결).
 */
@Schema(description = "건물 상세")
public record BuildingDetailResponse(
        @Schema(description = "건물 ID", example = "21")
        Long buildingId,

        @Schema(description = "단지명 (국토부 실거래 자료의 단지명)", example = "한화오벨리스크")
        String name,

        // #33: 오피스텔 실거래(24번) 이후 OFFICETEL 도 내려가서 둘 다 적음
        @Schema(description = "건물 유형. APT 아파트, OFFICETEL 오피스텔", example = "APT",
                allowableValues = {"APT", "OFFICETEL"})
        String type,

        @Schema(description = "지번 주소 (시도는 줄임말)", example = "서울 마포구 도화동 555")
        String jibunAddress,

        @Schema(description = "도로명 주소. 아직 준비 중이라 지금은 항상 null", example = "서울특별시 마포구 마포대로 195",
                nullable = true)
        String roadAddress,

        @Schema(description = "건축년도", example = "2004", nullable = true)
        Integer builtYear,

        @Schema(description = "건물 위도. 주소로 좌표를 못 찾으면 null", example = "37.54004", nullable = true)
        Double lat,

        @Schema(description = "건물 경도. 주소로 좌표를 못 찾으면 null", example = "126.94547", nullable = true)
        Double lon,

        // #33: 예시가 없으면 Swagger 가 "string" 으로 보여서 형식 예시를 넣음 (실제 값은 아직 null)
        @Schema(description = "건물 사진 URL. 출처 미정이라 지금은 항상 null (예시는 형식만 보여 주는 가짜 주소)",
                example = "https://example.com/buildings/21.jpg", nullable = true)
        String imageUrl,

        @Schema(description = "내가 저장한 건물인지. X-Anonymous-Id 헤더를 보낼 때만 계산하고, 헤더가 없으면 false", example = "false")
        boolean saved,

        @Schema(description = "자료 기준일. 서버가 국토부 실거래 자료를 마지막으로 받은 날짜", example = "2026-11-07")
        LocalDate dataBaseDate,

        @Schema(description = "최근 거래 최대 3건, 최신순. 해제(취소)된 거래는 빠져요")
        List<TransactionResponse> recentTransactions,

        @Schema(description = "true 면 「더보기」 버튼을 보여 주고 실거래 내역 API(/transactions)를 불러요", example = "true")
        boolean hasMoreTransactions) {
}
