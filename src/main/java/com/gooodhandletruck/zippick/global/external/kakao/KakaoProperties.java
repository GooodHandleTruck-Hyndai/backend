package com.gooodhandletruck.zippick.global.external.kakao;


import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties (
    String restApiKey,
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout
    ) {
    public KakaoProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://dapi.kakao.com";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(3);
        }
    }
}
