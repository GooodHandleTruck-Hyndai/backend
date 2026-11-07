package com.gooodhandletruck.zippick.global.external.kakao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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

//    건물 여러 개 좌표를 동시에  찾을 때 쓰는 스레드 풀
    @Bean
    public ThreadPoolTaskExecutor kakaoExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("kakao-");
        return executor;
    }
}
