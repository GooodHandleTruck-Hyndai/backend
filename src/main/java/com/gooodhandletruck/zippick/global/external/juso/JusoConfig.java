package com.gooodhandletruck.zippick.global.external.juso;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** 행안부 주소정보 API 호출용 RestClient 설정 */
@Slf4j
@Configuration
@EnableConfigurationProperties(JusoProperties.class)
public class JusoConfig {

    @Bean
    public RestClient jusoRestClient(JusoProperties properties) {
        if (!StringUtils.hasText(properties.searchKey()) || !StringUtils.hasText(properties.coordKey())) {
            log.warn("JUSO_SEARCH_KEY / JUSO_COORD_KEY 가 비어 있음. 행안부 좌표 채우기는 실패함");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
