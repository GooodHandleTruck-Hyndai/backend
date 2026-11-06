package com.gooodhandletruck.zippick.domain.trade.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제 국토부 API 연결 확인. (#7 에서 추가)
 *
 * 평소(CI 포함)에는 건너뛰어요. 키가 있는 PC 에서만 이렇게 돌려요:
 * {@code MOLIT_LIVE_TEST=true MOLIT_SERVICE_KEY=<키> ./gradlew test --tests '*MolitClientLiveTest'}
 * 실제 서버 데이터는 날마다 바뀌어서, 건수 대신 "다 받았는지·형식이 맞는지"만 봐요.
 */
@EnabledIfEnvironmentVariable(named = "MOLIT_LIVE_TEST", matches = "true")
class MolitClientLiveTest {

    private final MolitClient client = new MolitClient(new MolitProperties(
            System.getenv("MOLIT_SERVICE_KEY"), "https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade",
            1000, 50, Duration.ofSeconds(5), Duration.ofSeconds(30)));

    @Test
    void 마포구_한_달_치를_실제로_다_받는다() {
        MolitMonthResult result = client.fetchMonth("11440", "202608");

        assertThat(result.totalCount()).isPositive();
        assertThat(result.isComplete()).isTrue();
        assertThat(result.items()).allSatisfy(item -> {
            assertThat(item.sggCd()).isEqualTo("11440");
            assertThat(item.dealYear()).isEqualTo("2026");
            assertThat(item.dealMonth()).isEqualTo("8");
        });
    }

    @Test
    void 작은_페이지로_나눠_받아도_한_번에_받은_것과_같다() {
        MolitClient smallPages = new MolitClient(new MolitProperties(
                System.getenv("MOLIT_SERVICE_KEY"), "https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade",
                50, 50, Duration.ofSeconds(5), Duration.ofSeconds(30)));

        MolitMonthResult whole = client.fetchMonth("11440", "202608");
        MolitMonthResult paged = smallPages.fetchMonth("11440", "202608");

        assertThat(paged.items()).containsExactlyElementsOf(whole.items());
    }
}
