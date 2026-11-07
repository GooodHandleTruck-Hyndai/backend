package com.gooodhandletruck.zippick.domain.building.service;

import com.gooodhandletruck.zippick.domain.building.dto.BuildingAddress;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingCoordinateResponse;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.external.kakao.KakaoLocalClient;
import com.gooodhandletruck.zippick.global.external.kakao.dto.KakaoAddressResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 실거래가 건물 → 좌표.
 * 카카오 주소 검색을 실시간으로 호출하고 결과는 저장하지 않음 (카카오 정책)
 *
 * 1) 도로명 주소로 검색  2) 없으면 지번 주소로 검색
 * 검색 결과 중 법정동 코드가 sggCd 로 시작하는 것만 인정 (다른 지역 같은 도로명 방지)
 */
@Slf4j
@Service
public class BuildingCoordinateService {

    private final KakaoLocalClient kakaoLocalClient;
    private final Executor kakaoExecutor;

    public BuildingCoordinateService(KakaoLocalClient kakaoLocalClient,
                                     @Qualifier("kakaoExecutor") Executor kakaoExecutor) {
        this.kakaoLocalClient = kakaoLocalClient;
        this.kakaoExecutor = kakaoExecutor;
    }

    /** 여러 건물을 동시에 조회. 요청 순서 그대로 반환, 실패한 건물은 found = false */
    public List<BuildingCoordinateResponse> findCoordinates(List<BuildingAddress> buildings) {
        List<CompletableFuture<BuildingCoordinateResponse>> futures = buildings.stream()
            .map(building -> CompletableFuture.supplyAsync(() -> findCoordinate(building), kakaoExecutor))
            .toList();
        return futures.stream()
            .map(CompletableFuture::join)
            .toList();
    }

    /** 건물 하나. 카카오 호출이 실패해도 예외 대신 found = false */
    public BuildingCoordinateResponse findCoordinate(BuildingAddress building) {
        try {
            return searchByRoad(building)
                .or(() -> searchByJibun(building))
                .map(document -> toResponse(building.buildingId(), document))
                .orElseGet(() -> BuildingCoordinateResponse.notFound(building.buildingId()));
        } catch (BusinessException e) {
            return BuildingCoordinateResponse.notFound(building.buildingId());
        }
    }

    private Optional<KakaoAddressResponse.Document> searchByRoad(BuildingAddress building) {
        String bonbun = stripZeros(building.roadNmBonbun());
        if (!StringUtils.hasText(building.roadNm()) || bonbun.isEmpty()) {
            return Optional.empty();
        }
        String bubun = stripZeros(building.roadNmBubun());
        String query = building.roadNm().trim() + " " + bonbun + (bubun.isEmpty() ? "" : "-" + bubun);
        return search(query, building.sggCd());
    }

    private Optional<KakaoAddressResponse.Document> searchByJibun(BuildingAddress building) {
        if (!StringUtils.hasText(building.umdNm()) || !StringUtils.hasText(building.jibun())) {
            return Optional.empty();
        }
        return search(building.umdNm().trim() + " " + building.jibun().trim(), building.sggCd());
    }

    private Optional<KakaoAddressResponse.Document> search(String query, String sggCd) {
        return kakaoLocalClient.searchAddress(query).stream()
            .filter(document -> document.legalDongCode() != null && document.legalDongCode().startsWith(sggCd))
            .findFirst();
    }

    private BuildingCoordinateResponse toResponse(String buildingId, KakaoAddressResponse.Document document) {
        try {
            return BuildingCoordinateResponse.found(buildingId,
                Double.parseDouble(document.y()), Double.parseDouble(document.x()));
        } catch (NumberFormatException | NullPointerException e) {
            return BuildingCoordinateResponse.notFound(buildingId);
        }
    }

    /** "00427" → "427", "00000" 이나 빈 값 → "" */
    static String stripZeros(String number) {
        if (!StringUtils.hasText(number)) {
            return "";
        }
        String stripped = number.trim().replaceFirst("^0+", "");
        return stripped.matches("\\d+") ? stripped : "";
    }
}
