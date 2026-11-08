package com.gooodhandletruck.zippick.scenario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingAddress;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingCoordinateResponse;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.building.service.BuildingCoordinateService;
import com.gooodhandletruck.zippick.domain.device.repository.DeviceRepository;
import com.gooodhandletruck.zippick.domain.favorite.repository.FavoriteRepository;
import com.gooodhandletruck.zippick.domain.trade.client.MolitClient;
import com.gooodhandletruck.zippick.domain.trade.client.MolitDataset;
import com.gooodhandletruck.zippick.domain.trade.client.MolitFixtures;
import com.gooodhandletruck.zippick.domain.trade.client.MolitMonthResult;
import com.gooodhandletruck.zippick.domain.trade.client.MolitTradeItem;
import com.gooodhandletruck.zippick.domain.trade.entity.CollectRun;
import com.gooodhandletruck.zippick.domain.trade.repository.CollectRunRepository;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import com.gooodhandletruck.zippick.domain.trade.service.TradeIngestService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 상황별 테스트 공통 준비 (#45 에서 추가).
 *
 * 실제 앱처럼 "국토부 → 적재 → API" 흐름을 재현하려고, 국토부 클라이언트만 가짜로 바꾸고
 * 실제 마포 2026-08 응답 픽스처(121건)를 실제 적재 서비스(TradeIngestService)로 넣어요.
 * 카카오 좌표는 건물 id 로 정해지는 가짜 좌표(마포 도화동 부근, 반경 1km 안)를 돌려줘요.
 *
 * 동시 요청과 재수집 트랜잭션을 재현해야 해서 테스트 트랜잭션을 쓰지 않고(데이터가 실제로 커밋돼야 다른 스레드가 봄),
 * 테스트마다 표를 직접 비워요.
 */
abstract class ScenarioSupport {

    static final String SGG = "11440";
    static final String YM = "202608";
    /** 가짜 좌표의 중심 (마포 도화동 부근) */
    static final double CENTER_LAT = 37.5404;
    static final double CENTER_LON = 126.9455;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    TradeIngestService tradeIngestService;
    @Autowired
    TradeRepository tradeRepository;
    @Autowired
    BuildingRepository buildingRepository;
    @Autowired
    CollectRunRepository collectRunRepository;
    @Autowired
    FavoriteRepository favoriteRepository;
    @Autowired
    DeviceRepository deviceRepository;

    @MockitoBean
    MolitClient molitClient;
    @MockitoBean
    BuildingCoordinateService buildingCoordinateService;

    final ObjectMapper objectMapper = new ObjectMapper();
    List<MolitTradeItem> mapo;

    @BeforeEach
    void loadMapo() {
        cleanUp();
        mapo = MolitFixtures.mapo202608().items();
        collect(YM, mapo);
        givenKakaoFindsEveryBuilding();
    }

    @AfterEach
    void cleanUp() {
        favoriteRepository.deleteAllInBatch(); // 저장이 기기·건물을 가리켜서 먼저
        tradeRepository.deleteAllInBatch(); // 거래가 건물을 가리켜서 건물보다 먼저
        collectRunRepository.deleteAllInBatch();
        buildingRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    /** 국토부가 이 목록을 보낸 것처럼 실제 적재 서비스로 한 달 치를 넣어요 (totalCount = 건수) */
    CollectRun collect(String dealYm, List<MolitTradeItem> items) {
        MolitMonthResult result = MolitFixtures.result(SGG, dealYm, items.size(), items);
        given(molitClient.fetchMonth(eq(MolitDataset.APT), eq(SGG), eq(dealYm))).willReturn(result);
        return tradeIngestService.collectMonth(SGG, dealYm);
    }

    /** 카카오가 모든 건물 좌표를 찾는 상황. 좌표는 건물 id 로 정해져서 같은 건물은 항상 같은 좌표 */
    void givenKakaoFindsEveryBuilding() {
        given(buildingCoordinateService.findCoordinate(any(BuildingAddress.class)))
                .willAnswer(inv -> found(inv.getArgument(0)));
        given(buildingCoordinateService.findCoordinates(anyList()))
                .willAnswer(inv -> ((List<BuildingAddress>) inv.getArgument(0)).stream()
                        .map(ScenarioSupport::found).toList());
    }

    static BuildingCoordinateResponse found(BuildingAddress address) {
        long id = Long.parseLong(address.buildingId());
        // 중심에서 위도·경도로 각각 최대 약 0.006도(약 600m) 안쪽
        return BuildingCoordinateResponse.found(address.buildingId(),
                CENTER_LAT + (id % 13) * 0.0005, CENTER_LON + (id % 11) * 0.0005);
    }

    // ---- HTTP 도우미 ----

    JsonNode getData(String url) throws Exception {
        return data(mockMvc.perform(get(url)).andReturn());
    }

    JsonNode getData(String url, String anonymousId) throws Exception {
        return data(mockMvc.perform(get(url).header("X-Anonymous-Id", anonymousId)).andReturn());
    }

    JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    String registerDevice() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/devices")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        return data(result).get("anonymousId").asText();
    }

    int save(String anonymousId, long buildingId) throws Exception {
        return mockMvc.perform(post("/api/v1/favorites").header("X-Anonymous-Id", anonymousId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"buildingId\":" + buildingId + "}"))
                .andReturn().getResponse().getStatus();
    }

    int unsave(String anonymousId, long buildingId) throws Exception {
        return mockMvc.perform(delete("/api/v1/favorites/" + buildingId).header("X-Anonymous-Id", anonymousId))
                .andReturn().getResponse().getStatus();
    }

    String nearbyUrl(int radius) {
        return "/api/v1/buildings/nearby?lat=" + CENTER_LAT + "&lon=" + CENTER_LON + "&radius=" + radius;
    }

    /** 거래가 가장 많은 건물 id (흐름 테스트에서 값 비교가 의미 있게) */
    long busiestBuildingId() {
        return tradeRepository.findAll().stream()
                .filter(t -> t.getCdealType() == null)
                .collect(java.util.stream.Collectors.groupingBy(t -> t.getBuilding().getId(),
                        java.util.stream.Collectors.counting()))
                .entrySet().stream().max(java.util.Map.Entry.comparingByValue()).orElseThrow().getKey();
    }
}
