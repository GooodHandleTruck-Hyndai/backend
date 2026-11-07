package com.gooodhandletruck.zippick.domain.building.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingAddress;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingCoordinateResponse;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.building.service.BuildingCoordinateService;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import com.gooodhandletruck.zippick.global.util.KstClock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주변 건물 API (API 2번, GET /api/v1/buildings/nearby) 를 실제 HTTP 요청으로 확인해요. (#35 에서 추가)
 * 카카오 좌표(#13 BuildingCoordinateService)는 가짜로 바꿔서, 건물마다 정해 둔 좌표를 돌려주게 해요.
 * 기준 위치는 도화동 부근(37.5400, 126.9450). 위도 0.001도 ≈ 111m. H2(MySQL 모드), 테스트마다 롤백.
 */
// 카카오 한도 대비 쉬는 시간은 테스트에서 0 (가짜 좌표 서비스라 한도가 없어요)
@SpringBootTest(properties = {"zippick.nearby.kakao-chunk-pause-ms=0", "zippick.nearby.kakao-retry-pause-ms=0"})
@AutoConfigureMockMvc
@Transactional
class BuildingNearbyApiTest {

    private static final String URL = "/api/v1/buildings/nearby";
    private static final double LAT = 37.5400;
    private static final double LON = 126.9450;
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private LegalDongRepository legalDongRepository;

    @MockitoBean
    private BuildingCoordinateService buildingCoordinateService;

    /** 건물 id → 카카오가 돌려줄 좌표. 없으면 found=false */
    private final Map<String, double[]> kakao = new HashMap<>();
    private int seq;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = KstClock.today();
        given(buildingCoordinateService.findCoordinates(anyList())).willAnswer(invocation -> {
            List<BuildingAddress> asked = invocation.getArgument(0);
            return asked.stream().map(a -> {
                double[] p = kakao.get(a.buildingId());
                return p == null ? BuildingCoordinateResponse.notFound(a.buildingId())
                        : BuildingCoordinateResponse.found(a.buildingId(), p[0], p[1]);
            }).toList();
        });
    }

    // ---- 반경·정렬 ----

    @Test
    void 반경_안의_건물만_가까운_순으로_거리와_함께() throws Exception {
        Building far = building("먼단지", LAT + 0.005, LON);     // 약 556m
        Building near = building("가까운단지", LAT + 0.001, LON); // 약 111m
        building("반경밖단지", LAT + 0.02, LON);                   // 약 2.2km

        JsonNode list = buildings(URL + "?lat=" + LAT + "&lon=" + LON + "&radius=1000");

        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("buildingId").asLong()).isEqualTo(near.getId());
        assertThat(list.get(0).get("distanceM").asInt()).isBetween(110, 112);
        assertThat(list.get(1).get("buildingId").asLong()).isEqualTo(far.getId());
        assertThat(list.get(1).get("distanceM").asInt()).isBetween(555, 557);
        assertThat(list.get(0).get("lat").asDouble()).isEqualTo(LAT + 0.001);
        assertThat(list.get(0).get("lon").asDouble()).isEqualTo(LON);
    }

    @Test
    void radius_를_안_보내면_1000m() throws Exception {
        building("900m", LAT + 0.0081, LON);  // 약 901m
        building("1100m", LAT + 0.0099, LON); // 약 1101m

        JsonNode list = buildings(URL + "?lat=" + LAT + "&lon=" + LON);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("name").asText()).isEqualTo("900m");
    }

    @Test
    void 주변에_없으면_빈_목록과_기준일_null() throws Exception {
        building("반경밖", LAT + 0.02, LON);

        mockMvc.perform(get(URL).param("lat", "" + LAT).param("lon", "" + LON).param("radius", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.buildings", hasSize(0)))
                .andExpect(jsonPath("$.data.dataBaseDate").doesNotExist());
    }

    @Test
    void 명세_응답_모양() throws Exception {
        legalDongRepository.save(TestFixtures.legalDong().build()); // 주소의 "서울 마포구" 는 법정동 표에서 와요
        Building b = building("한화오벨리스크", LAT + 0.001, LON);
        trade(b, today.minusDays(10), 59700, null);

        mockMvc.perform(get(URL).param("lat", "" + LAT).param("lon", "" + LON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataBaseDate").exists())
                .andExpect(jsonPath("$.data.buildings[0].buildingId").value(b.getId()))
                .andExpect(jsonPath("$.data.buildings[0].name").value("한화오벨리스크"))
                .andExpect(jsonPath("$.data.buildings[0].type").value("APT"))
                .andExpect(jsonPath("$.data.buildings[0].address").value("서울 마포구 아현동 777"))
                .andExpect(jsonPath("$.data.buildings[0].imageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.buildings[0].latestDeal.price").value(59700))
                .andExpect(jsonPath("$.data.buildings[0].latestDeal.dealDate").value(today.minusDays(10).toString()))
                .andExpect(jsonPath("$.data.buildings[0].latestDeal.areaM2").value(84.97))
                .andExpect(jsonPath("$.data.buildings[0].latestDeal.areaPyeong").value(25.7));
    }

    // ---- latestDeal ----

    @Test
    void latestDeal_은_해제_거래를_빼고_가장_최근_1건() throws Exception {
        Building b = building("단지", LAT + 0.001, LON);
        trade(b, today.minusMonths(3), 100000, null);
        trade(b, today.minusMonths(1), 110000, null);
        trade(b, today.minusDays(5), 999999, "O"); // 해제 — 더 최근이어도 빠져요

        JsonNode deal = buildings(URL + "?lat=" + LAT + "&lon=" + LON).get(0).get("latestDeal");

        assertThat(deal.get("price").asInt()).isEqualTo(110000);
    }

    @Test
    void 최근_1년_거래가_없으면_latestDeal_null() throws Exception {
        Building b = building("단지", LAT + 0.001, LON);
        trade(b, today.minusMonths(13), 100000, null);

        JsonNode first = buildings(URL + "?lat=" + LAT + "&lon=" + LON).get(0);

        assertThat(first.get("latestDeal").isNull()).isTrue();
    }

    // ---- 좌표 ----

    @Test
    void 좌표를_못_찾은_건물은_빠진다() throws Exception {
        building("찾음", LAT + 0.001, LON);
        buildingRepository.save(TestFixtures.building().aptNm("못찾음").build()); // kakao 에 좌표 없음

        JsonNode list = buildings(URL + "?lat=" + LAT + "&lon=" + LON);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("name").asText()).isEqualTo("찾음");
    }

    @Test
    void 첫_시도에_못_찾은_건물은_한_번_더_찾는다() throws Exception {
        // 카카오 한도에 막힌 상황: 처음엔 못 찾음, 두 번째엔 찾음
        Building b = buildingRepository.save(TestFixtures.building().aptNm("한도에막힘").build());
        String id = String.valueOf(b.getId());
        given(buildingCoordinateService.findCoordinates(anyList()))
                .willReturn(List.of(BuildingCoordinateResponse.notFound(id)))
                .willReturn(List.of(BuildingCoordinateResponse.found(id, LAT + 0.001, LON)));

        JsonNode list = buildings(URL + "?lat=" + LAT + "&lon=" + LON);

        assertThat(list).hasSize(1);
        verify(buildingCoordinateService, times(2)).findCoordinates(anyList());
    }

    @Test
    void 다시_찾는_건_한_번뿐이다() throws Exception {
        building("찾음", LAT + 0.001, LON);
        buildingRepository.save(TestFixtures.building().aptNm("주소없음").build()); // 계속 못 찾음

        buildings(URL + "?lat=" + LAT + "&lon=" + LON);

        verify(buildingCoordinateService, times(2)).findCoordinates(anyList()); // 첫 시도 1 + 재시도 1
    }

    @Test
    void 좌표를_하나도_못_찾으면_502_E001() throws Exception {
        buildingRepository.save(TestFixtures.building().aptNm("못찾음").build());

        mockMvc.perform(get(URL).param("lat", "" + LAT).param("lon", "" + LON))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("E001"));
    }

    @Test
    void 행안부_좌표가_저장된_건물은_카카오를_안_부른다() throws Exception {
        Building b = buildingRepository.save(TestFixtures.building().aptNm("행안부").build());
        b.updateLocation(null, null, LAT + 0.001, LON, CoordSource.JUSO);
        buildingRepository.saveAndFlush(b);

        JsonNode list = buildings(URL + "?lat=" + LAT + "&lon=" + LON);

        assertThat(list).hasSize(1);
        verify(buildingCoordinateService, never()).findCoordinates(anyList());
    }

    @Test
    void 출처_없는_저장_좌표는_믿지_않고_카카오로_다시_찾는다() throws Exception {
        Building b = building("샘플", LAT + 0.001, LON);
        b.updateLocation(null, null, 0.0, 0.0, null); // 예전 샘플처럼 출처 없는 엉뚱한 좌표
        buildingRepository.saveAndFlush(b);

        JsonNode list = buildings(URL + "?lat=" + LAT + "&lon=" + LON);

        assertThat(list).hasSize(1); // 카카오 좌표(가까움)로 계산돼서 나와요
    }

    @Test
    void 건물이_30개를_넘으면_30개씩_나눠서_좌표를_찾는다() throws Exception {
        for (int i = 0; i < 31; i++) {
            building("단지" + i, LAT + 0.001, LON);
        }

        JsonNode list = buildings(URL + "?lat=" + LAT + "&lon=" + LON);

        assertThat(list).hasSize(31);
        verify(buildingCoordinateService, times(2)).findCoordinates(anyList());
    }

    // ---- 입력 오류 ----

    @Test
    void radius_범위_밖은_400_C001() throws Exception {
        mockMvc.perform(get(URL).param("lat", "" + LAT).param("lon", "" + LON).param("radius", "99"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(get(URL).param("lat", "" + LAT).param("lon", "" + LON).param("radius", "3001"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void radius_경계값_100과_3000은_된다() throws Exception {
        mockMvc.perform(get(URL).param("lat", "" + LAT).param("lon", "" + LON).param("radius", "100"))
                .andExpect(status().isOk());
        mockMvc.perform(get(URL).param("lat", "" + LAT).param("lon", "" + LON).param("radius", "3000"))
                .andExpect(status().isOk());
    }

    @Test
    void lat_lon_이_없거나_범위_밖이면_400_C001() throws Exception {
        mockMvc.perform(get(URL).param("lon", "" + LON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(get(URL).param("lat", "" + LAT))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(get(URL).param("lat", "91").param("lon", "" + LON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(get(URL).param("lat", "" + LAT).param("lon", "181"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(get(URL).param("lat", "abc").param("lon", "" + LON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void nearby_는_건물_상세_경로로_잡히지_않는다() throws Exception {
        // /{buildingId} 로 잡히면 "nearby" 를 숫자로 못 바꿔서 C001 이 나와요 — 200 이면 주변 건물로 간 거예요
        mockMvc.perform(get(URL).param("lat", "" + LAT).param("lon", "" + LON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buildings").isArray());
    }

    // ---- 도우미 ----

    private Building building(String name, double lat, double lon) {
        Building b = buildingRepository.save(TestFixtures.building().aptNm(name).build());
        kakao.put(String.valueOf(b.getId()), new double[] {lat, lon});
        return b;
    }

    private void trade(Building b, LocalDate dealDate, int price, String cdealType) {
        tradeRepository.save(TestFixtures.trade(b)
                .fingerprint(String.format("%064d", ++seq))
                .dealYm(dealDate.format(YM))
                .dealDate(dealDate)
                .dealAmount(price)
                .cdealType(cdealType)
                .build());
    }

    private JsonNode buildings(String url) throws Exception {
        String body = mockMvc.perform(get(url)).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body).get("data").get("buildings");
    }
}
