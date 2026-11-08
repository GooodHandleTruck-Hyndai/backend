package com.gooodhandletruck.zippick.domain.building.service;

import com.gooodhandletruck.zippick.domain.building.dto.BuildingAddress;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingCoordinateResponse;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingDetailResponse;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.favorite.repository.FavoriteRepository;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.domain.trade.dto.PriceTrendResponse;
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
import com.gooodhandletruck.zippick.global.util.KstClock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 건물 조회 로직. (#6 에서 추가: 건물 상세)
 * #16 변경: 실거래 내역(API 8번, 더보기) getTransactions 추가.
 * #26 변경: 가격 추이(API 13번) getPriceTrend 추가.
 * #19 변경: 자료 기준일(dataBaseDate)을 그 구의 마지막 성공 수집 날짜로 (없으면 예전처럼 마지막 거래 수집 날짜).
 * #37 변경: 건물 상세 lat·lon 을 채워요. 행안부 좌표(JUSO)가 DB 에 있으면 그 값, 없으면 #13 좌표 서비스로
 *   카카오에 건물 하나만 실시간으로 물어봐요(저장 안 함 — 카카오 정책). 못 찾으면 좌표만 null, 상세는 정상.
 * #42 변경: saved 를 실제 저장 여부로 연결 (저장 기능 #12 머지 후). 헤더가 없거나 등록 안 된 ID 면 false.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingService {

    /** 건물 상세에 보여 줄 최근 거래 수 (명세: 최대 3건) */
    static final int RECENT_TRANSACTION_LIMIT = 3;

    /** 실거래 내역 쪽 크기 상한 (명세: 최대 50) */
    static final int MAX_TRANSACTION_PAGE_SIZE = 50;

    /** 가격 추이 개월 수 (명세: 12개를 항상 채워서) */
    static final int PRICE_TREND_MONTHS = 12;
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final BuildingRepository buildingRepository;
    private final TradeRepository tradeRepository;
    private final LegalDongRepository legalDongRepository;
    private final CollectRunRepository collectRunRepository;
    private final FavoriteRepository favoriteRepository; // #42 saved 계산 (팀원 저장 기능의 조회만 사용)
    private final BuildingCoordinateService buildingCoordinateService; // #37 건물 상세 좌표

    /**
     * 건물 상세 (7번).
     *
     * @param anonymousId X-Anonymous-Id 헤더 (없으면 null). saved 계산에 써요 (#42)
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

        double[] coordinate = coordinate(building); // #37: null 이면 좌표 없음

        return new BuildingDetailResponse(
                building.getId(),
                building.getAptNm(),
                building.getBuildingType().name(),
                JibunAddressFormatter.format(findRegion(building), building.getUmdNm(), building.getJibun()),
                null, // 도로명주소는 P1 (14번) — 명세대로 지금은 null
                building.getBuildYear(),
                coordinate == null ? null : coordinate[0], // #37: 예전엔 DB 값(항상 비어 있었음)을 그대로 썼어요
                coordinate == null ? null : coordinate[1],
                null, // 건물 사진 출처 미정 — 명세대로 null
                isSaved(anonymousId, buildingId), // #42
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

    /** 가격 추이 (API 13번). 이번 달 포함 최근 12개월, 오래된 달부터 (#26) */
    public PriceTrendResponse getPriceTrend(Long buildingId) {
        return getPriceTrend(buildingId, KstClock.today());
    }

    /** 테스트에서 기준일을 고정하려고 나눠 둬요 */
    PriceTrendResponse getPriceTrend(Long buildingId, LocalDate today) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new BusinessException(ErrorCode.BUILDING_NOT_FOUND);
        }
        YearMonth to = YearMonth.from(today);
        YearMonth from = to.minusMonths(PRICE_TREND_MONTHS - 1);

        Map<String, TradeRepository.MonthlyTotal> byYm = new HashMap<>();
        for (TradeRepository.MonthlyTotal row : tradeRepository.sumByDealYm(buildingId, from.format(YM), to.format(YM))) {
            byYm.put(row.getDealYm(), row);
        }

        // 거래 없는 달도 빠짐없이 12개 — 앱이 x축을 그대로 그리면 돼요(명세: 항상 채워서)
        List<PriceTrendResponse.MonthlyPrice> months = new ArrayList<>(PRICE_TREND_MONTHS);
        for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
            TradeRepository.MonthlyTotal row = byYm.get(ym.format(YM));
            String label = ym.toString(); // "2025-12"
            if (row == null) {
                months.add(new PriceTrendResponse.MonthlyPrice(label, null, 0));
            } else {
                int avg = BigDecimal.valueOf(row.getTotal())
                        .divide(BigDecimal.valueOf(row.getCnt()), 0, RoundingMode.HALF_UP)
                        .intValueExact();
                months.add(new PriceTrendResponse.MonthlyPrice(label, avg, row.getCnt().intValue()));
            }
        }
        return new PriceTrendResponse(months);
    }

    /**
     * 이 기기가 건물을 저장했는지 (#42). 헤더가 없으면 false — 상세는 헤더 없이도 보여야 해서 에러를 내지 않아요.
     * 등록 안 된 ID 도 조회 결과가 없어서 false 예요 (저장 API 처럼 D002 를 내지 않음)
     */
    private boolean isSaved(String anonymousId, Long buildingId) {
        if (anonymousId == null || anonymousId.isBlank()) {
            return false;
        }
        return favoriteRepository.existsByDeviceAnonymousIdAndBuildingId(anonymousId, buildingId);
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

    /**
     * 건물 좌표 {위도, 경도} (#37). 주변 건물(#35)과 같은 규칙:
     * 행안부 좌표(coordSource=JUSO)만 DB 값을 믿고, 그 밖엔 #13 좌표 서비스(카카오 실시간, 동 + 지번 검색).
     * 출처 없는 저장 좌표(예전 샘플 데이터 등)는 근거가 없어서 쓰지 않아요. 못 찾으면 null —
     * #13 서비스가 카카오 오류도 "못 찾음"으로 돌려줘서 상세가 실패하지 않아요.
     * 건물 하나라 카카오 호출은 1~2번(도로명 없음 → 지번 1번)이고, 주변 건물 같은 한도 문제는 없어요.
     */
    private double[] coordinate(Building building) {
        if (building.getCoordSource() == CoordSource.JUSO
                && building.getLatitude() != null && building.getLongitude() != null) {
            return new double[] {building.getLatitude(), building.getLongitude()};
        }
        // 국토부 일반 실거래 자료엔 도로명이 없어서 지번(동 + 지번)으로만 찾아요
        BuildingCoordinateResponse found = buildingCoordinateService.findCoordinate(new BuildingAddress(
                String.valueOf(building.getId()), building.getSggCd(), null, null, null,
                building.getUmdNm(), building.getJibun()));
        if (found == null || !found.found() || found.lat() == null || found.lon() == null) {
            return null;
        }
        return new double[] {found.lat(), found.lon()};
    }

    /** 주소 매칭으로 연결된 법정동이 있으면 그걸, 없으면 구 코드로 시도·시군구 이름만 찾아요 */
    private LegalDong findRegion(Building building) {
        if (building.getLegalDong() != null) {
            return building.getLegalDong();
        }
        return legalDongRepository.findFirstBySggCd(building.getSggCd()).orElse(null);
    }
}
