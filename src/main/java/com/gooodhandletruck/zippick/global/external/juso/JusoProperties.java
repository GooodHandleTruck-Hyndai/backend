package com.gooodhandletruck.zippick.global.external.juso;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 행안부 주소정보 API 설정 (zippick.juso.*). 키는 환경 변수로만 넣어요.
 * 검색 API 와 좌표제공 API 는 승인키가 따로 나와요
 */
@ConfigurationProperties(prefix = "zippick.juso")
public record JusoProperties(
        String searchKey,
        String coordKey,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Fill fill
) {

    public JusoProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://business.juso.go.kr";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
        if (fill == null) {
            fill = new Fill(false, 100, 600_000L, 500L);
        }
    }

    /**
     * 건물 좌표 채우기 배치.
     *
     * @param enabled   켜면 서버가 주기적으로 좌표 없는 건물을 채움 (기본 꺼짐: 테스트·CI 에서 행안부를 안 부름)
     * @param batchSize 한 번에 채울 건물 수
     * @param delayMs   한 번 끝나고 다음 실행까지 쉬는 시간
     * @param pauseMs   건물 사이에 쉬는 시간. 건물 하나에 행안부를 2번(검색·좌표) 불러서, 안 쉬면 E0007(다량 요청)이 나요
     */
    public record Fill(boolean enabled, int batchSize, long delayMs, long pauseMs) {

        public Fill {
            if (batchSize <= 0) {
                batchSize = 100;
            }
            if (delayMs <= 0) {
                delayMs = 600_000L;
            }
            if (pauseMs <= 0) {
                pauseMs = 500L;
            }
        }
    }
}
