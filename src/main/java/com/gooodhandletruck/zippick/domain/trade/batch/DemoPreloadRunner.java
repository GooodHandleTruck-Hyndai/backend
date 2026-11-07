package com.gooodhandletruck.zippick.domain.trade.batch;

import com.gooodhandletruck.zippick.global.util.KstClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 시연 지역 사전 저장 (API 5번). 서버가 다 켜진 뒤 한 번, 설정한 구의 최근 12개월을 DB 에 넣어요. (#17 에서 추가)
 *
 * 서버가 요청을 받을 준비가 끝난 뒤(ApplicationReadyEvent)에 돌아서, 적재하는 동안에도 API 는 응답해요.
 * 발표장에서 통신이 끊겨 있어도 서버는 정상으로 켜지고, 실패한 달은 기존 데이터를 그대로 써요.
 * 관리용 API 를 따로 열지 않은 이유: 인증이 없는 지금 구조에선 누구나 국토부 호출을 일으킬 수 있어서예요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoPreloadRunner {

    private final CollectProperties properties;
    private final TradeCollectBatch batch;

    @EventListener(ApplicationReadyEvent.class)
    public void preload() {
        if (!properties.preload().enabled()) {
            return;
        }
        log.info("시연 지역 사전 저장 시작 구={} 최근 {}개월", properties.sggCds(), properties.preload().months());
        batch.collectRecent(properties.sggCds(), properties.preload().months(), KstClock.today());
    }
}
