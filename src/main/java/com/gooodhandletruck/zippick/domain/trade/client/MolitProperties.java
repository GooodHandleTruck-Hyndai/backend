package com.gooodhandletruck.zippick.domain.trade.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 국토부 실거래 API 설정 (application.yml 의 zippick.molit). (#7 에서 추가)
 *
 * @param serviceKey     공공데이터포털 일반 인증키(Decoding). 환경 변수 MOLIT_SERVICE_KEY 에서 읽어요
 * @param baseUrl        API 공통 주소. 아파트·오피스텔 경로는 MolitDataset 이 붙여요 (#27 에서 공통 주소로 바뀜)
 * @param pageSize       한 번에 받을 건수 (최대 1000)
 * @param maxPages       페이지를 끝없이 돌지 않게 막는 상한
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout    응답 제한 시간
 */
@ConfigurationProperties("zippick.molit")
public record MolitProperties(
        String serviceKey,
        @DefaultValue("https://apis.data.go.kr/1613000") String baseUrl,
        @DefaultValue("1000") int pageSize,
        @DefaultValue("50") int maxPages,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout) {
}
