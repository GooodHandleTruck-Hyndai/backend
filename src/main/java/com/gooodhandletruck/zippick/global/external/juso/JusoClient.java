package com.gooodhandletruck.zippick.global.external.juso;

import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.external.juso.dto.JusoCommon;
import com.gooodhandletruck.zippick.global.external.juso.dto.JusoCoordResponse;
import com.gooodhandletruck.zippick.global.external.juso.dto.JusoSearchResponse;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * 행안부 주소정보 API 호출 (도로명주소 검색, 좌표제공).
 * 행안부 데이터는 공공데이터라 DB 에 저장해도 돼요 (카카오와 다름)
 */
@Slf4j
@Component
public class JusoClient {

    private static final String SEARCH_PATH = "/addrlink/addrLinkApi.do";
    private static final String COORD_PATH = "/addrlink/addrCoordApi.do";
    private static final String OK = "0";

    /**
     * 지금은 실패지만 나중에 다시 하면 될 수 있는 오류. 예외를 던져서 NONE 으로 저장하지 않게 해요 → 다음 배치에서 다시 시도.
     * E0001 승인되지 않은 키, -999 시스템 오류, E0007 다량 요청(너무 자주 부름).
     * 나머지(검색어 문제 등)는 "결과 없음"으로 봐요
     */
    private static final Set<String> RETRY_LATER_ERRORS = Set.of("E0001", "-999", "E0007");

    private final RestClient restClient;
    private final JusoProperties properties;

    public JusoClient(@Qualifier("jusoRestClient") RestClient restClient, JusoProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** 주소 검색 (최대 10건) */
    public List<JusoSearchResponse.Juso> search(String keyword) {
        requireKey(properties.searchKey(), "JUSO_SEARCH_KEY");
        JusoSearchResponse response = get("search", uriBuilder -> uriBuilder.path(SEARCH_PATH)
                .queryParam("confmKey", properties.searchKey())
                .queryParam("currentPage", 1)
                .queryParam("countPerPage", 10)
                .queryParam("keyword", keyword)
                .queryParam("resultType", "json")
                .build(), JusoSearchResponse.class);

        if (response == null || response.results() == null || !isOk(response.results().common(), "search")) {
            return List.of();
        }
        return response.results().juso() == null ? List.of() : response.results().juso();
    }

    /** 검색 결과 한 건의 좌표 {entX, entY} (UTM-K) */
    public Optional<double[]> coordinate(JusoSearchResponse.Juso juso) {
        requireKey(properties.coordKey(), "JUSO_COORD_KEY");
        JusoCoordResponse response = get("coord", uriBuilder -> uriBuilder.path(COORD_PATH)
                .queryParam("confmKey", properties.coordKey())
                .queryParam("admCd", juso.admCd())
                .queryParam("rnMgtSn", juso.rnMgtSn())
                .queryParam("udrtYn", juso.udrtYn())
                .queryParam("buldMnnm", juso.buldMnnm())
                .queryParam("buldSlno", juso.buldSlno())
                .queryParam("resultType", "json")
                .build(), JusoCoordResponse.class);

        if (response == null || response.results() == null || !isOk(response.results().common(), "coord")
                || response.results().juso() == null || response.results().juso().isEmpty()) {
            return Optional.empty();
        }
        JusoCoordResponse.Coord coord = response.results().juso().get(0);
        try {
            return Optional.of(new double[] {Double.parseDouble(coord.entX()), Double.parseDouble(coord.entY())});
        } catch (NumberFormatException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private boolean isOk(JusoCommon common, String apiName) {
        if (common == null || OK.equals(common.errorCode())) {
            return true;
        }
        if (RETRY_LATER_ERRORS.contains(common.errorCode())) {
            log.warn("Juso {} 실패: {} {}", apiName, common.errorCode(), common.errorMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
        log.debug("Juso {} 결과 없음: {} {}", apiName, common.errorCode(), common.errorMessage());
        return false;
    }

    private static void requireKey(String key, String envName) {
        if (!StringUtils.hasText(key)) {
            log.warn("{} 가 비어 있어서 행안부 API 를 부를 수 없음", envName);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    private <T> T get(String apiName, Function<UriBuilder, URI> uri, Class<T> responseType) {
        try {
            return restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        log.warn("Juso {} 실패: status={}", apiName, httpResponse.getStatusCode().value());
                        throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
                    })
                    .body(responseType);
        } catch (ResourceAccessException e) {
            log.warn("Juso {} 응답 없음: {}", apiName, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.EXTERNAL_API_TIMEOUT);
        } catch (RestClientException e) {
            log.warn("Juso {} 처리 실패: {}", apiName, e.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }
}
