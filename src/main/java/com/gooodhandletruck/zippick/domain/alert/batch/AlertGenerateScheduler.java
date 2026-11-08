package com.gooodhandletruck.zippick.domain.alert.batch;

import com.gooodhandletruck.zippick.domain.alert.service.AlertGenerateService;
import com.gooodhandletruck.zippick.global.util.KstClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 알림 만들기 배치. 기본은 매일 04:30(KST) — 실거래 재수집(04:00) 다음. (#18 에서 추가)
 * zippick.alert.generate.enabled=true 일 때만 만들어져요 (테스트·CI·다른 팀원 로컬에서는 안 돎)
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "zippick.alert.generate.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AlertGenerateScheduler {

    private final AlertGenerateService alertGenerateService;

    @Scheduled(cron = "${zippick.alert.generate.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void generate() {
        int created = alertGenerateService.generate(KstClock.today());
        log.info("관심 건물 알림 만들기: 새 알림 {}개", created);
    }
}
