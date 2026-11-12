package com.gooodhandletruck.zippick.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.trade.client.MolitFixtures;
import com.gooodhandletruck.zippick.domain.trade.client.MolitTradeItem;
import com.gooodhandletruck.zippick.domain.trade.entity.CollectStatus;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 앞쪽 건물 찾기(API 20번) 실제 사용 상황 (#50 에서 추가).
 *
 * 실제 마포 2026-08 국토부 응답(121건)을 실제 적재 서비스로 넣고(ScenarioSupport), 건물마다 행안부 좌표(JUSO)를
 * 중심 둘레 약 ±1.4km 에 흩어 놓은 상태에서 차가 돌아다니는 상황을 재현해요.
 *
 * 핵심 확인: 프론트(frontend PR #8)가 지금 앱에서 하는 계산 — 주변 건물(1000m) 을 받아 nearestAhead 로 고르기 — 과
 * 서버 identify 결과가 무작위 위치·방향 수천 번에서 똑같은지. 같으면 프론트는 brief() 를 서버 호출로 바꿔 끼우기만 하면 돼요.
 */
@SpringBootTest(properties = {"zippick.nearby.kakao-chunk-pause-ms=0", "zippick.nearby.kakao-retry-pause-ms=0"})
@AutoConfigureMockMvc
class IdentifyScenarioTest extends ScenarioSupport {

    private static final String IDENTIFY = "/api/v1/buildings/identify";
    private final ExecutorService pool = Executors.newFixedThreadPool(16);
    private String me;

    /** 부모(ScenarioSupport)의 마포 적재가 끝난 뒤 돌아요: 모든 건물에 흩어진 행안부 좌표를 넣어요 */
    @BeforeEach
    void scatterJusoCoordinates() throws Exception {
        List<Building> all = buildingRepository.findAll();
        for (Building b : all) {
            long id = b.getId();
            // 건물 id 로 정해지는 좌표 (같은 건물은 항상 같은 자리), 위도 ±0.013°(≈1.4km), 경도 ±0.016°(≈1.4km)
            double lat = CENTER_LAT + ((id * 7919) % 2001 - 1000) * 0.000013;
            double lon = CENTER_LON + ((id * 104729) % 2001 - 1000) * 0.000016;
            b.updateLocation(null, null, lat, lon, CoordSource.JUSO);
        }
        buildingRepository.saveAll(all);
        me = registerDevice();
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    // ---- ① 프론트 계산과 같은지 (무작위 대량) ----

    @Test
    void 무작위_위치와_방향_1500번에서_첫_후보가_프론트_nearestAhead_와_같다() throws Exception {
        Random random = new Random(50);
        int nonEmpty = 0;
        for (int i = 0; i < 1500; i++) {
            double lat = CENTER_LAT + (random.nextDouble() - 0.5) * 0.024;
            double lon = CENTER_LON + (random.nextDouble() - 0.5) * 0.030;
            double heading = Math.floor(random.nextDouble() * 360_000) / 1000; // 0.001° 단위
            nonEmpty += compareWithFront(lat, lon, heading) ? 1 : 0;
        }
        // 비교가 의미 있으려면 후보가 있는 경우가 충분히 많아야 해요
        assertThat(nonEmpty).isGreaterThan(500);
    }

    @Test
    void 건물을_정확히_옆_90도에_두는_경계_방향_400번에서도_프론트와_같다() throws Exception {
        List<Building> all = buildingRepository.findAll();
        Random random = new Random(90);
        for (int i = 0; i < 400; i++) {
            double lat = CENTER_LAT + (random.nextDouble() - 0.5) * 0.016;
            double lon = CENTER_LON + (random.nextDouble() - 0.5) * 0.020;
            Building target = all.get(random.nextInt(all.size()));
            double bearing = frontBearing(lat, lon, target.getLatitude(), target.getLongitude());
            // 그 건물이 정확히 왼쪽/오른쪽 경계(상대 90°·270°) 또는 정면(0°)이 되게 heading 을 잡아요
            double[] offsets = {-90, 90, 0, 22.5, -22.5};
            double heading = ((bearing - offsets[i % offsets.length]) % 360 + 360) % 360;
            compareWithFront(lat, lon, heading);
        }
    }

    @Test
    void 북쪽_0도_근처_heading_들도_프론트와_같다() throws Exception {
        Random random = new Random(0);
        double[] headings = {0, 0.001, 359.999, 359, 1, 360};
        for (int i = 0; i < 300; i++) {
            double lat = CENTER_LAT + (random.nextDouble() - 0.5) * 0.02;
            double lon = CENTER_LON + (random.nextDouble() - 0.5) * 0.025;
            compareWithFront(lat, lon, headings[i % headings.length]);
        }
    }

    // ---- ② 주행 시뮬레이션 ----

    @Test
    void 남에서_북으로_달렸다가_돌아오는_동안_모든_응답이_규칙을_지킨다() throws Exception {
        // 중심 남쪽 1.5km → 북쪽 1.5km (25m 간격, heading 0), 다시 남쪽으로 (heading 180)
        int steps = 120;
        Set<Long> heardNorth = new HashSet<>();
        Set<Long> heardSouth = new HashSet<>();
        for (int s = 0; s <= steps; s++) {
            double lat = CENTER_LAT - 0.0135 + s * 0.000225;
            heardNorth.addAll(checkRules(lat, CENTER_LON, 0));
        }
        for (int s = steps; s >= 0; s--) {
            double lat = CENTER_LAT - 0.0135 + s * 0.000225;
            heardSouth.addAll(checkRules(lat, CENTER_LON, 180));
        }
        assertThat(heardNorth).isNotEmpty();
        assertThat(heardSouth).isNotEmpty();
    }

    @Test
    void 교차로에서_1도씩_한_바퀴_돌면_방향마다_프론트와_같고_첫_후보가_바뀐다() throws Exception {
        Set<Long> firsts = new HashSet<>();
        for (int heading = 0; heading < 360; heading++) {
            compareWithFront(CENTER_LAT, CENTER_LON, heading);
            checkRules(CENTER_LAT, CENTER_LON, heading);
            JsonNode list = identify(CENTER_LAT, CENTER_LON, heading).get("candidates");
            if (list.size() > 0) {
                firsts.add(list.get(0).get("buildingId").asLong());
            }
        }
        // 가장 가까운 건물은 진행 방향 절반(180°) 동안 계속 첫 번째라 많이 바뀌진 않지만, 방향을 무시하면 하나로 고정돼요
        assertThat(firsts.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void 같은_자리_같은_방향이면_몇_번을_불러도_응답이_같다() throws Exception {
        String url = url(CENTER_LAT, CENTER_LON, 37.5);
        String first = mockMvc.perform(get(url).header("X-Anonymous-Id", me)).andReturn().getResponse().getContentAsString();
        for (int i = 0; i < 30; i++) {
            assertThat(mockMvc.perform(get(url).header("X-Anonymous-Id", me)).andReturn().getResponse()
                    .getContentAsString()).isEqualTo(first);
        }
    }

    // ---- ③ 다른 API 와 이어지는지 ----

    @Test
    void 주행_중_나온_모든_후보를_상세로_열면_이름_주소_유형_최근_거래가_같다() throws Exception {
        Map<Long, JsonNode> seen = new HashMap<>();
        Random random = new Random(3);
        for (int i = 0; i < 200; i++) {
            JsonNode data = identify(CENTER_LAT + (random.nextDouble() - 0.5) * 0.02,
                    CENTER_LON + (random.nextDouble() - 0.5) * 0.025, random.nextInt(360));
            data.get("candidates").forEach(c -> seen.putIfAbsent(c.get("buildingId").asLong(), c));
        }
        assertThat(seen.size()).isGreaterThan(10);
        for (JsonNode c : seen.values()) {
            long id = c.get("buildingId").asLong();
            JsonNode detail = getData("/api/v1/buildings/" + id);
            assertThat(detail.get("name").asText()).isEqualTo(c.get("name").asText());
            assertThat(detail.get("jibunAddress").asText()).isEqualTo(c.get("address").asText());
            assertThat(detail.get("type").asText()).isEqualTo(c.get("type").asText());
            JsonNode deal = c.get("latestDeal");
            JsonNode recent = detail.get("recentTransactions");
            if (deal.isNull()) {
                continue;
            }
            // 최근 1년 안의 가장 최근 해제 아닌 거래 = 상세 최근 거래 첫 줄
            assertThat(recent.get(0).get("dealDate").asText()).isEqualTo(deal.get("dealDate").asText());
            assertThat(recent.get(0).get("price").asInt()).isEqualTo(deal.get("price").asInt());
            assertThat(recent.get(0).get("floor").asInt()).isEqualTo(deal.get("floor").asInt());
        }
    }

    @Test
    void 앞쪽_후보에서_저장하면_다음_호출에서_saved_true_이고_취소하면_원래대로() throws Exception {
        double[] spot = spotWithCandidates();
        JsonNode before = identify(spot[0], spot[1], spot[2]);
        long id = before.get("candidates").get(0).get("buildingId").asLong();
        assertThat(before.get("candidates").get(0).get("saved").asBoolean()).isFalse();
        assertThat(before.get("speechText").asText()).endsWith("저장할까요?");

        assertThat(save(me, id)).isEqualTo(201);
        JsonNode after = identify(spot[0], spot[1], spot[2]);
        assertThat(after.get("candidates").get(0).get("saved").asBoolean()).isTrue();
        assertThat(after.get("speechText").asText()).doesNotContain("저장할까요");

        // 다른 기기에는 영향 없음
        String other = registerDevice();
        JsonNode othersView = getData(url(spot[0], spot[1], spot[2]), other);
        assertThat(othersView.get("candidates").get(0).get("saved").asBoolean()).isFalse();

        assertThat(unsave(me, id)).isEqualTo(200);
        assertThat(identify(spot[0], spot[1], spot[2]).get("candidates").get(0).get("saved").asBoolean()).isFalse();
    }

    @Test
    void 재수집에서_최근_거래가_해제되면_앞쪽_후보의_latestDeal_도_바로_바뀐다() throws Exception {
        double[] spot = spotWithCandidates();
        JsonNode first = identify(spot[0], spot[1], spot[2]).get("candidates").get(0);
        long id = first.get("buildingId").asLong();
        if (first.get("latestDeal").isNull()) {
            return; // 이 자리의 첫 후보에 최근 거래가 없으면 확인할 게 없어요 (spotWithCandidates 가 거래 있는 곳을 골라요)
        }
        Trade latest = tradeRepository.findTop4ByBuildingIdAndCdealTypeIsNullOrderByDealDateDescIdDesc(id).get(0);
        String aptNm = buildingRepository.findById(id).orElseThrow().getAptNm();
        List<MolitTradeItem> resent = new ArrayList<>();
        boolean marked = false;
        for (MolitTradeItem item : mapo) {
            if (!marked && sameDeal(item, latest, aptNm)) {
                resent.add(MolitFixtures.copy(item, Map.of("cdealType", "O", "cdealDay", "26.10.01")));
                marked = true;
            } else {
                resent.add(item);
            }
        }
        assertThat(marked).isTrue();
        assertThat(collect(YM, resent).getStatus()).isEqualTo(CollectStatus.SUCCESS);

        JsonNode after = identify(spot[0], spot[1], spot[2]).get("candidates").get(0);
        assertThat(after.get("buildingId").asLong()).isEqualTo(id); // 좌표는 재수집과 관계없어서 같은 건물
        JsonNode recent = getData("/api/v1/buildings/" + id).get("recentTransactions");
        if (recent.isEmpty()) {
            assertThat(after.get("latestDeal").isNull()).isTrue();
        } else {
            assertThat(after.get("latestDeal").get("dealDate").asText() + after.get("latestDeal").get("price"))
                    .isEqualTo(recent.get(0).get("dealDate").asText() + recent.get(0).get("price"));
        }
        // 주변 건물 카드와도 같아요
        JsonNode card = null;
        for (JsonNode c : getData(nearbyUrl(3000)).get("buildings")) {
            if (c.get("buildingId").asLong() == id) {
                card = c;
            }
        }
        assertThat(card).isNotNull();
        assertThat(String.valueOf(after.get("latestDeal").get("price")))
                .isEqualTo(String.valueOf(card.get("latestDeal").get("price")));
    }

    // ---- ④ 동시 요청 ----

    @Test
    void 기기_8대가_여러_방향으로_동시에_48번_불러도_전부_200이고_혼자_부를_때와_같다() throws Exception {
        List<String> devices = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            devices.add(registerDevice());
        }
        double[] headings = {0, 45, 90, 135, 180, 225};
        Map<Integer, String> expected = new HashMap<>();
        for (int i = 0; i < 48; i++) {
            expected.put(i, response(i, devices, headings).getContentAsString());
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            int index = i;
            Callable<MockHttpServletResponse> c = () -> {
                start.await();
                return response(index, devices, headings);
            };
            futures.add(pool.submit(c));
        }
        start.countDown();
        for (int i = 0; i < 48; i++) {
            MockHttpServletResponse r = futures.get(i).get(30, TimeUnit.SECONDS);
            assertThat(r.getStatus()).isEqualTo(200);
            assertThat(r.getContentAsString()).isEqualTo(expected.get(i));
        }
    }

    @Test
    void 재수집이_도는_중에_불러도_5xx_없이_응답한다() throws Exception {
        double[] spot = spotWithCandidates();
        CountDownLatch start = new CountDownLatch(1);
        Future<?> recollect = pool.submit(() -> {
            start.await();
            return collect(YM, mapo);
        });
        List<Future<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            calls.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(get(url(spot[0], spot[1], spot[2])).header("X-Anonymous-Id", me))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        recollect.get(60, TimeUnit.SECONDS);
        for (Future<Integer> f : calls) {
            assertThat(f.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        }
    }

    // ---- 비교 도우미 ----

    /**
     * 프론트 RemoteRepository.brief 와 똑같이 계산해서 서버 결과와 비교해요.
     * 프론트: nearby(lat, lon, 1000) → nearestAhead(lat, lon, heading, 1000) = 반경·좌우 90° 안에서 가장 가까운 하나.
     * 서버 후보 전체(최대 3개)도 프론트 규칙으로 가까운 순 3개와 같은지 봐요. @return 후보가 있었는지
     */
    private boolean compareWithFront(double lat, double lon, double heading) throws Exception {
        JsonNode nearby = getData("/api/v1/buildings/nearby?lat=" + lat + "&lon=" + lon + "&radius=1000").get("buildings");
        double frontHeading = heading % 360; // 프론트 LocationRepository 가 0~360 으로 맞춰서 보내요
        List<double[]> ahead = new ArrayList<>(); // {id, raw distance, 목록 순서}
        int order = 0;
        for (JsonNode b : nearby) {
            double bLat = b.get("lat").asDouble();
            double bLon = b.get("lon").asDouble();
            double distance = frontDistance(lat, lon, bLat, bLon);
            if (distance <= 1000 && frontIsAhead(frontBearing(lat, lon, bLat, bLon), frontHeading)) {
                ahead.add(new double[] {b.get("buildingId").asLong(), distance, order});
            }
            order++;
        }
        // 프론트 minByOrNull: 거리 최소, 같으면 목록에서 먼저 나온 것
        ahead.sort(Comparator.<double[]>comparingDouble(a -> a[1]).thenComparingDouble(a -> a[2]));

        JsonNode server = identify(lat, lon, heading).get("candidates");
        String where = "lat=" + lat + " lon=" + lon + " heading=" + heading;
        if (ahead.isEmpty()) {
            assertThat(server.size()).as(where).isZero();
            return false;
        }
        assertThat(server.size()).as(where).isEqualTo(Math.min(3, ahead.size()));
        for (int i = 0; i < server.size(); i++) {
            assertThat(server.get(i).get("buildingId").asLong()).as(where + " #" + i).isEqualTo((long) ahead.get(i)[0]);
        }
        return true;
    }

    /**
     * 응답 하나가 규칙을 지키는지 DB 좌표로 다시 계산해서 확인해요:
     * 반경 1000m·좌우 90° 안, 가까운 순, 최대 3개, side·direction·distanceM 이 계산과 같고, 문장이 첫 후보 방향으로 시작.
     */
    private Set<Long> checkRules(double lat, double lon, double heading) throws Exception {
        JsonNode data = identify(lat, lon, heading);
        JsonNode list = data.get("candidates");
        assertThat(list.size()).isLessThanOrEqualTo(3);
        Set<Long> ids = new HashSet<>();
        double prev = -1;
        for (JsonNode c : list) {
            Building b = buildingRepository.findById(c.get("buildingId").asLong()).orElseThrow();
            double distance = frontDistance(lat, lon, b.getLatitude(), b.getLongitude());
            double relative = ((frontBearing(lat, lon, b.getLatitude(), b.getLongitude()) - heading) % 360 + 360) % 360;
            assertThat(distance).isLessThanOrEqualTo(1000);
            assertThat(relative <= 90 || relative >= 270).isTrue();
            assertThat(distance).isGreaterThanOrEqualTo(prev);
            assertThat(c.get("distanceM").asInt()).isEqualTo((int) Math.round(distance));
            String expectedSide = relative < 22.5 || relative >= 337.5 ? "AHEAD" : relative < 180 ? "RIGHT" : "LEFT";
            assertThat(c.get("side").asText()).isEqualTo(expectedSide);
            prev = distance;
            ids.add(b.getId());
        }
        if (list.isEmpty()) {
            assertThat(data.get("speechText").asText()).isEqualTo("주변에서 거래된 건물을 찾지 못했어요.");
        } else {
            String word = switch (list.get(0).get("direction").asText()) {
                case "FRONT" -> "앞, ";
                case "FRONT_RIGHT" -> "오른쪽 앞, ";
                case "RIGHT" -> "오른쪽, ";
                case "FRONT_LEFT" -> "왼쪽 앞, ";
                default -> "왼쪽, ";
            };
            assertThat(data.get("speechText").asText()).startsWith(word + list.get(0).get("name").asText() + ". ");
        }
        return ids;
    }

    /** 후보가 있고 첫 후보에 최근 거래가 있는 자리 {lat, lon, heading} (고정 시드라 항상 같은 자리) */
    private double[] spotWithCandidates() throws Exception {
        Random random = new Random(11);
        for (int i = 0; i < 500; i++) {
            double[] spot = {CENTER_LAT + (random.nextDouble() - 0.5) * 0.01,
                    CENTER_LON + (random.nextDouble() - 0.5) * 0.012, random.nextInt(360)};
            JsonNode list = identify(spot[0], spot[1], spot[2]).get("candidates");
            if (list.size() > 0 && !list.get(0).get("latestDeal").isNull()) {
                return spot;
            }
        }
        throw new AssertionError("후보가 있는 자리를 못 찾았어요");
    }

    private MockHttpServletResponse response(int i, List<String> devices, double[] headings) throws Exception {
        double lat = CENTER_LAT + (i % 4) * 0.001;
        return mockMvc.perform(get(url(lat, CENTER_LON, headings[i % headings.length]))
                .header("X-Anonymous-Id", devices.get(i % devices.size()))).andReturn().getResponse();
    }

    private JsonNode identify(double lat, double lon, double heading) throws Exception {
        MockHttpServletResponse r = mockMvc.perform(get(url(lat, lon, heading)).header("X-Anonymous-Id", me))
                .andReturn().getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return objectMapper.readTree(r.getContentAsString()).get("data");
    }

    private static String url(double lat, double lon, double heading) {
        return IDENTIFY + "?lat=" + lat + "&lon=" + lon + "&heading=" + heading;
    }

    // 프론트 Geo.kt / FakeRepository.kt 를 그대로 옮긴 기준 계산 (바꾸지 말 것)

    private static double frontDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * 6_371_000.0 * Math.asin(Math.sqrt(a));
    }

    private static double frontBearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private static boolean frontIsAhead(double bearing, double heading) {
        double relative = ((bearing - heading) % 360.0 + 360.0) % 360.0;
        return relative <= 90.0 || relative >= 360.0 - 90.0;
    }

    /** 픽스처 항목이 DB 의 그 거래와 같은 거래인지 (AppFlowScenarioTest 와 같은 규칙) */
    private static boolean sameDeal(MolitTradeItem item, Trade trade, String aptNm) {
        LocalDate date = LocalDate.of(Integer.parseInt(item.dealYear().trim()),
                Integer.parseInt(item.dealMonth().trim()), Integer.parseInt(item.dealDay().trim()));
        return date.equals(trade.getDealDate())
                && Integer.parseInt(item.dealAmount().replace(",", "").trim()) == trade.getDealAmount()
                && Integer.parseInt(item.floor().trim()) == trade.getFloor()
                && new BigDecimal(item.excluUseAr().trim()).compareTo(trade.getExcluUseAr()) == 0
                && item.aptNm().trim().equals(aptNm);
    }
}
