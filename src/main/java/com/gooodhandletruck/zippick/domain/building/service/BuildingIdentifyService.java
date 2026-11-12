package com.gooodhandletruck.zippick.domain.building.service;

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
 * 외부 API(카카오) 는 부르지 않아요. 행안부 좌표(coordSource=JUSO)가 DB 에 있는 건물만 봐요 — 명세 "저장해 둔 데이터만".
 * 그래서 주변 건물(카카오로 보충) 에는 나오지만 여기는 안 나오는 건물이 있을 수 있어요 (11/09 기준 마포 346개 중 NONE 7개).
 */
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

    public BuildingIdentifyResponse identify(CurrentDevice device, double lat, double lon, double heading) {
        return identify(device, lat, lon, heading, KstClock.today());
    }

    /** 테스트에서 오늘 날짜(최근 1년 기준)를 고정하려고 나눠 둬요 */
    BuildingIdentifyResponse identify(CurrentDevice device, double lat, double lon, double heading, LocalDate today) {
        double carHeading = validate(lat, lon, heading);

        // 1) DB 에서 반경을 감싸는 사각형 안 건물만 (인덱스) → 2) 원·앞쪽으로 정확히 거르기
        List<Located> ahead = buildingsInBox(lat, lon).stream()
                .map(b -> locate(b, lat, lon, carHeading))
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
                            b.getLatitude(),
                            b.getLongitude(),
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

    /** 건물 하나의 거리(m, 반올림 전) 와 진행 방향 기준 상대 각도(0 이상 360 미만, 시계 방향) */
    private static Located locate(Building b, double lat, double lon, double heading) {
        double distance = NearbyBuildingService.distanceM(lat, lon, b.getLatitude(), b.getLongitude());
        double bearing = bearingDegrees(lat, lon, b.getLatitude(), b.getLongitude());
        return new Located(b, distance, relative(bearing, heading));
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

    /** 계산 중간값: 건물, 거리(m, 반올림 전), 상대 각도 */
    private record Located(Building building, double distance, double relative) {
    }
}
