package com.gooodhandletruck.zippick.global.external.kakao;

import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.external.kakao.dto.KakaoRegionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Component
public class KakaoLocalClient {

    private static final String COORD_TO_REGION_PATH = "/v2/local/geo/coord2regioncode.json";

    private final RestClient restClient;

    public KakaoLocalClient(@Qualifier("kakaoRestClient") RestClient restClient){
        this.restClient = restClient;
    }

//    좌표 -> 행정구역 목록.
//    카카오는 x = 경도, y = 위도 순서
public List<KakaoRegionResponse.Document> coordToRegion(double lat, double lng) {
    try {
        KakaoRegionResponse response = restClient.get()
            .uri(uriBuilder -> uriBuilder.path(COORD_TO_REGION_PATH)
                .queryParam("x", lng)
                .queryParam("y", lat)
                .build())
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                log.warn("Kakao coord2regioncode 실패: status={}", httpResponse.getStatusCode().value());
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            })
            .body(KakaoRegionResponse.class);

        if (response == null || response.documents() == null) {
            return List.of();
        }
        return response.documents();
    } catch (ResourceAccessException e) {
        log.warn("Kakao coord2regioncode 응답 없음: {}", e.getClass().getSimpleName());
        throw new BusinessException(ErrorCode.EXTERNAL_API_TIMEOUT);
    } catch (RestClientException e) {
        log.warn("Kakao coord2regioncode 처리 실패: {}", e.getClass().getSimpleName());
        throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
    }
}
}
