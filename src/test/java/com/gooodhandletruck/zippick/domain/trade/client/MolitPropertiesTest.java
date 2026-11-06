package com.gooodhandletruck.zippick.domain.trade.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 국토부 설정이 앱에 제대로 올라가는지 (키 없이도 앱이 뜨는지 포함). (#7 에서 추가)
 */
@SpringBootTest(properties = "zippick.molit.service-key=from-env")
class MolitPropertiesTest {

    @Autowired
    private MolitProperties properties;

    @Autowired
    private MolitClient molitClient;

    @Test
    void 키는_설정에서_읽고_나머지는_기본값을_쓴다() {
        assertThat(properties.serviceKey()).isEqualTo("from-env");
        assertThat(properties.baseUrl()).isEqualTo("https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade");
        assertThat(properties.pageSize()).isEqualTo(1000);
        assertThat(properties.maxPages()).isEqualTo(50);
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void 클라이언트가_스프링_빈으로_만들어진다() {
        assertThat(molitClient).isNotNull();
    }
}
