package com.gooodhandletruck.zippick.global.external.kakao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(KakaoProperties.class)
public class KakaoConfig {

    @Bean
    public RestClient kakaoRestClient(KakaoProperties properties){
        if(!StringUtils.hasText(properties.restApiKey())){
            log.warn("KAKAO_REST_API_KEY 가 비어 있음. 카카오 API 호출 실패");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
            .requestFactory(requestFactory)
            .build();
    }
}
