package com.gooodhandletruck.zippick.domain.building.service;

import com.gooodhandletruck.zippick.domain.building.dto.BuildingAddress;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingCoordinateResponse;
import com.gooodhandletruck.zippick.domain.building.dto.NearbyBuildingsResponse;
import com.gooodhandletruck.zippick.domain.building.dto.NearbyBuildingsResponse.LatestDeal;
import com.gooodhandletruck.zippick.domain.building.dto.NearbyBuildingsResponse.NearbyBuilding;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.domain.trade.dto.TransactionResponse;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.util.KstClock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주변 건물 (API 2번). (#35 에서 추가)
 *
 * 흐름: 입력 검사 → 후보 건물(DB 전체) → 좌표 → 거리 계산·반경 필터·가까운 순 → 최근 1년 거래 1건(latestDeal).
 *
 * 좌표: 행안부 좌표(coordSource=JUSO)가 DB 에 있으면 그걸 쓰고, 없으면 #13 좌표 서비스(카카오 실시간)로 찾아요.
 * 카카오 좌표는 정책상 저장할 수 없어서 요청마다 다시 찾고, 결과는 응답에만 써요.
 * 후보를 "그 위치의 구"로 좁히지 않는 이유: 구 경계 근처에서 반경(최대 3km)이 옆 구로 넘어가면 그쪽 건물을 놓쳐요.
 * 지금은 시연 지역(마포) 건물만 DB 에 있어서 전체를 봐도 346개.
 * 카카오는 몰아 부르면 초당 한도로 막아서, 30개씩 쉬어 가며 부르고 못 찾은 건물은 한 번 더 찾아요 (임시 조치, 아래 chunkPauseMs).
 * #45 변경: 좌표 조회에서 예상 못한 예외가 나면 그 덩어리만 못 찾음으로 처리 (상황별 테스트에서 500 발견).
 * 지역을 늘리면 행안부 좌표를 DB 에 채워서 이 서비스 안쪽만 바꾸면 돼요 (API 모양은 그대로).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NearbyBuildingService {

    /** 명세: radius 기본 1000, 100~3000 (임시, 미정 사항) */
    static final int DEFAULT_RADIUS_M = 1000;
    static final int MIN_RADIUS_M = 100;
    static final int MAX_RADIUS_M = 3000;

    /** 명세: latestDeal 은 최근 1년 거래 */
    static final int LATEST_DEAL_MONTHS = 12;

    /**
     * 좌표 서비스에 한 번에 넘기는 건물 수. #13 카카오 스레드 풀이 큐 200 이라 한꺼번에 넘기면 거부될 수 있어서,
     * #13 API 와 같은 30개씩 나눠요 (30개 1회 실측 0.3초)
     */
    static final int COORDINATE_CHUNK = 30;

    private static final double EARTH_RADIUS_M = 6_371_000;

    /**
     * 덩어리(30개) 사이에 쉬는 시간(ms). 카카오는 짧은 시간에 몰아 부르면 "API limit has been exceeded"(400, code -10)로
     * 막아요 — 346개를 쉬지 않고 부르면(약 1.4초) 매번 100~200개가 막혀서 건물이 랜덤하게 빠졌어요 (11/08 실측).
     * 실측(마포 346개, 반경 3000m 5회씩): 300ms → 매번 192개·5~6초, 150ms → 매번 192개·3.3초, 둘 다 한도 오류 0건.
     * 행안부 좌표를 DB 에 채우면 필요 없어지는 임시 조치예요. 설정으로 바꿀 수 있게 열어 둠 (테스트는 0)
     */
    @Value("${zippick.nearby.kakao-chunk-pause-ms:150}")
    private long chunkPauseMs;

    /** 첫 시도에서 못 찾은 건물을 한 번 더 찾기 전에 쉬는 시간(ms) — 한도에 막혔던 건물이 대부분이라 잠깐 쉬고 다시 불러요 */
    @Value("${zippick.nearby.kakao-retry-pause-ms:1000}")
    private long retryPauseMs;

    private final BuildingRepository buildingRepository;
    private final TradeRepository tradeRepository;
    private final LegalDongRepository legalDongRepository;
    private final BuildingCoordinateService buildingCoordinateService;
    private final BuildingService buildingService;

    public NearbyBuildingsResponse findNearby(double lat, double lon, Integer radius) {
        return findNearby(lat, lon, radius, KstClock.today());
    }

    /** 테스트에서 오늘 날짜(최근 1년 기준)를 고정하려고 나눠 둬요 */
    NearbyBuildingsResponse findNearby(double lat, double lon, Integer radius, LocalDate today) {
        int radiusM = validate(lat, lon, radius);

        List<Building> candidates = buildingRepository.findAll();
        Map<Long, double[]> coords = coordinates(candidates);
        // 후보는 있는데 좌표를 하나도 못 찾았으면 카카오 호출 자체가 실패한 거예요 (명세: E001)
        if (!candidates.isEmpty() && coords.isEmpty()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }

        // 반경 안만 남기고 가까운 순 (같은 거리면 id 순으로 고정)
        List<Building> inRadius = new ArrayList<>();
        Map<Long, Integer> distances = new HashMap<>();
        for (Building building : candidates) {
            double[] point = coords.get(building.getId());
            if (point == null) {
                continue; // 좌표를 못 찾은 건물은 지도에 못 찍으니 빼요
            }
            int distance = (int) Math.round(distanceM(lat, lon, point[0], point[1]));
            if (distance <= radiusM) {
                inRadius.add(building);
                distances.put(building.getId(), distance);
            }
        }
        inRadius.sort(Comparator.comparingInt((Building b) -> distances.get(b.getId())).thenComparing(Building::getId));

        Map<Long, LatestDeal> latestDeals = latestDeals(inRadius, today.minusMonths(LATEST_DEAL_MONTHS));
        Map<String, LegalDong> regions = new HashMap<>();
        List<NearbyBuilding> buildings = inRadius.stream()
                .map(b -> new NearbyBuilding(
                        b.getId(),
                        b.getAptNm(),
                        b.getBuildingType().name(),
                        JibunAddressFormatter.format(region(b, regions), b.getUmdNm(), b.getJibun()),
                        coords.get(b.getId())[0],
                        coords.get(b.getId())[1],
                        distances.get(b.getId()),
                        null, // 건물 사진 출처 미정 — 명세대로 null
                        latestDeals.get(b.getId())))
                .toList();

        return new NearbyBuildingsResponse(dataBaseDate(inRadius), buildings);
    }

    /** 명세 에러: lat·lon 범위 밖 → C001. radius 범위 밖도 C001. 비어 있으면(필수 누락) 팀 공통 처리기가 C001 */
    private int validate(double lat, double lon, Integer radius) {
        if (lat < -90 || lat > 90 || Double.isNaN(lat)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "lat 는 -90~90 이어야 해요: " + lat);
        }
        if (lon < -180 || lon > 180 || Double.isNaN(lon)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "lon 은 -180~180 이어야 해요: " + lon);
        }
        int radiusM = radius == null ? DEFAULT_RADIUS_M : radius;
        if (radiusM < MIN_RADIUS_M || radiusM > MAX_RADIUS_M) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "radius 는 " + MIN_RADIUS_M + "~" + MAX_RADIUS_M + " 이어야 해요: " + radiusM);
        }
        return radiusM;
    }

    /** 건물 id → {위도, 경도}. 행안부 좌표(JUSO)는 DB 값, 나머지는 카카오 실시간. 못 찾은 건물은 map 에 없어요 */
    private Map<Long, double[]> coordinates(List<Building> candidates) {
        Map<Long, double[]> coords = new HashMap<>();
        List<BuildingAddress> needLookup = new ArrayList<>();
        for (Building b : candidates) {
            // JUSO 만 믿어요 — 출처가 비어 있는 좌표(예전 샘플 데이터 등)는 근거가 없어서 다시 찾아요
            if (b.getCoordSource() == CoordSource.JUSO && b.getLatitude() != null && b.getLongitude() != null) {
                coords.put(b.getId(), new double[] {b.getLatitude(), b.getLongitude()});
            } else {
                // 국토부 일반 실거래 자료엔 도로명이 없어서 지번(동 + 지번)으로만 찾아요 (#13 서비스가 도로명 없으면 지번으로 감)
                needLookup.add(new BuildingAddress(String.valueOf(b.getId()), b.getSggCd(), null, null, null,
                        b.getUmdNm(), b.getJibun()));
            }
        }
        lookup(needLookup, coords);

        // 카카오 한도에 막힌 건물은 #13 서비스에서 "못 찾음"과 구분이 안 돼서, 못 찾은 건물 전부를 잠깐 쉬고 한 번만 다시 찾아요.
        // 정말 주소가 없는 건물은 두 번째도 못 찾고 빠져요 (두 번까지만 — 응답이 너무 늦어지지 않게)
        List<BuildingAddress> missed = needLookup.stream()
                .filter(a -> !coords.containsKey(Long.valueOf(a.buildingId())))
                .toList();
        if (!missed.isEmpty()) {
            pause(retryPauseMs);
            lookup(missed, coords);
        }
        return coords;
    }

    /** 30개씩 나눠 좌표를 찾고, 덩어리 사이에 쉬어요 (카카오 초당 한도 대비). 찾은 것만 coords 에 넣어요 */
    private void lookup(List<BuildingAddress> addresses, Map<Long, double[]> coords) {
        for (int from = 0; from < addresses.size(); from += COORDINATE_CHUNK) {
            if (from > 0) {
                pause(chunkPauseMs);
            }
            List<BuildingAddress> chunk = addresses.subList(from, Math.min(from + COORDINATE_CHUNK, addresses.size()));
            List<BuildingCoordinateResponse> results;
            try {
                results = buildingCoordinateService.findCoordinates(chunk);
            } catch (RuntimeException e) {
                // #45: 좌표 서비스의 스레드 풀이 가득 차서 거부되는 등 예상 못한 예외는 그 덩어리만 "못 찾음"으로 처리.
                // 못 찾은 건물은 재시도에서 한 번 더 찾고, 끝까지 하나도 못 찾으면 E001 이에요 (findNearby)
                log.warn("좌표 덩어리 조회 실패({}개), 다음 덩어리로 진행: {}", chunk.size(), e.getClass().getSimpleName());
                continue;
            }
            for (BuildingCoordinateResponse found : results) {
                if (found.found() && found.lat() != null && found.lon() != null) {
                    coords.put(Long.valueOf(found.buildingId()), new double[] {found.lat(), found.lon()});
                }
            }
        }
    }

    private static void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 끊겨도 지금까지 찾은 좌표로 응답해요
        }
    }

    /** 건물별 latestDeal. 쿼리는 한 번, 정렬이 최신순이라 건물별 첫 건만 남겨요 */
    private Map<Long, LatestDeal> latestDeals(List<Building> buildings, LocalDate since) {
        if (buildings.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = buildings.stream().map(Building::getId).toList();
        Map<Long, LatestDeal> result = new LinkedHashMap<>();
        for (Trade trade : tradeRepository
                .findByBuildingIdInAndCdealTypeIsNullAndDealDateGreaterThanEqualOrderByDealDateDescIdDesc(ids, since)) {
            // 면적·평 계산은 건물 상세와 같은 규칙을 쓰려고 TransactionResponse 를 거쳐요
            result.computeIfAbsent(trade.getBuilding().getId(), id -> {
                TransactionResponse t = TransactionResponse.from(trade);
                return new LatestDeal(t.price(), t.dealDate(), t.areaM2(), t.areaPyeong());
            });
        }
        return result;
    }

    /**
     * 자료 기준일. 목록에 나온 구마다 건물 상세와 같은 방식(#19)으로 구하고, 가장 오래된 날짜를 써요 —
     * 카드에 "이 날짜까지의 자료"라고 보여 주는 값이라 더 최신인 구 날짜를 쓰면 과장이 돼요. 목록이 비면 null.
     */
    private LocalDate dataBaseDate(List<Building> buildings) {
        return buildings.stream()
                .map(Building::getSggCd)
                .distinct()
                .map(buildingService::dataBaseDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /** 건물 상세와 같은 주소 규칙: 매칭된 법정동, 없으면 구 코드로 시도·시군구 이름 (구마다 한 번만 조회) */
    private LegalDong region(Building building, Map<String, LegalDong> cache) {
        if (building.getLegalDong() != null) {
            return building.getLegalDong();
        }
        return cache.computeIfAbsent(building.getSggCd(),
                sgg -> legalDongRepository.findFirstBySggCd(sgg).orElse(null));
    }

    /** 두 위경도 사이 거리(m). 하버사인 공식 — 수 km 안에서는 오차가 거의 없어요 */
    static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }
}
