package com.gooodhandletruck.zippick.domain.trade.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gooodhandletruck.zippick.domain.trade.service.TradeIngestService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.CronExpression;

/**
 * 최근 실거래 재수집 (API 12번) 스케줄 등록 조건과 범위. (#18 에서 추가)
 * 실제로 새벽 4시를 기다리지 않고, 스케줄이 "어떤 cron 으로 등록됐는지"와 "실행하면 무엇을 부르는지"를 나눠서 봐요.
 */
class TradeRefreshSchedulerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withBean(TradeIngestService.class, () -> mock(TradeIngestService.class))
            .withUserConfiguration(TradeCollectBatch.class, TradeRefreshScheduler.class);

    private static List<String> cronExpressions(ScheduledTaskHolder holder) {
        return holder.getScheduledTasks().stream()
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(t -> ((CronTask) t).getExpression())
                .toList();
    }

    // ---- 켜기/끄기 ----

    @Test
    void 기본은_꺼져_있어서_스케줄이_없다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TradeRefreshScheduler.class);
            assertThat(context).doesNotHaveBean(ScheduledTaskHolder.class); // 스케줄 기능 자체가 안 켜져요
        });
    }

    @Test
    void 켜면_매일_4시_스케줄이_등록된다() {
        contextRunner.withPropertyValues("zippick.collect.refresh.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(TradeRefreshScheduler.class);
            assertThat(cronExpressions(context.getBean(ScheduledTaskHolder.class))).containsExactly("0 0 4 * * *");
            assertThat(context.getBean(CollectProperties.class).refresh().months()).isEqualTo(3);
        });
    }

    @Test
    void 시각은_설정으로_바꿀_수_있다() {
        contextRunner.withPropertyValues("zippick.collect.refresh.enabled=true", "zippick.collect.refresh.cron=0 30 5 * * *")
                .run(context -> assertThat(cronExpressions(context.getBean(ScheduledTaskHolder.class)))
                        .containsExactly("0 30 5 * * *"));
    }

    @Test
    void 잘못된_cron_이면_서버가_켜지지_않는다() {
        contextRunner.withPropertyValues("zippick.collect.refresh.enabled=true", "zippick.collect.refresh.cron=매일 4시")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 개월_수가_범위를_벗어나면_서버가_켜지지_않는다() {
        contextRunner.withPropertyValues("zippick.collect.refresh.months=0")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("refresh.months"));
    }

    // ---- 실행 내용 ----

    @Test
    void 실행하면_설정한_구의_최근_3개월을_다시_받는다() {
        TradeCollectBatch batch = mock(TradeCollectBatch.class);
        CollectProperties properties = new CollectProperties(List.of("11440"),
                new CollectProperties.Preload(false, 12), new CollectProperties.Refresh(true, 3));

        new TradeRefreshScheduler(properties, batch).refresh();

        verify(batch).collectRecent(eq(List.of("11440")), eq(3), any());
    }

    @Test
    void 스케줄은_한국_시간대로_돈다() throws Exception {
        // zone 이 빠지면 서버 시간대를 따라가요 — 클라우드 서버가 UTC 면 한국 13:00 에 돌게 돼요 (변형 검사로 찾은 빈틈)
        Scheduled scheduled = TradeRefreshScheduler.class.getMethod("refresh").getAnnotation(Scheduled.class);

        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 기본_cron_은_한국_시간_새벽_4시에_돈다() {
        // 스케줄에 zone=Asia/Seoul 을 줬으니, 한국 시간 기준으로 다음 실행이 다음 날 04:00 이어야 해요
        ZonedDateTime afternoon = ZonedDateTime.of(2026, 11, 7, 14, 0, 0, 0, KST);
        ZonedDateTime beforeFour = ZonedDateTime.of(2026, 11, 8, 3, 59, 59, 0, KST);

        CronExpression cron = CronExpression.parse("0 0 4 * * *");

        assertThat(cron.next(afternoon)).isEqualTo(ZonedDateTime.of(2026, 11, 8, 4, 0, 0, 0, KST));
        assertThat(cron.next(beforeFour)).isEqualTo(ZonedDateTime.of(2026, 11, 8, 4, 0, 0, 0, KST));
    }
}
