package com.gooodhandletruck.zippick.domain.building.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingAddress;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingCoordinateResponse;
import com.gooodhandletruck.zippick.domain.building.service.BuildingCoordinateService;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import com.gooodhandletruck.zippick.global.util.KstClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앞쪽 건물 찾기 API (API 20번, GET /api/v1/buildings/identify) 를 실제 HTTP 요청으로 확인해요. (#50 에서 추가)
 *
 * 건물은 기준 위치(도화동 부근 37.5400, 126.9450)에서 "거리 m · 방위각 °" 로 정확히 놓아요 (구면 목적지 공식).
 * 좌표는 행안부 좌표(JUSO)로 DB 에 넣고, 카카오 좌표 서비스는 가짜로 바꿔서 "한 번도 안 불렸는지" 확인해요.
 * H2(MySQL 모드), 테스트마다 롤백.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BuildingIdentifyApiTest {

    private static final String URL = "/api/v1/buildings/identify";
    private static final double LAT = 37.5400;
    private static final double LON = 126.9450;
    private static final double EARTH_RADIUS_M = 6_371_000;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private LegalDongRepository legalDongRepository;

    /** 카카오 좌표 — 이 API 는 부르면 안 돼요 (명세: 저장된 데이터만) */
    @MockitoBean
    private BuildingCoordinateService buildingCoordinateService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String me;
    private int seq;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = KstClock.today();
        me = registerDevice();
    }

    // ---- ① 범위·정렬 ----

    @Test
    void 앞쪽_건물을_가까운_순으로_최대_3개() throws Exception {
        Building a = at("200m", 200, 0);
        Building b = at("400m", 400, 30);
        Building c = at("600m", 600, 330);
        at("800m", 800, 10); // 4번째 → 빠짐

        JsonNode list = candidates(0);

        assertThat(ids(list)).containsExactly(a.getId(), b.getId(), c.getId());
        assertThat(list.get(0).get("distanceM").asInt()).isEqualTo(200);
        assertThat(list.get(1).get("distanceM").asInt()).isEqualTo(400);
        assertThat(list.get(2).get("distanceM").asInt()).isEqualTo(600);
    }

    @Test
    void 뒤쪽_건물은_가까워도_빠진다() throws Exception {
        at("뒤 50m", 50, 180);
        at("오른쪽 뒤 100m", 100, 135);
        at("왼쪽 뒤 100m", 100, 225);
        Building ahead = at("앞 900m", 900, 0);

        assertThat(ids(candidates(0))).containsExactly(ahead.getId());
    }

    @ParameterizedTest(name = "{0}m → 포함 {1}")
    @CsvSource({"999, true", "1000, true", "1001, false", "1500, false"})
    void 반경_1000m_경계(int distance, boolean included) throws Exception {
        at("경계", distance, 0);
        assertThat(candidates(0)).hasSize(included ? 1 : 0);
    }

    @ParameterizedTest(name = "heading {0}, 방위각 {1} → 포함 {2}")
    @CsvSource({
            "0, 89.9, true", "0, 90.1, false", "0, 270.1, true", "0, 269.9, false",
            "90, 179.9, true", "90, 180.1, false", "90, 0.1, true", "90, 359.9, false",
            "180, 269.9, true", "180, 270.1, false", "180, 90.1, true", "180, 89.9, false",
            "270, 359.9, true", "270, 0.1, false", "270, 180.1, true", "270, 179.9, false",
            "350, 79.9, true", "350, 80.1, false", "350, 260.1, true", "350, 259.9, false"})
    void 진행_방향_좌우_90도_경계와_북쪽_걸침(double heading, double bearing, boolean included) throws Exception {
        at("경계", 300, bearing);
        assertThat(candidates(heading)).hasSize(included ? 1 : 0);
    }

    @ParameterizedTest(name = "heading {0} → 정면 {1}, 빠짐 {2}")
    @CsvSource({"0, 북, 남", "90, 동, 서", "180, 남, 북", "270, 서, 동"})
    void 동서남북에_하나씩_있으면_heading_쪽은_정면이고_반대쪽은_빠진다(double heading, String front, String back)
            throws Exception {
        at("북", 300, 0);
        at("동", 310, 90);
        at("남", 320, 180);
        at("서", 330, 270);
        // 정확히 옆(±90°) 건물은 경계 포함이라 같이 나올 수 있어요 (프론트 isAhead 와 같음)
        JsonNode list = candidates(heading);
        List<String> names = new ArrayList<>();
        list.forEach(n -> names.add(n.get("name").asText()));
        assertThat(names).contains(front).doesNotContain(back);
        list.forEach(n -> {
            if (n.get("name").asText().equals(front)) {
                assertThat(n.get("side").asText()).isEqualTo("AHEAD");
            } else {
                assertThat(n.get("side").asText()).isIn("LEFT", "RIGHT");
            }
        });
    }

    @ParameterizedTest(name = "heading {0} → {1}·{2} 만")
    @CsvSource({"45, 북, 동", "135, 동, 남", "225, 남, 서", "315, 서, 북"})
    void 대각선_heading_이면_양옆_두_건물만_나온다(double heading, String one, String two) throws Exception {
        at("북", 300, 0);
        at("동", 310, 90);
        at("남", 320, 180);
        at("서", 330, 270);
        List<String> names = new ArrayList<>();
        candidates(heading).forEach(n -> names.add(n.get("name").asText()));
        assertThat(names).containsExactlyInAnyOrder(one, two);
    }

    @Test
    void 같은_거리면_id_순() throws Exception {
        Building first = at("먼저", 300, 10);
        Building second = at("나중", 300, 350);

        assertThat(ids(candidates(0))).containsExactly(first.getId(), second.getId());
    }

    @Test
    void 반올림_전_거리로_정렬한다() throws Exception {
        Building farther = at("300.4m", 300.4, 0);
        Building nearer = at("300.2m", 300.2, 20); // 둘 다 distanceM 300 이지만 실제로 더 가까워요

        JsonNode list = candidates(0);
        assertThat(ids(list)).containsExactly(nearer.getId(), farther.getId());
        assertThat(list.get(0).get("distanceM").asInt()).isEqualTo(300);
        assertThat(list.get(1).get("distanceM").asInt()).isEqualTo(300);
    }

    @Test
    void 차와_같은_자리의_건물은_거리_0_으로_나온다() throws Exception {
        // 프론트도 같은 자리면 방위각 0 → heading 0 이면 앞쪽(정면)
        Building here = at("여기", 0, 0);
        JsonNode list = candidates(0);
        assertThat(ids(list)).containsExactly(here.getId());
        assertThat(list.get(0).get("distanceM").asInt()).isZero();
        assertThat(list.get(0).get("side").asText()).isEqualTo("AHEAD");
    }

    // ---- ② side · direction ----

    @ParameterizedTest(name = "방위각 {0} → {1} / {2}")
    @CsvSource({
            "0, AHEAD, FRONT", "20, AHEAD, FRONT", "25, RIGHT, FRONT_RIGHT", "60, RIGHT, FRONT_RIGHT",
            "70, RIGHT, RIGHT", "89, RIGHT, RIGHT", "340, AHEAD, FRONT", "335, LEFT, FRONT_LEFT",
            "300, LEFT, FRONT_LEFT", "290, LEFT, LEFT", "271, LEFT, LEFT"})
    void 차에서_본_쪽과_8방향(double bearing, String side, String direction) throws Exception {
        at("건물", 300, bearing);
        JsonNode c = candidates(0).get(0);
        assertThat(c.get("side").asText()).isEqualTo(side);
        assertThat(c.get("direction").asText()).isEqualTo(direction);
    }

    @Test
    void side_는_heading_기준이다() throws Exception {
        at("동쪽 건물", 300, 90);
        assertThat(candidates(90).get(0).get("side").asText()).isEqualTo("AHEAD"); // 동쪽으로 가면 정면
        assertThat(candidates(5).get(0).get("side").asText()).isEqualTo("RIGHT"); // 거의 북쪽으로 가면 오른쪽
        assertThat(candidates(175).get(0).get("side").asText()).isEqualTo("LEFT"); // 거의 남쪽으로 가면 왼쪽
    }

    // ---- ③ 좌표 출처 · 외부 API ----

    // #50 후속: 행안부 좌표가 없는 건물은 카카오로 찾아요 (주변 건물 API 와 같은 규칙). 아래 3개가 그 동작을 확인해요

    @Test
    void 행안부_좌표_건물만_있으면_카카오는_부르지_않는다() throws Exception {
        Building juso = at("행안부 좌표", 300, 0);
        assertThat(ids(candidates(0))).containsExactly(juso.getId());
        verify(buildingCoordinateService, never()).findCoordinates(anyList());
        verify(buildingCoordinateService, never()).findCoordinate(any(BuildingAddress.class));
    }

    @Test
    void 행안부_좌표가_없는_건물은_카카오_좌표로_찾는다() throws Exception {
        Building juso = at("행안부 좌표", 300, 0);
        Building noneSource = saved(TestFixtures.building().aptNm("NONE").jibun(String.valueOf(1000 + seq++)).build());
        noneSource.updateLocation(null, null, null, null, CoordSource.NONE);
        Building noSourceCoord = saved(TestFixtures.building().aptNm("출처 없는 좌표").jibun(String.valueOf(1000 + seq++)).build());
        double[] stale = destination(50, 0);
        noSourceCoord.updateLocation(null, null, stale[0], stale[1], null); // 출처 없는 좌표는 안 믿고 카카오로 다시 찾아요
        buildingRepository.flush();

        double[] kakaoNone = destination(100, 0);   // 카카오가 알려준 NONE 건물 위치: 정면 100m
        double[] kakaoStale = destination(500, 180); // 출처 없는 건물의 실제 위치: 뒤쪽 500m → 빠져야 해요
        when(buildingCoordinateService.findCoordinates(anyList())).thenReturn(List.of(
                BuildingCoordinateResponse.found(String.valueOf(noneSource.getId()), kakaoNone[0], kakaoNone[1]),
                BuildingCoordinateResponse.found(String.valueOf(noSourceCoord.getId()), kakaoStale[0], kakaoStale[1])));

        JsonNode list = candidates(0);
        assertThat(ids(list)).containsExactly(noneSource.getId(), juso.getId()); // 카카오 100m → 행안부 300m 순
        assertThat(list.get(0).get("lat").asDouble()).isEqualTo(kakaoNone[0]);  // 응답 좌표도 카카오 좌표
        assertThat(list.get(0).get("distanceM").asInt()).isEqualTo(100);
    }

    @Test
    void 카카오가_못_찾거나_실패해도_행안부_좌표_건물로_응답한다() throws Exception {
        Building juso = at("행안부 좌표", 300, 0);
        Building noneSource = saved(TestFixtures.building().aptNm("NONE").jibun(String.valueOf(1000 + seq++)).build());
        noneSource.updateLocation(null, null, null, null, CoordSource.NONE);
        buildingRepository.flush();

        when(buildingCoordinateService.findCoordinates(anyList()))
                .thenReturn(List.of(BuildingCoordinateResponse.notFound(String.valueOf(noneSource.getId()))));
        assertThat(ids(candidates(0))).containsExactly(juso.getId());

        when(buildingCoordinateService.findCoordinates(anyList())).thenThrow(new RuntimeException("카카오 장애"));
        assertThat(ids(candidates(0))).containsExactly(juso.getId()); // E001 대신 정상 응답
    }

    @Test
    void 경도_사각형_모서리_근처_건물도_빠지지_않는다() throws Exception {
        // 북동·북서 45° 990m — 위·경도 사각형으로 먼저 거를 때 잘리면 안 되는 위치
        Building ne = at("북동", 990, 45);
        Building nw = at("북서", 990, 315);
        assertThat(ids(candidates(0))).containsExactlyInAnyOrder(ne.getId(), nw.getId());
    }

    // ---- ④ 거래 · 문장 ----

    @Test
    void 명세_응답_모양과_주변_건물과_같은_필드() throws Exception {
        legalDongRepository.save(TestFixtures.legalDong().build()); // 주소의 "서울 마포구"
        Building b = at("한화오벨리스크", 180, 330);
        trade(b, today.minusDays(10), 59700, "36.49", 7, null);

        MvcResult result = mockMvc.perform(get(URL).header("X-Anonymous-Id", me)
                        .param("lat", "" + LAT).param("lon", "" + LON).param("heading", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dataBaseDate").exists())
                .andExpect(jsonPath("$.data.candidates", hasSize(1)))
                .andExpect(jsonPath("$.data.candidates[0].buildingId").value(b.getId()))
                .andExpect(jsonPath("$.data.candidates[0].name").value("한화오벨리스크"))
                .andExpect(jsonPath("$.data.candidates[0].type").value("APT"))
                .andExpect(jsonPath("$.data.candidates[0].address").value("서울 마포구 아현동 " + b.getJibun()))
                .andExpect(jsonPath("$.data.candidates[0].distanceM").value(180))
                .andExpect(jsonPath("$.data.candidates[0].imageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.candidates[0].side").value("LEFT"))
                .andExpect(jsonPath("$.data.candidates[0].direction").value("FRONT_LEFT"))
                .andExpect(jsonPath("$.data.candidates[0].saved").value(false))
                .andExpect(jsonPath("$.data.candidates[0].latestDeal.price").value(59700))
                .andExpect(jsonPath("$.data.candidates[0].latestDeal.areaM2").value(36.49))
                .andExpect(jsonPath("$.data.candidates[0].latestDeal.areaPyeong").value(11.0))
                .andExpect(jsonPath("$.data.candidates[0].latestDeal.floor").value(7))
                .andExpect(jsonPath("$.data.candidates[0].latestDeal.dealDate").value(today.minusDays(10).toString()))
                .andExpect(jsonPath("$.data.speechText")
                        .value("왼쪽 앞, 한화오벨리스크. 전용 36제곱미터, 최근 거래 5억 9,700만 원이에요. 저장할까요?"))
                .andReturn();

        // 프론트가 쓰는 주변 건물 필드(NearbyBuildingDto)는 주변 건물 API 와 값이 같아야 해요 (같은 변환 재사용)
        JsonNode identify = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("candidates").get(0);
        JsonNode nearby = objectMapper.readTree(mockMvc.perform(get("/api/v1/buildings/nearby")
                        .param("lat", "" + LAT).param("lon", "" + LON)).andReturn().getResponse().getContentAsString())
                .get("data").get("buildings").get(0);
        for (String field : List.of("buildingId", "name", "type", "address", "lat", "lon", "distanceM", "imageUrl")) {
            assertThat(identify.get(field)).as(field).isEqualTo(nearby.get(field));
        }
        for (String field : List.of("price", "dealDate", "areaM2", "areaPyeong")) {
            assertThat(identify.get("latestDeal").get(field)).as("latestDeal." + field)
                    .isEqualTo(nearby.get("latestDeal").get(field));
        }
    }

    @Test
    void 해제_거래와_1년_지난_거래는_latestDeal_에서_빠진다() throws Exception {
        Building b = at("건물", 300, 0);
        trade(b, today.minusDays(3), 99000, "84.97", 10, "O"); // 해제 → 제외
        trade(b, today.minusDays(20), 80000, "59.99", 5, null); // 이게 latestDeal
        trade(b, today.minusMonths(13), 70000, "59.99", 4, null); // 1년 지남

        JsonNode deal = candidates(0).get(0).get("latestDeal");
        assertThat(deal.get("price").asInt()).isEqualTo(80000);
        assertThat(deal.get("floor").asInt()).isEqualTo(5);
    }

    @Test
    void 최근_1년_거래가_없으면_latestDeal_null_과_그런_문장() throws Exception {
        Building b = at("오래된단지", 300, 90);
        trade(b, today.minusMonths(14), 70000, "59.99", 4, null);

        JsonNode data = data(0);
        assertThat(data.get("candidates").get(0).get("latestDeal").isNull()).isTrue();
        assertThat(data.get("speechText").asText()).isEqualTo("오른쪽, 오래된단지. 최근 12개월 거래가 없어요. 저장할까요?");
    }

    @Test
    void 후보가_없으면_빈_배열과_명세_문장_기준일_null() throws Exception {
        at("뒤", 300, 180);

        mockMvc.perform(get(URL).header("X-Anonymous-Id", me)
                        .param("lat", "" + LAT).param("lon", "" + LON).param("heading", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.candidates", hasSize(0)))
                .andExpect(jsonPath("$.data.speechText").value("주변에서 거래된 건물을 찾지 못했어요."))
                .andExpect(jsonPath("$.data.dataBaseDate").doesNotExist());
    }

    @Test
    void DB_에_건물이_하나도_없어도_빈_배열() throws Exception {
        assertThat(candidates(0)).isEmpty();
    }

    @Test
    void 오피스텔은_OFFICETEL() throws Exception {
        Building b = saved(TestFixtures.building().buildingType(BuildingType.OFFICETEL).aptNm("오피스텔").jibun("999").build());
        place(b, 300, 0);
        assertThat(candidates(0).get(0).get("type").asText()).isEqualTo("OFFICETEL");
    }

    // ---- ⑤ 기기 · 저장 여부 ----

    @Test
    void 내가_저장한_건물만_saved_true_이고_문장에서_저장_질문이_빠진다() throws Exception {
        Building mine = at("내가 저장", 200, 0);
        Building others = at("남이 저장", 300, 0);
        String other = registerDevice();
        favorite(me, mine.getId());
        favorite(other, others.getId());

        JsonNode data = data(0);
        assertThat(data.get("candidates").get(0).get("saved").asBoolean()).isTrue();
        assertThat(data.get("candidates").get(1).get("saved").asBoolean()).isFalse();
        assertThat(data.get("speechText").asText()).doesNotContain("저장할까요");
    }

    @Test
    void 헤더가_없으면_D001() throws Exception {
        at("건물", 300, 0);
        mockMvc.perform(get(URL).param("lat", "" + LAT).param("lon", "" + LON).param("heading", "0"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("D001"));
    }

    @Test
    void 등록_안_된_기기면_D002() throws Exception {
        mockMvc.perform(get(URL).header("X-Anonymous-Id", "00000000-0000-0000-0000-000000000000")
                        .param("lat", "" + LAT).param("lon", "" + LON).param("heading", "0"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("D002"));
    }

    // ---- ⑥ 잘못된 입력 ----

    @ParameterizedTest(name = "heading={0} → C001")
    @ValueSource(strings = {"-1", "-0.001", "360.001", "400", "NaN", "abc", ""})
    void heading_이_범위_밖이거나_숫자가_아니면_C001(String heading) throws Exception {
        mockMvc.perform(get(URL).header("X-Anonymous-Id", me)
                        .param("lat", "" + LAT).param("lon", "" + LON).param("heading", heading))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @ParameterizedTest(name = "lat={0} lon={1} → C001")
    @CsvSource({"90.1, 126.9", "-90.1, 126.9", "37.5, 180.1", "37.5, -180.1", "NaN, 126.9", "37.5, NaN"})
    void lat_lon_이_범위_밖이면_C001(String lat, String lon) throws Exception {
        mockMvc.perform(get(URL).header("X-Anonymous-Id", me)
                        .param("lat", lat).param("lon", lon).param("heading", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @ParameterizedTest(name = "{0} 없음 → C001")
    @ValueSource(strings = {"lat", "lon", "heading"})
    void 필수_파라미터가_없으면_C001(String missing) throws Exception {
        var request = get(URL).header("X-Anonymous-Id", me);
        if (!missing.equals("lat")) {
            request.param("lat", "" + LAT);
        }
        if (!missing.equals("lon")) {
            request.param("lon", "" + LON);
        }
        if (!missing.equals("heading")) {
            request.param("heading", "0");
        }
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void heading_360_은_0_북쪽과_같다() throws Exception {
        at("북", 300, 0);
        at("남", 300, 180);
        assertThat(ids(candidates(360))).isEqualTo(ids(candidates(0)));
    }

    @ParameterizedTest(name = "heading={0} 경계값 허용")
    @ValueSource(strings = {"0", "0.0", "359", "359.999", "360"})
    void heading_경계값은_받는다(String heading) throws Exception {
        mockMvc.perform(get(URL).header("X-Anonymous-Id", me)
                        .param("lat", "" + LAT).param("lon", "" + LON).param("heading", heading))
                .andExpect(status().isOk());
    }

    @Test
    void 위경도_극값에서도_500_이_아니다() throws Exception {
        at("건물", 300, 0);
        for (String[] p : List.of(new String[] {"90", "0"}, new String[] {"-90", "180"}, new String[] {"0", "-180"})) {
            mockMvc.perform(get(URL).header("X-Anonymous-Id", me)
                            .param("lat", p[0]).param("lon", p[1]).param("heading", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.candidates", hasSize(0)));
        }
    }

    // ---- 도우미 ----

    private JsonNode data(double heading) throws Exception {
        MvcResult result = mockMvc.perform(get(URL).header("X-Anonymous-Id", me)
                        .param("lat", "" + LAT).param("lon", "" + LON).param("heading", "" + heading))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private JsonNode candidates(double heading) throws Exception {
        return data(heading).get("candidates");
    }

    private static List<Long> ids(JsonNode list) {
        List<Long> ids = new ArrayList<>();
        list.forEach(n -> ids.add(n.get("buildingId").asLong()));
        return ids;
    }

    /** 기준 위치에서 distance(m)·bearing(°) 만큼 떨어진 곳에 행안부 좌표 건물을 놓아요 */
    private Building at(String name, double distance, double bearing) {
        Building b = saved(TestFixtures.building().aptNm(name).jibun(String.valueOf(1000 + seq++)).build());
        place(b, distance, bearing);
        return b;
    }

    private void place(Building b, double distance, double bearing) {
        double[] p = destination(distance, bearing);
        b.updateLocation(null, null, p[0], p[1], CoordSource.JUSO);
        buildingRepository.flush();
    }

    private Building saved(Building b) {
        return buildingRepository.saveAndFlush(b);
    }

    /** 구면 목적지 공식 — 서버·프론트의 하버사인·초기 방위각과 같은 구(반지름 6,371km) */
    private static double[] destination(double distance, double bearing) {
        double d = distance / EARTH_RADIUS_M;
        double th = Math.toRadians(bearing);
        double p1 = Math.toRadians(LAT);
        double l1 = Math.toRadians(LON);
        double p2 = Math.asin(Math.sin(p1) * Math.cos(d) + Math.cos(p1) * Math.sin(d) * Math.cos(th));
        double l2 = l1 + Math.atan2(Math.sin(th) * Math.sin(d) * Math.cos(p1), Math.cos(d) - Math.sin(p1) * Math.sin(p2));
        return new double[] {Math.toDegrees(p2), Math.toDegrees(l2)};
    }

    private void trade(Building b, LocalDate date, int price, String area, int floor, String cdealType) {
        tradeRepository.saveAndFlush(TestFixtures.trade(b)
                .fingerprint(String.format("%064d", seq++))
                .dealYm(date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")))
                .dealDate(date)
                .dealAmount(price)
                .excluUseAr(new BigDecimal(area))
                .floor(floor)
                .cdealType(cdealType)
                .build());
    }

    private String registerDevice() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/devices")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("anonymousId").asText();
    }

    private void favorite(String anonymousId, long buildingId) throws Exception {
        mockMvc.perform(post("/api/v1/favorites").header("X-Anonymous-Id", anonymousId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"buildingId\":" + buildingId + "}"))
                .andExpect(status().isCreated());
    }
}
