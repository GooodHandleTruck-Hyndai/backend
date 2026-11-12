package com.gooodhandletruck.zippick.domain.alert.service;

import com.gooodhandletruck.zippick.domain.alert.entity.Alert;
import com.gooodhandletruck.zippick.domain.alert.repository.AlertRepository;
import com.gooodhandletruck.zippick.domain.alert.repository.AlertTradeRepository;
import com.gooodhandletruck.zippick.domain.favorite.repository.FavoriteRepository;
import com.gooodhandletruck.zippick.domain.interest.entity.InterestCondition;
import com.gooodhandletruck.zippick.domain.interest.repository.InterestConditionRepository;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새 거래를 찾아 알림을 만들어요. (#18 에서 추가)
 *
 * 대상: 알림을 켠 기기 (관심 조건이 없으면 알림 없음 — 명세)
 * 알림을 만드는 거래:
 *  - 관심 조건에 맞는 거래 (지역·유형·가격·면적), 또는
 *  - 저장한 건물의 거래 (조건과 상관없이)
 * 알림 폭탄 방지:
 *  - 조건을 저장한 뒤에 서버에 들어온 거래만 (처음 적재한 12개월치는 알리지 않음)
 *  - 계약일이 최근 30일 이내인 거래만
 *  - 같은 기기에 같은 거래는 한 번만
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertGenerateService {

    static final int RECENT_DEAL_DAYS = 30;

    private final InterestConditionRepository interestConditionRepository;
    private final FavoriteRepository favoriteRepository;
    private final AlertTradeRepository alertTradeRepository;
    private final AlertRepository alertRepository;

    /** @return 새로 만든 알림 수 */
    @Transactional
    public int generate(LocalDate today) {
        LocalDate minDealDate = today.minusDays(RECENT_DEAL_DAYS);
        int created = 0;
        for (InterestCondition condition : interestConditionRepository.findAllByAlertEnabledTrue()) {
            created += generateFor(condition, minDealDate);
        }
        return created;
    }

    private int generateFor(InterestCondition condition, LocalDate minDealDate) {
        Long deviceId = condition.getDevice().getId();

        Map<Long, Trade> candidates = new LinkedHashMap<>();
        for (Trade trade : alertTradeRepository.findNewInRegions(
                condition.getRegionCodes(), condition.getUpdatedAt(), minDealDate)) {
            if (matches(condition, trade)) {
                candidates.put(trade.getId(), trade);
            }
        }
        List<Long> favoriteBuildingIds = favoriteRepository.findBuildingIdsByDeviceId(deviceId);
        if (!favoriteBuildingIds.isEmpty()) {
            for (Trade trade : alertTradeRepository.findNewInBuildings(
                    favoriteBuildingIds, condition.getUpdatedAt(), minDealDate)) {
                candidates.putIfAbsent(trade.getId(), trade);
            }
        }
        if (candidates.isEmpty()) {
            return 0;
        }

        Set<Long> already = new HashSet<>(alertRepository.findTradeIds(deviceId, candidates.keySet()));
        List<Alert> alerts = candidates.values().stream()
                .filter(trade -> !already.contains(trade.getId()))
                .map(trade -> Alert.of(condition.getDevice(), trade))
                .toList();
        alertRepository.saveAll(alerts);
        return alerts.size();
    }

    /** 관심 조건에 맞는 거래인지. 범위의 한쪽이 null 이면 그쪽은 제한 없음 */
    static boolean matches(InterestCondition condition, Trade trade) {
        if (!condition.getRegionCodes().contains(trade.getSggCd())) {
            return false;
        }
        if (!condition.getTypes().contains(trade.getBuilding().getBuildingType())) {
            return false;
        }
        int price = trade.getDealAmount();
        if (condition.getPriceMin() != null && price < condition.getPriceMin()) {
            return false;
        }
        if (condition.getPriceMax() != null && price > condition.getPriceMax()) {
            return false;
        }
        BigDecimal area = trade.getExcluUseAr();
        if (condition.getAreaMinM2() != null && area.compareTo(BigDecimal.valueOf(condition.getAreaMinM2())) < 0) {
            return false;
        }
        return condition.getAreaMaxM2() == null || area.compareTo(BigDecimal.valueOf(condition.getAreaMaxM2())) <= 0;
    }
}
