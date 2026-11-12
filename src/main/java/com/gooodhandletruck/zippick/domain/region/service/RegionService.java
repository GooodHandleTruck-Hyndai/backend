package com.gooodhandletruck.zippick.domain.region.service;

import com.gooodhandletruck.zippick.domain.region.dto.RegionResponse;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.external.kakao.KakaoAvailability;
import com.gooodhandletruck.zippick.global.external.kakao.KakaoLocalClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 위치 → 지역(구 코드). 카카오 좌표→행정구역 API 사용 (#11 에서 추가)
 * #55 변경: 카카오 키 없으면(로컬 e2e) RegionFallbackService 로 DB 에서 대신 조회. 키 있으면 동작 그대로
 */
@Service
@RequiredArgsConstructor
public class RegionService {

    /** 실거래가 API 는 법정동 기준이라 B 만 사용 */
    private static final String LEGAL_DONG = "B";

    private final KakaoLocalClient kakaoLocalClient;
    private final KakaoAvailability kakaoAvailability;       // #55
    private final RegionFallbackService regionFallbackService; // #55

    public RegionResponse findByCoordinate(double lat, double lon) {
        // #55: 카카오 키 없을 때(로컬 e2e)만 DB 로 대신 조회. 키 있으면 아래 카카오 방식 그대로
        if (!kakaoAvailability.enabled()) {
            return regionFallbackService.findByCoordinate(lat, lon);
        }
        return kakaoLocalClient.coordToRegion(lat, lon).stream()
            .filter(document -> LEGAL_DONG.equals(document.regionType()))
            .filter(document -> document.code() != null && document.code().length() >= 5)
            .findFirst()
            .map(RegionResponse::from)
            .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));
    }
}
