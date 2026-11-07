package com.gooodhandletruck.zippick.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** 주변 건물 거리 계산(하버사인) 확인 (#35 에서 추가) */
class NearbyBuildingServiceTest {

    @Test
    void 같은_점은_0m() {
        assertThat(NearbyBuildingService.distanceM(37.54, 126.945, 37.54, 126.945)).isZero();
    }

    @Test
    void 위도_0_001도는_약_111m() {
        assertThat(NearbyBuildingService.distanceM(37.54, 126.945, 37.541, 126.945)).isCloseTo(111.2, within(0.5));
    }

    @Test
    void 위도_경도가_같이_바뀌면_두_방향을_합친_거리() {
        // 위도 0.0116도(약 1290m) + 경도 0.0072도(위도 37.5에서 약 636m) → 피타고라스로 약 1438m
        double d = NearbyBuildingService.distanceM(37.5547, 126.9707, 37.5663, 126.9779);
        assertThat(d).isBetween(1400.0, 1500.0);
    }
}
