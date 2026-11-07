package com.gooodhandletruck.zippick.domain.region.service;

import com.gooodhandletruck.zippick.domain.region.dto.RegionResponse;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.external.kakao.KakaoLocalClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegionService {

    /** 실거래가 API 는 법정동 기준이라 B 만 사용 */
    private static final String LEGAL_DONG = "B";

    private final KakaoLocalClient kakaoLocalClient;

    public RegionResponse findByCoordinate(double lat, double lng) {
        return kakaoLocalClient.coordToRegion(lat, lng).stream()
            .filter(document -> LEGAL_DONG.equals(document.regionType()))
            .filter(document -> document.code() != null && document.code().length() >= 5)
            .findFirst()
            .map(RegionResponse::from)
            .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));
    }
}
