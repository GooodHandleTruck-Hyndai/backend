package com.gooodhandletruck.zippick.domain.trade.batch;

import com.gooodhandletruck.zippick.global.util.KstClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 최근 실거래 재수집 (API 12번). 매일 04:00(KST)에 설정한 구의 최근 3개월을 다시 받아요. (#18 에서 추가)
 *
 * 거래 신고는 계약 후 최대 30일 뒤에 들어오고 취소도 나중에 표시돼서, 지난 달 것도 다시 받아야 해요.
 * 다시 받으면 적재 서비스(#8)가 바뀐 거래만 수정·새 거래 추가·사라진 거래 삭제를 해요.
 *
 * zippick.collect.refresh.enabled=true 일 때만 이 클래스가 만들어지고, 그때만 스케줄 기능(@EnableScheduling)도 켜져요.
 * 꺼져 있으면 테스트·CI·다른 팀원 로컬에서 새벽에 국토부를 부르는 일이 없어요.
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "zippick.collect.refresh.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TradeRefreshScheduler {

    private final CollectProperties properties;
    private final TradeCollectBatch batch;

    // zone 을 꼭 적어요: 서버(클라우드) 시간대가 UTC 면 04:00 이 한국 13:00 이 돼요
    @Scheduled(cron = "${zippick.collect.refresh.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void refresh() {
        log.info("실거래 재수집 시작 구={} 최근 {}개월", properties.sggCds(), properties.refresh().months());
        batch.collectRecent(properties.sggCds(), properties.refresh().months(), KstClock.today());
    }
}
