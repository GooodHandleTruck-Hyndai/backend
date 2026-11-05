package com.gooodhandletruck.zippick.domain.building.dto;

import com.gooodhandletruck.zippick.domain.trade.dto.TransactionResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * 건물 상세(7번) 응답 data. 필드 이름은 API 명세와 같아요. (#6 에서 추가)
 *
 * @param buildingId          건물 ID
 * @param name                단지명 (국토부 aptNm)
 * @param type                건물 유형 (APT)
 * @param jibunAddress        지번 주소 (예: 서울 마포구 상암동 1601)
 * @param roadAddress         도로명주소. P1 이라 지금은 null
 * @param builtYear           건축년도
 * @param lat                 위도
 * @param lon                 경도
 * @param imageUrl            건물 사진. 출처 미정이라 null
 * @param saved               저장 여부. 저장 기능(B) 연결 전까지 false
 * @param dataBaseDate        자료 기준일 (서버가 국토부 데이터를 받은 날짜)
 * @param recentTransactions  최신순 최대 3건, 해제 거래 제외
 * @param hasMoreTransactions true 면 앱이 「더보기」로 실거래 내역(8번)을 불러요
 */
public record BuildingDetailResponse(
        Long buildingId,
        String name,
        String type,
        String jibunAddress,
        String roadAddress,
        Integer builtYear,
        Double lat,
        Double lon,
        String imageUrl,
        boolean saved,
        LocalDate dataBaseDate,
        List<TransactionResponse> recentTransactions,
        boolean hasMoreTransactions) {
}
