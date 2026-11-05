package com.gooodhandletruck.zippick.domain.building.service;

import com.gooodhandletruck.zippick.domain.building.dto.BuildingDetailResponse;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.domain.trade.dto.TransactionResponse;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 건물 조회 로직. (#6 에서 추가: 건물 상세)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingService {

    /** 건물 상세에 보여 줄 최근 거래 수 (명세: 최대 3건) */
    static final int RECENT_TRANSACTION_LIMIT = 3;

    private final BuildingRepository buildingRepository;
    private final TradeRepository tradeRepository;
    private final LegalDongRepository legalDongRepository;

    /**
     * 건물 상세 (7번).
     *
     * @param anonymousId X-Anonymous-Id 헤더 (없으면 null). 저장 기능(B) 이 머지되면 saved 계산에 써요
     */
    public BuildingDetailResponse getDetail(Long buildingId, String anonymousId) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUILDING_NOT_FOUND));

        List<Trade> trades = tradeRepository.findTop4ByBuildingIdAndCdealTypeIsNullOrderByDealDateDescIdDesc(
                buildingId);
        boolean hasMore = trades.size() > RECENT_TRANSACTION_LIMIT;
        List<TransactionResponse> recent = trades.stream()
                .limit(RECENT_TRANSACTION_LIMIT)
                .map(TransactionResponse::from)
                .toList();

        LocalDateTime latestCollectedAt = tradeRepository.findLatestCollectedAt(building.getSggCd());

        return new BuildingDetailResponse(
                building.getId(),
                building.getAptNm(),
                building.getBuildingType().name(),
                JibunAddressFormatter.format(findRegion(building), building.getUmdNm(), building.getJibun()),
                null, // 도로명주소는 P1 (14번) — 명세대로 지금은 null
                building.getBuildYear(),
                building.getLatitude(),
                building.getLongitude(),
                null, // 건물 사진 출처 미정 — 명세대로 null
                false, // TODO 저장 기능(B, 9~11번) 머지 후 anonymousId 로 저장 여부 연결
                latestCollectedAt == null ? null : latestCollectedAt.toLocalDate(),
                recent,
                hasMore);
    }

    /** 주소 매칭으로 연결된 법정동이 있으면 그걸, 없으면 구 코드로 시도·시군구 이름만 찾아요 */
    private LegalDong findRegion(Building building) {
        if (building.getLegalDong() != null) {
            return building.getLegalDong();
        }
        return legalDongRepository.findFirstBySggCd(building.getSggCd()).orElse(null);
    }
}
