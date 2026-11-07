package com.gooodhandletruck.zippick.domain.trade.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gooodhandletruck.zippick.domain.trade.service.TradeIngestService;
import com.gooodhandletruck.zippick.global.util.KstClock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 시연 지역 사전 저장 (API 5번) 실행 조건과 설정 읽기. (#17 에서 추가)
 */
class DemoPreloadRunnerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withBean(TradeIngestService.class, () -> mock(TradeIngestService.class))
            .withUserConfiguration(TradeCollectBatch.class);

    // ---- 실행 조건 ----

    @Test
    void 꺼져_있으면_국토부를_부르지_않는다() {
        TradeCollectBatch batch = mock(TradeCollectBatch.class);
        CollectProperties properties = new CollectProperties(List.of("11440"), new CollectProperties.Preload(false, 12));

        new DemoPreloadRunner(properties, batch).preload();

        verify(batch, never()).collectRecent(anyList(), anyInt(), any());
    }

    @Test
    void 켜져_있으면_설정한_구의_최근_12개월을_오늘_KST_기준으로_넣는다() {
        TradeCollectBatch batch = mock(TradeCollectBatch.class);
        CollectProperties properties = new CollectProperties(List.of("11440"), new CollectProperties.Preload(true, 12));
        LocalDate before = KstClock.today();

        new DemoPreloadRunner(properties, batch).preload();

        // 자정 직전에 돌아 날짜가 바뀌어도 깨지지 않게, 실행 전후 날짜 중 하나면 맞다고 봐요
        verify(batch).collectRecent(eq(List.of("11440")), eq(12),
                argThat(d -> d.equals(before) || d.equals(KstClock.today())));
    }

    // ---- 설정 읽기 (application.yml 모양 그대로) ----

    @Test
    void 아무_설정이_없으면_마포구_12개월_꺼짐이다() {
        contextRunner.run(context -> {
            CollectProperties p = context.getBean(CollectProperties.class);
            assertThat(p.sggCds()).containsExactly("11440");
            assertThat(p.preload().enabled()).isFalse(); // 테스트·CI 에서 국토부를 부르지 않는 근거
            assertThat(p.preload().months()).isEqualTo(12);
        });
    }

    @Test
    void 구_여러_개와_켜기를_읽는다() {
        contextRunner.withPropertyValues(
                        "zippick.collect.sgg-cds=11440,11410",
                        "zippick.collect.preload.enabled=true",
                        "zippick.collect.preload.months=6")
                .run(context -> {
                    CollectProperties p = context.getBean(CollectProperties.class);
                    assertThat(p.sggCds()).containsExactly("11440", "11410");
                    assertThat(p.preload().enabled()).isTrue();
                    assertThat(p.preload().months()).isEqualTo(6);
                });
    }

    @ParameterizedTest(name = "sgg-cds={0} → 서버 시작 실패")
    @ValueSource(strings = {"1144", "114400", "마포구", "11440,abcde"})
    void 구_코드가_5자리_숫자가_아니면_서버가_켜지지_않는다(String sggCds) {
        contextRunner.withPropertyValues("zippick.collect.sgg-cds=" + sggCds)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("5자리"));
    }

    @ParameterizedTest(name = "months={0} → 서버 시작 실패")
    @ValueSource(ints = {0, -1, 37})
    void 개월_수가_범위를_벗어나면_서버가_켜지지_않는다(int months) {
        contextRunner.withPropertyValues("zippick.collect.preload.months=" + months)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("1~36"));
    }
}
