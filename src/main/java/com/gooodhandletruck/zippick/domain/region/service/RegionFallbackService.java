package com.gooodhandletruck.zippick.domain.region.service;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.building.service.NearbyBuildingService;
import com.gooodhandletruck.zippick.domain.region.dto.RegionResponse;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오 키 없을 때 위치 → 지역(구·동) DB 대체 조회 (#55 에서 추가)
 *
 * - 로컬 e2e 용. 행안부 좌표(JUSO)가 저장된 건물 중 가장 가까운 건물의 구·동 반환
 * - 시연 데이터가 마포구 하나라 구 코드(lawdCd) 정확. 앱은 lawdCd·addressName 만 사용 (frontend RegionDto)
 * - 가장 가까운 건물도 MAX_DISTANCE_M 보다 멀면 데이터 없는 지역 → 카카오와 같은 R001
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionFallbackService {

    /** 이보다 멀면 "지역 정보 없음". 마포구 끝에서 끝 약 8km → 여유 있게 10km */
    static final double MAX_DISTANCE_M = 10_000;

    private final BuildingRepository buildingRepository;
    private final LegalDongRepository legalDongRepository;

    public RegionResponse findByCoordinate(double lat, double lon) {
        Building nearest = buildingRepository
                .findByCoordSourceAndLatitudeIsNotNullAndLongitudeIsNotNull(CoordSource.JUSO).stream()
                .min(Comparator.comparingDouble(
                        (Building b) -> NearbyBuildingService.distanceM(lat, lon, b.getLatitude(), b.getLongitude()))
                        .thenComparing(Building::getId))
                .filter(b -> NearbyBuildingService.distanceM(lat, lon, b.getLatitude(), b.getLongitude())
                        <= MAX_DISTANCE_M)
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));

        LegalDong dong = nearest.getLegalDong();
        LegalDong sgg = dong != null ? dong : legalDongRepository.findFirstBySggCd(nearest.getSggCd()).orElse(null);
        String sidoName = sgg == null ? null : sgg.getSidoNm();
        String sigunguName = sgg == null ? null : sgg.getSggNm();
        String addressName = Stream.of(sidoName, sigunguName, nearest.getUmdNm())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));

        return new RegionResponse(
                nearest.getSggCd(),
                dong == null ? null : dong.getAdmCd(), // 법정동 코드는 행안부로 매칭된 건물만 있음
                sidoName,
                sigunguName,
                nearest.getUmdNm(),
                addressName);
    }
}
