package com.gooodhandletruck.sidong;

import static org.assertj.core.api.Assertions.assertThat;

import com.gooodhandletruck.sidong.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 앱이 정상적으로 뜨는지 확인하는 기본 테스트 (H2 메모리 DB 사용) */
@SpringBootTest
class SidongApplicationTests {

    @Autowired
    private HealthController healthController;

    @Test
    void contextLoads() {
        assertThat(healthController).isNotNull();
    }

    @Test
    void healthReturnsOk() {
        assertThat(healthController.health()).containsEntry("status", "ok");
    }
}
