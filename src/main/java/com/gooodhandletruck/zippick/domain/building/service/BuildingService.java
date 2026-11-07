package com.gooodhandletruck.zippick.domain.building.service;

import com.gooodhandletruck.zippick.domain.building.dto.BuildingDetailResponse;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.domain.trade.dto.TransactionListResponse;
import com.gooodhandletruck.zippick.domain.trade.dto.TransactionResponse;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import com.gooodhandletruck.zippick.domain.trade.repository.CollectRunRepository;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 건물 조회 로직. (#6 에서 추가: 건물 상세)
 * #16 변경: 실거래 내역(API 8번, 더보기) getTransactions 추가.
 * #19 변경: 자료 기준일(dataBaseDate)을 그 구의 마지막 성공 수집 날짜로 (없으면 예전처럼 마지막 거래 수집 날짜).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingService {

    /** 건물 상세에 보여 줄 최근 거래 수 (명세: 최대 3건) */
    static final int RECENT_TRANSACTION_LIMIT = 3;

    /** 실거래 내역 쪽 크기 상한 (명세: 최대 50) */
    static final int MAX_TRANSACTION_PAGE_SIZE = 50;

    private final BuildingRepository buildingRepository;
    private final TradeRepository tradeRepository;
    private final LegalDongRepository legalDongRepository;
    private final CollectRunRepository collectRunRepository;

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
                dataBaseDate(building.getSggCd()),
                recent,
                hasMore);
    }

    /**
     * 실거래 내역 (API 8번, 더보기). 해제 거래 제외, 최신순, 쪽 단위.
     *
     * @param page 0부터
     * @param size 1~50
     */
    public TransactionListResponse getTransactions(Long buildingId, int page, int size) {
        // 명세: page·size 범위 오류는 C001. 없는 건물(B001)보다 먼저 봐요 — 요청 자체가 잘못된 거라서
        if (page < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "page 는 0 이상이어야 해요: " + page);
        }
        if (size < 1 || size > MAX_TRANSACTION_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "size 는 1~" + MAX_TRANSACTION_PAGE_SIZE + " 이어야 해요: " + size);
        }
        // 거래 0건인 건물(빈 목록)과 없는 건물(B001)을 구분하려고 건물부터 확인해요
        if (!buildingRepository.existsById(buildingId)) {
            throw new BusinessException(ErrorCode.BUILDING_NOT_FOUND);
        }

        Slice<Trade> slice = tradeRepository.findByBuildingIdAndCdealTypeIsNullOrderByDealDateDescIdDesc(
                buildingId, PageRequest.of(page, size));
        return new TransactionListResponse(
                slice.getContent().stream().map(TransactionResponse::from).toList(),
                page,
                size,
                slice.hasNext());
    }

    /**
     * 자료 기준일 = 그 구 데이터를 마지막으로 받은 날짜 (#19, 명세 12번 "다시 받은 날짜가 dataBaseDate 로 바뀌어요").
     * 수집 이력이 없으면(배치 도입 전에 넣은 데이터 등) 그 구에서 마지막으로 거래가 들어온 날짜로 대신해요.
     * 둘 다 없으면 null — 그 구 데이터를 아직 한 번도 안 받은 거예요.
     */
    LocalDate dataBaseDate(String sggCd) {
        LocalDateTime lastSuccess = collectRunRepository.findLastSuccessAt(sggCd);
        if (lastSuccess != null) {
            return lastSuccess.toLocalDate();
        }
        LocalDateTime latestTrade = tradeRepository.findLatestCollectedAt(sggCd);
        return latestTrade == null ? null : latestTrade.toLocalDate();
    }

    /** 주소 매칭으로 연결된 법정동이 있으면 그걸, 없으면 구 코드로 시도·시군구 이름만 찾아요 */
    private LegalDong findRegion(Building building) {
        if (building.getLegalDong() != null) {
            return building.getLegalDong();
        }
        return legalDongRepository.findFirstBySggCd(building.getSggCd()).orElse(null);
    }
}
