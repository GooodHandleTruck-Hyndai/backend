package com.gooodhandletruck.zippick.domain.trade.batch;

import com.gooodhandletruck.zippick.domain.trade.client.MolitDataset;
import com.gooodhandletruck.zippick.domain.trade.entity.CollectRun;
import com.gooodhandletruck.zippick.domain.trade.entity.CollectStatus;
import com.gooodhandletruck.zippick.domain.trade.service.TradeIngestService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 여러 구 × 최근 N개월을 한 달씩 적재 서비스(#8)로 반영해요. (#17 에서 추가)
 * 시연 지역 사전 저장(API 5번)과 재수집(API 12번)이 같이 써요 — 둘 다 "범위만 다른 같은 일"이라서.
 *
 * #27 변경: 아파트·오피스텔(MolitDataset 전부)을 같은 구·기간으로 받아요.
 * 한 달이 실패해도 다음 달로 넘어가요. 실패한 달은 DB 가 그대로이고 수집 이력에 FAILED·SKIPPED 로 남아요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(CollectProperties.class)
public class TradeCollectBatch {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final TradeIngestService ingestService;

    /**
     * @param today  기준일 (KST). 이 날이 속한 달부터 거꾸로 months 개월
     * @param months 이번 달 포함 개월 수
     * @return 달마다 수집 이력 (오래된 달부터)
     */
    public List<CollectRun> collectRecent(List<String> sggCds, int months, LocalDate today) {
        List<String> dealYms = recentMonths(today, months);
        List<CollectRun> runs = new ArrayList<>();
        for (String sggCd : sggCds) {
            for (MolitDataset dataset : MolitDataset.values()) {
                for (String dealYm : dealYms) {
                    try {
                        runs.add(ingestService.collectMonth(dataset.buildingType(), sggCd, dealYm));
                    } catch (RuntimeException e) {
                        // collectMonth 는 결과를 이력으로 돌려주고 던지지 않지만, 이력 저장 자체가 실패하는 경우(DB 끊김 등)에도
                        // 남은 달은 계속 시도해요
                        log.error("실거래 수집 중 예외 type={} sggCd={} dealYm={}", dataset, sggCd, dealYm, e);
                    }
                }
            }
        }
        log.info("실거래 수집 완료 구={} 기간={}~{} 결과={}", sggCds, dealYms.get(0), dealYms.get(dealYms.size() - 1),
                summarize(runs));
        return runs;
    }

    /** 이번 달 포함 최근 n개월 YYYYMM, 오래된 달부터. 예: 2026-11-07, 3 → [202609, 202610, 202611] */
    static List<String> recentMonths(LocalDate today, int months) {
        YearMonth current = YearMonth.from(today);
        List<String> result = new ArrayList<>(months);
        for (int i = months - 1; i >= 0; i--) {
            result.add(current.minusMonths(i).format(YM));
        }
        return result;
    }

    private static Map<CollectStatus, Long> summarize(List<CollectRun> runs) {
        return runs.stream().collect(Collectors.groupingBy(CollectRun::getStatus, Collectors.counting()));
    }
}
