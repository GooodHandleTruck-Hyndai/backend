package com.gooodhandletruck.zippick.domain.building.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실거래 내역 API (API 8번, GET /api/v1/buildings/{buildingId}/transactions) 를 실제 HTTP 요청으로 확인해요. (#16 에서 추가)
 * 응답 필드·기본값·에러 코드는 팀 API 명세 8번 기준이에요. H2(MySQL 모드), 테스트마다 롤백.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BuildingTransactionsApiTest {

    private static final String URL = "/api/v1/buildings/{buildingId}/transactions";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private TradeRepository tradeRepository;

    private Building building;
    private int fingerprintSeq;

    @BeforeEach
    void setUp() {
        building = buildingRepository.save(TestFixtures.building().build());
    }

    // ---- 정상 응답 ----

    @Test
    void 명세의_필드를_모두_담아_응답한다() throws Exception {
        saveTrade(LocalDate.of(2026, 10, 12), 125000, "705", null);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.transactions", hasSize(1)))
                .andExpect(jsonPath("$.data.transactions[0].dealDate").value("2026-10-12"))
                .andExpect(jsonPath("$.data.transactions[0].areaM2").value(84.97))
                .andExpect(jsonPath("$.data.transactions[0].areaPyeong").value(25.7))
                .andExpect(jsonPath("$.data.transactions[0].floor").value(12))
                .andExpect(jsonPath("$.data.transactions[0].dong").value("705"))
                .andExpect(jsonPath("$.data.transactions[0].price").value(125000))
                // 파라미터 없이 부르면 명세 기본값
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 항목_형식이_건물_상세의_recentTransactions_와_같다() throws Exception {
        saveTrade(LocalDate.of(2026, 9, 3), 98000, null, null);

        JsonNode detail = data(mockMvc.perform(get("/api/v1/buildings/{id}", building.getId()))
                .andReturn().getResponse().getContentAsString()).get("recentTransactions").get(0);
        JsonNode list = data(mockMvc.perform(get(URL, building.getId()))
                .andReturn().getResponse().getContentAsString()).get("transactions").get(0);

        assertThat(list).isEqualTo(detail); // 칸 이름·값이 그대로 같아야 앱이 같은 카드로 그려요
        assertThat(list.get("dong").isNull()).isTrue(); // 등기 전 → null 로 내려가요 (빠지면 안 돼요)
    }

    @Test
    void 최신순이고_같은_날이면_나중에_들어온_거래가_먼저다() throws Exception {
        saveTrade(LocalDate.of(2026, 8, 1), 90000, null, null);
        saveTrade(LocalDate.of(2026, 10, 1), 91000, null, null);
        saveTrade(LocalDate.of(2026, 10, 1), 92000, null, null);
        saveTrade(LocalDate.of(2026, 9, 1), 93000, null, null);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.transactions[*].price", contains(92000, 91000, 93000, 90000)));
    }

    @Test
    void 해제된_거래는_빠진다() throws Exception {
        saveTrade(LocalDate.of(2026, 10, 12), 125000, null, "O");
        saveTrade(LocalDate.of(2026, 10, 1), 120000, null, null);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.transactions[*].price", contains(120000)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 다른_건물_거래는_섞이지_않는다() throws Exception {
        Building other = buildingRepository.save(TestFixtures.building().aptNm("마포래미안푸르지오2단지").build());
        saveTradeFor(other, LocalDate.of(2026, 10, 12), 200000, null);
        saveTrade(LocalDate.of(2026, 10, 1), 120000, null, null);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.transactions[*].price", contains(120000)));
    }

    // ---- 쪽 나누기 ----

    @Test
    void 거래_25건을_20건씩_나누면_1쪽은_hasNext_true_2쪽은_5건_false() throws Exception {
        saveDays(25);

        mockMvc.perform(get(URL, building.getId()).param("page", "0").param("size", "20"))
                .andExpect(jsonPath("$.data.transactions", hasSize(20)))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mockMvc.perform(get(URL, building.getId()).param("page", "1").param("size", "20"))
                .andExpect(jsonPath("$.data.transactions", hasSize(5)))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 딱_size_건이면_hasNext_는_false() throws Exception {
        // 경계: size+1 건을 읽는 방식이라 정확히 20건이면 다음 쪽이 없어야 해요
        saveDays(20);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.transactions", hasSize(20)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 같은_날_거래가_쪽_경계에_걸쳐도_겹치거나_빠지지_않는다() throws Exception {
        for (int i = 0; i < 7; i++) {
            saveTrade(LocalDate.of(2026, 10, 1), 100000 + i, null, null); // 전부 같은 날
        }

        List<Integer> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonNode data = data(mockMvc.perform(get(URL, building.getId())
                    .param("page", String.valueOf(page)).param("size", "3"))
                    .andReturn().getResponse().getContentAsString());
            data.get("transactions").forEach(t -> seen.add(t.get("price").asInt()));
        }

        assertThat(seen).containsExactly(100006, 100005, 100004, 100003, 100002, 100001, 100000);
    }

    @Test
    void 해제_거래는_쪽_계산에서도_빠진다() throws Exception {
        // 정상 3건 + 해제 2건, size 3 → 1쪽에 정상 3건, 다음 쪽 없음
        saveTrade(LocalDate.of(2026, 10, 5), 1, null, "O");
        saveTrade(LocalDate.of(2026, 10, 4), 2, null, null);
        saveTrade(LocalDate.of(2026, 10, 3), 3, null, "O");
        saveTrade(LocalDate.of(2026, 10, 2), 4, null, null);
        saveTrade(LocalDate.of(2026, 10, 1), 5, null, null);

        mockMvc.perform(get(URL, building.getId()).param("size", "3"))
                .andExpect(jsonPath("$.data.transactions[*].price", contains(2, 4, 5)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void size_최대_50_이면_정상이다() throws Exception {
        saveDays(51);

        mockMvc.perform(get(URL, building.getId()).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions", hasSize(50)))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void 거래가_없는_건물은_빈_목록이다() throws Exception {
        mockMvc.perform(get(URL, building.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions", hasSize(0)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 마지막_쪽을_넘어가도_빈_목록이다() throws Exception {
        saveDays(3);

        mockMvc.perform(get(URL, building.getId()).param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions", hasSize(0)))
                .andExpect(jsonPath("$.data.page").value(5))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    // ---- 에러 ----

    @ParameterizedTest(name = "page={0}, size={1} → 400 C001")
    @CsvSource({"-1, 20", "0, 0", "0, -5", "0, 51", "0, 1000"})
    void page_size_범위_오류는_400_C001(int page, int size) throws Exception {
        mockMvc.perform(get(URL, building.getId())
                        .param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.data").doesNotExist()); // 팀 공통 응답은 실패 시 data 칸을 빼요
    }

    @Test
    void 없는_건물은_404_B001() throws Exception {
        mockMvc.perform(get(URL, 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("B001"));
    }

    @Test
    void 범위_오류와_없는_건물이_겹치면_C001_이_먼저다() throws Exception {
        mockMvc.perform(get(URL, 999_999L).param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    // ---- 도우미 ----

    private JsonNode data(String body) throws Exception {
        return new ObjectMapper().readTree(body).get("data");
    }

    /** 날짜가 하루씩 다른 거래 n 건 */
    private void saveDays(int n) {
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < n; i++) {
            saveTrade(start.plusDays(i), 100000 + i, null, null);
        }
    }

    private void saveTrade(LocalDate dealDate, int price, String dong, String cdealType) {
        saveTradeFor(building, dealDate, price, dong, cdealType);
    }

    private void saveTradeFor(Building target, LocalDate dealDate, int price, String cdealType) {
        saveTradeFor(target, dealDate, price, null, cdealType);
    }

    private void saveTradeFor(Building target, LocalDate dealDate, int price, String dong, String cdealType) {
        tradeRepository.save(TestFixtures.trade(target)
                .fingerprint(String.format("%064d", ++fingerprintSeq))
                .dealDate(dealDate)
                .dealAmount(price)
                .excluUseAr(new BigDecimal("84.97"))
                .aptDong(dong)
                .cdealType(cdealType)
                .build());
    }
}
