package com.gooodhandletruck.zippick.domain.building.service;

import com.gooodhandletruck.zippick.domain.building.dto.BuildingAddress;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingCoordinateResponse;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingIdentifyResponse;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingIdentifyResponse.Candidate;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingIdentifyResponse.LatestDeal;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.favorite.repository.FavoriteRepository;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.domain.trade.dto.TransactionResponse;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.util.KstClock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앞쪽 건물 찾기 (API 20번, GET /api/v1/buildings/identify). (#50 에서 추가)
 *
 * 주행 중 [주변 단지 듣기](S-08) 버튼에서 불러요. 차 위치·진행 방향(heading)으로 앞쪽 건물을 가까운 순 최대 3개 돌려줘요.
 *
 * 규칙은 프론트(frontend PR #8)가 지금 앱에서 하는 계산과 똑같이 맞췄어요 — 서버로 바꿔 끼워도 결과가 같아야 해서:
 *   - 범위: 반경 1000m(반올림 전 거리), 진행 방향 좌우 90°(경계 포함)  ← FakeRepository.nearestAhead / isAhead
 *   - 거리: 하버사인, 방위각: 대권 초기 방위각(북 0°, 시계 방향)   ← Geo.distanceMeters / bearingDegrees
 *   - 8방향: 45° 칸, 정면 FRONT = ±22.5°                          ← RelativeDirection.of
 *   - 가격 말: "12억 3,000만 원", 면적 말: "84제곱미터"            ← PriceFormat.formatPriceVoice / UnitFormat.formatAreaVoice
 * 노션 명세 제안(좌우 60°, 300m)과는 달라요 → 노션 명세를 프론트 기준으로 고칠 예정.
 *
 * 좌표: 행안부 좌표(coordSource=JUSO)는 DB 값을 쓰고, 행안부가 못 찾은 건물(NONE·출처 없음)만 카카오로 실시간 조회해요.
 *   주변 건물 API(NearbyBuildingService.coordinates)와 같은 규칙이라, 앱이 주변 목록에서 고르던 결과와 같아져요.
 *   (#50 후속 변경: 처음엔 JUSO 만 봤는데, 카카오로 찾은 건물이 앱에는 나오고 서버에는 안 나와 150번 중 8번 첫 후보가 달랐어요)
 *   카카오 좌표는 정책상 저장하지 않아서 매 요청 불러요. 11/12 재채우기 후 마포에서 카카오로 찾는 건물은 7개뿐이라 비용이 작아요.
 *   주행 중 응답이라 주변 건물과 달리 "못 찾으면 쉬고 한 번 더"는 안 해요 — 못 찾은 건물은 이번 응답에서만 빠져요.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingIdentifyService {

    /** 프론트 RemoteRepository.BRIEF_RADIUS_METERS 와 같아요 */
    static final int RADIUS_M = 1000;
    /** 진행 방향 좌우 이만큼(°)까지 "앞쪽". 프론트 isAhead 의 QUARTER_TURN */
    static final double HALF_ANGLE = 90.0;
    /** 명세: 최대 3개 */
    static final int MAX_CANDIDATES = 3;
    /** 주변 건물과 같은 "최근 1년" */
    static final int LATEST_DEAL_MONTHS = 12;

    static final String NONE_SPEECH = "주변에서 거래된 건물을 찾지 못했어요.";

    private static final double FULL_TURN = 360.0;
    /** 8방향 한 칸 (프론트 RelativeDirection.SECTOR) */
    private static final double SECTOR = 45.0;
    /** 위도 1도의 최소 길이(m, 적도). 사각형을 조금 넉넉히 잡으려고 작은 값을 써요 */
    private static final double METERS_PER_LAT_DEGREE = 110_000.0;

    /** 8방향 순서는 프론트 RelativeDirection.entries 와 같아요 (index = 칸 번호) */
    private static final String[] DIRECTIONS =
            {"FRONT", "FRONT_RIGHT", "RIGHT", "BACK_RIGHT", "BACK", "BACK_LEFT", "LEFT", "FRONT_LEFT"};
    /** 프론트 strings.xml direction_* 와 같은 말 */
    private static final Map<String, String> DIRECTION_WORDS = Map.of(
            "FRONT", "앞", "FRONT_RIGHT", "오른쪽 앞", "RIGHT", "오른쪽", "BACK_RIGHT", "오른쪽 뒤",
            "BACK", "뒤", "BACK_LEFT", "왼쪽 뒤", "LEFT", "왼쪽", "FRONT_LEFT", "왼쪽 앞");

    private final BuildingRepository buildingRepository;
    private final TradeRepository tradeRepository;
    private final LegalDongRepository legalDongRepository;
    private final FavoriteRepository favoriteRepository; // saved 계산 (팀원 저장 기능의 조회만 사용, #42 와 같은 방식)
    private final BuildingService buildingService; // 자료 기준일 규칙(#19)을 그대로 쓰려고
    private final BuildingCoordinateService buildingCoordinateService; // #50 후속: 행안부 좌표 없는 건물만 카카오로 (#13 서비스)

    public BuildingIdentifyResponse identify(CurrentDevice device, double lat, double lon, double heading) {
        return identify(device, lat, lon, heading, KstClock.today());
    }

    /** 테스트에서 오늘 날짜(최근 1년 기준)를 고정하려고 나눠 둬요 */
    BuildingIdentifyResponse identify(CurrentDevice device, double lat, double lon, double heading, LocalDate today) {
        double carHeading = validate(lat, lon, heading);

        // 1) 행안부 좌표: DB 에서 반경을 감싸는 사각형 안 건물만 (인덱스)
        //    + 행안부 좌표 없는 건물: 카카오로 좌표를 찾아서 (#50 후속)  → 2) 원·앞쪽으로 정확히 거르기
        List<Located> located = new ArrayList<>();
        for (Building b : buildingsInBox(lat, lon)) {
            located.add(locate(b, b.getLatitude(), b.getLongitude(), lat, lon, carHeading));
        }
        located.addAll(locateByKakao(lat, lon, carHeading));

        List<Located> ahead = located.stream()
                .filter(l -> l.distance() <= RADIUS_M && isAhead(l.relative()))
                // 가까운 순, 같은 거리면 id 순 (프론트는 주변 목록 순서 = 거리·id 순에서 가장 가까운 것)
                .sorted(Comparator.comparingDouble(Located::distance).thenComparing(l -> l.building().getId()))
                .limit(MAX_CANDIDATES)
                .toList();

        List<Building> buildings = ahead.stream().map(Located::building).toList();
        Map<Long, LatestDeal> deals = latestDeals(buildings, today.minusMonths(LATEST_DEAL_MONTHS));
        Set<Long> savedIds = new HashSet<>(favoriteRepository.findBuildingIdsByDeviceId(device.deviceId()));
        Map<String, LegalDong> regions = new HashMap<>();

        List<Candidate> candidates = ahead.stream()
                .map(l -> {
                    Building b = l.building();
                    return new Candidate(
                            b.getId(),
                            b.getAptNm(),
                            b.getBuildingType().name(),
                            JibunAddressFormatter.format(region(b, regions), b.getUmdNm(), b.getJibun()),
                            l.lat(), // #50 후속: 카카오로 찾은 건물은 DB 에 좌표가 없어서 계산에 쓴 좌표를 그대로
                            l.lon(),
                            (int) Math.round(l.distance()),
                            null, // 건물 사진 출처 미정 — 주변 건물과 같이 null
                            deals.get(b.getId()),
                            side(l.relative()),
                            direction(l.relative()),
                            savedIds.contains(b.getId()));
                })
                .toList();

        return new BuildingIdentifyResponse(dataBaseDate(buildings), candidates, speech(candidates));
    }

    /** 입력 검사 → C001. heading 은 0 이상 360 이하만 받고 360 은 0(북쪽)으로 봐요. 돌려주는 값은 0 이상 360 미만 */
    private static double validate(double lat, double lon, double heading) {
        if (Double.isNaN(lat) || lat < -90 || lat > 90) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "lat 는 -90~90 이어야 해요: " + lat);
        }
        if (Double.isNaN(lon) || lon < -180 || lon > 180) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "lon 은 -180~180 이어야 해요: " + lon);
        }
        // 명세는 0~359. 차 센서가 360 을 줄 수 있어서 360 은 받아 주고, 음수·360 초과·NaN 은 잘못된 값으로 봐요
        if (Double.isNaN(heading) || heading < 0 || heading > FULL_TURN) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "heading 은 0~359 이어야 해요: " + heading);
        }
        return heading % FULL_TURN;
    }

    /** 반경을 감싸는 위·경도 사각형 안의 JUSO 좌표 건물. 경도 1도 길이는 위도에 따라 줄어서 cos(위도)로 나눠요 */
    private List<Building> buildingsInBox(double lat, double lon) {
        double dLat = RADIUS_M / METERS_PER_LAT_DEGREE;
        double cos = Math.cos(Math.toRadians(lat));
        // 극지방(cos≈0)은 경도 폭이 끝없이 커져서 경도 조건을 풀어요 (서비스 지역과는 무관한 경계 처리)
        double dLon = cos < 1e-6 ? FULL_TURN : dLat / cos;
        return buildingRepository.findWithCoordinateIn(CoordSource.JUSO,
                lat - dLat, lat + dLat, lon - dLon, lon + dLon);
    }

    /**
     * #50 후속: 행안부 좌표가 없는 건물(NONE·출처 없음·좌표 비어 있음)을 카카오로 찾아 위치를 계산해요.
     * 주변 건물 API 와 같은 주소(구 코드 + 동 + 지번)·같은 30개 덩어리. 실패한 덩어리·못 찾은 건물은 빼고 계속해요
     * (주행 중 안내라 카카오가 막혀도 행안부 좌표 건물로는 응답해야 해서 E001 을 던지지 않아요).
     */
    private List<Located> locateByKakao(double lat, double lon, double heading) {
        Map<Long, Building> byId = new HashMap<>();
        List<BuildingAddress> addresses = new ArrayList<>();
        for (Building b : buildingRepository.findWithoutJusoCoordinate(CoordSource.JUSO)) {
            byId.put(b.getId(), b);
            addresses.add(new BuildingAddress(String.valueOf(b.getId()), b.getSggCd(), null, null, null,
                    b.getUmdNm(), b.getJibun()));
        }
        List<Located> result = new ArrayList<>();
        for (int from = 0; from < addresses.size(); from += NearbyBuildingService.COORDINATE_CHUNK) {
            List<BuildingAddress> chunk = addresses.subList(from,
                    Math.min(from + NearbyBuildingService.COORDINATE_CHUNK, addresses.size()));
            List<BuildingCoordinateResponse> found;
            try {
                found = buildingCoordinateService.findCoordinates(chunk);
            } catch (RuntimeException e) {
                log.warn("identify 카카오 좌표 조회 실패({}개), 행안부 좌표 건물로만 응답: {}", chunk.size(),
                        e.getClass().getSimpleName());
                continue;
            }
            for (BuildingCoordinateResponse r : found) {
                if (r.found() && r.lat() != null && r.lon() != null) {
                    result.add(locate(byId.get(Long.valueOf(r.buildingId())), r.lat(), r.lon(), lat, lon, heading));
                }
            }
        }
        return result;
    }

    /** 건물 하나(좌표 pLat, pLon)의 거리(m, 반올림 전) 와 진행 방향 기준 상대 각도(0 이상 360 미만, 시계 방향) */
    private static Located locate(Building b, double pLat, double pLon, double lat, double lon, double heading) {
        double distance = NearbyBuildingService.distanceM(lat, lon, pLat, pLon);
        double bearing = bearingDegrees(lat, lon, pLat, pLon);
        return new Located(b, pLat, pLon, distance, relative(bearing, heading));
    }

    /** 프론트 Geo.bearingDegrees 와 같은 식: (lat1, lon1) 에서 (lat2, lon2) 를 바라본 방위각, 북 0°·시계 방향, 0 이상 360 미만 */
    static double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + FULL_TURN) % FULL_TURN;
    }

    /** 차가 향한 쪽을 0° 로 본 건물 방향 (시계 방향, 0 이상 360 미만). 프론트 isAhead·RelativeDirection 과 같은 식 */
    static double relative(double bearing, double heading) {
        return ((bearing - heading) % FULL_TURN + FULL_TURN) % FULL_TURN;
    }

    /** 프론트 isAhead: 좌우 90° 안(경계 포함) */
    static boolean isAhead(double relative) {
        return relative <= HALF_ANGLE || relative >= FULL_TURN - HALF_ANGLE;
    }

    /** 프론트 RelativeDirection.of 와 같은 8방향 (45° 칸, 칸 경계는 시계 방향 쪽 칸) */
    static String direction(double relative) {
        int index = (int) ((relative + SECTOR / 2) / SECTOR) % DIRECTIONS.length;
        return DIRECTIONS[index];
    }

    /** 명세 side: 정면 칸(FRONT, ±22.5°)이면 AHEAD, 그 밖은 오른쪽(0~180) RIGHT / 왼쪽 LEFT */
    static String side(double relative) {
        if ("FRONT".equals(direction(relative))) {
            return "AHEAD";
        }
        return relative < FULL_TURN / 2 ? "RIGHT" : "LEFT";
    }

    /**
     * 첫 번째 후보 안내 문장. 프론트 음성 문구(strings.xml voice_*)와 같은 모양 "방향, 이름. …" 에
     * 명세 예시처럼 끝에 "저장할까요?" 를 붙여요 (이미 저장한 건물이면 안 물어요).
     * 서버에는 평형별 평균이 없어서 "최근 N개월 평균" 대신 "최근 거래" 가격을 말해요.
     */
    static String speech(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return NONE_SPEECH;
        }
        Candidate first = candidates.get(0);
        String head = DIRECTION_WORDS.get(first.direction()) + ", " + first.name() + ". ";
        LatestDeal deal = first.latestDeal();
        String body = deal == null
                ? "최근 " + LATEST_DEAL_MONTHS + "개월 거래가 없어요."
                : "전용 " + deal.areaM2().intValue() + "제곱미터, 최근 거래 " + priceVoice(deal.price()) + "이에요.";
        return first.saved() ? head + body : head + body + " 저장할까요?";
    }

    /** 프론트 formatPriceVoice: 123000 → "12억 3,000만 원", 120000 → "12억 원", 9500 → "9,500만 원" */
    static String priceVoice(int priceManwon) {
        int eok = priceManwon / 10_000;
        int man = priceManwon % 10_000;
        StringBuilder sb = new StringBuilder();
        if (eok > 0) {
            sb.append(String.format(Locale.KOREA, "%,d억", eok));
        }
        if (man > 0) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(String.format(Locale.KOREA, "%,d만", man));
        }
        if (sb.isEmpty()) {
            sb.append("0만");
        }
        return sb.append(" 원").toString();
    }

    /** 건물별 최근 1년 해제 아닌 거래 중 가장 최근 1건 (주변 건물과 같은 쿼리·규칙 + 층) */
    private Map<Long, LatestDeal> latestDeals(List<Building> buildings, LocalDate since) {
        if (buildings.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = buildings.stream().map(Building::getId).toList();
        Map<Long, LatestDeal> result = new LinkedHashMap<>();
        for (Trade trade : tradeRepository
                .findByBuildingIdInAndCdealTypeIsNullAndDealDateGreaterThanEqualOrderByDealDateDescIdDesc(ids, since)) {
            result.computeIfAbsent(trade.getBuilding().getId(), id -> {
                TransactionResponse t = TransactionResponse.from(trade); // 면적·평 계산을 상세와 같은 규칙으로
                return new LatestDeal(t.price(), t.dealDate(), t.areaM2(), t.areaPyeong(), t.floor());
            });
        }
        return result;
    }

    /** 자료 기준일: 후보에 나온 구마다 #19 규칙으로 구하고 가장 오래된 날짜 (주변 건물과 같아요). 후보가 없으면 null */
    private LocalDate dataBaseDate(List<Building> buildings) {
        return buildings.stream()
                .map(Building::getSggCd)
                .distinct()
                .map(buildingService::dataBaseDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /** 주소용 법정동: 매칭된 법정동, 없으면 구 코드로 (주변 건물·상세와 같은 규칙) */
    private LegalDong region(Building building, Map<String, LegalDong> cache) {
        if (building.getLegalDong() != null) {
            return building.getLegalDong();
        }
        return cache.computeIfAbsent(building.getSggCd(),
                sgg -> legalDongRepository.findFirstBySggCd(sgg).orElse(null));
    }

    /** 계산 중간값: 건물, 계산에 쓴 좌표(행안부 DB 값 또는 카카오), 거리(m, 반올림 전), 상대 각도 */
    private record Located(Building building, double lat, double lon, double distance, double relative) {
    }
}
