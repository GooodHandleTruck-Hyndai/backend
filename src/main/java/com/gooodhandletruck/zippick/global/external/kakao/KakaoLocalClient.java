package com.gooodhandletruck.zippick.global.external.kakao;

import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.external.kakao.dto.KakaoAddressResponse;
import com.gooodhandletruck.zippick.global.external.kakao.dto.KakaoRegionResponse;
import java.net.URI;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * 카카오 Local API 호출.
 * 응답 본문은 로그에 남기지 않고 상태 코드만 남김
 */
@Slf4j
@Component
public class KakaoLocalClient {

    private static final String COORD_TO_REGION_PATH = "/v2/local/geo/coord2regioncode.json";
    private static final String SEARCH_ADDRESS_PATH = "/v2/local/search/address.json";

    private final RestClient restClient;

    public KakaoLocalClient(@Qualifier("kakaoRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** 좌표 → 행정구역 목록. 카카오는 x = 경도, y = 위도 순서 */
    public List<KakaoRegionResponse.Document> coordToRegion(double lat, double lng) {
        KakaoRegionResponse response = get("coord2regioncode", uriBuilder -> uriBuilder
            .path(COORD_TO_REGION_PATH)
            .queryParam("x", lng)
            .queryParam("y", lat)
            .build(), KakaoRegionResponse.class);

        if (response == null || response.documents() == null) {
            return List.of();
        }
        return response.documents();
    }

    /** 주소 문자열 → 주소 목록 (좌표 포함). 정확히 일치하는 주소만 */
    public List<KakaoAddressResponse.Document> searchAddress(String query) {
        KakaoAddressResponse response = get("search/address", uriBuilder -> uriBuilder
            .path(SEARCH_ADDRESS_PATH)
            .queryParam("query", query)
            .queryParam("analyze_type", "exact")
            .queryParam("size", 10)
            .build(), KakaoAddressResponse.class);

        if (response == null || response.documents() == null) {
            return List.of();
        }
        return response.documents();
    }

    private <T> T get(String apiName, Function<UriBuilder, URI> uri, Class<T> responseType) {
        try {
            return restClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                    log.warn("Kakao {} 실패: status={}", apiName, httpResponse.getStatusCode().value());
                    throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
                })
                .body(responseType);
        } catch (ResourceAccessException e) {
            log.warn("Kakao {} 응답 없음: {}", apiName, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.EXTERNAL_API_TIMEOUT);
        } catch (RestClientException e) {
            log.warn("Kakao {} 처리 실패: {}", apiName, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }
}
