package com.gooodhandletruck.zippick.domain.building.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.gooodhandletruck.zippick.global.util.KstClock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가격 추이 API (API 13번, GET /api/v1/buildings/{buildingId}/price-trend) 를 실제 HTTP 요청으로 확인해요. (#26 에서 추가)
 * 기간은 "이번 달 포함 최근 12개월"이라 테스트도 오늘(KST) 기준으로 달을 계산해요. H2(MySQL 모드), 테스트마다 롤백.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BuildingPriceTrendApiTest {

    private static final String URL = "/api/v1/buildings/{buildingId}/price-trend";
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private TradeRepository tradeRepository;

    private Building building;
    private int fingerprintSeq;
    private YearMonth thisMonth;

    @BeforeEach
    void setUp() {
        building = buildingRepository.save(TestFixtures.building().build());
        thisMonth = YearMonth.from(KstClock.today());
    }

    // ---- 모양 ----

    @Test
    void 거래가_없어도_오래된_달부터_12개를_채운다() throws Exception {
        JsonNode months = months(building.getId());

        assertThat(months).hasSize(12);
        assertThat(months.get(0).get("yearMonth").asText()).isEqualTo(thisMonth.minusMonths(11).toString());
        assertThat(months.get(11).get("yearMonth").asText()).isEqualTo(thisMonth.toString()); // "2026-11" 형식
        months.forEach(m -> {
            assertThat(m.get("avgPrice").isNull()).isTrue(); // 빠지지 않고 null 로 와야 앱이 선을 끊어요
            assertThat(m.get("count").asInt()).isZero();
        });
    }

    @Test
    void 명세_응답_모양() throws Exception {
        save(thisMonth, 125000, null);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.months", hasSize(12)))
                .andExpect(jsonPath("$.data.months[11].yearMonth").value(thisMonth.toString()))
                .andExpect(jsonPath("$.data.months[11].avgPrice").value(125000))
                .andExpect(jsonPath("$.data.months[11].count").value(1));
    }

    // ---- 계산 ----

    @Test
    void 달마다_평균을_만원_정수로_반올림한다() throws Exception {
        save(thisMonth.minusMonths(1), 100000, null);
        save(thisMonth.minusMonths(1), 100001, null); // 평균 100000.5 → 100001
        save(thisMonth.minusMonths(2), 1, null);
        save(thisMonth.minusMonths(2), 1, null);
        save(thisMonth.minusMonths(2), 2, null);      // 평균 1.33 → 1

        JsonNode months = months(building.getId());

        assertThat(months.get(10).get("avgPrice").asInt()).isEqualTo(100001);
        assertThat(months.get(10).get("count").asInt()).isEqualTo(2);
        assertThat(months.get(9).get("avgPrice").asInt()).isEqualTo(1);
        assertThat(months.get(9).get("count").asInt()).isEqualTo(3);
    }

    @Test
    void 해제된_거래는_평균과_건수에서_빠진다() throws Exception {
        save(thisMonth, 100000, null);
        save(thisMonth, 900000, "O");

        JsonNode last = months(building.getId()).get(11);

        assertThat(last.get("avgPrice").asInt()).isEqualTo(100000);
        assertThat(last.get("count").asInt()).isEqualTo(1);
    }

    @Test
    void 해제_거래만_있는_달은_거래_없는_달과_같다() throws Exception {
        save(thisMonth, 900000, "O");

        JsonNode last = months(building.getId()).get(11);

        assertThat(last.get("avgPrice").isNull()).isTrue();
        assertThat(last.get("count").asInt()).isZero();
    }

    @Test
    void 기간_밖_거래와_다른_건물_거래는_섞이지_않는다() throws Exception {
        save(thisMonth.minusMonths(11), 50000, null);  // 첫 달 — 포함
        save(thisMonth.minusMonths(12), 70000, null);  // 13개월 전 — 제외
        Building other = buildingRepository.save(TestFixtures.building().aptNm("다른단지").build());
        saveFor(other, thisMonth.minusMonths(11), 999999, null);

        JsonNode months = months(building.getId());

        assertThat(months.get(0).get("avgPrice").asInt()).isEqualTo(50000);
        assertThat(months.get(0).get("count").asInt()).isEqualTo(1);
        int total = 0;
        for (JsonNode m : months) {
            total += m.get("count").asInt();
        }
        assertThat(total).isEqualTo(1);
    }

    // ---- 에러 ----

    @Test
    void 없는_건물은_404_B001() throws Exception {
        mockMvc.perform(get(URL, 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("B001"));
    }

    // ---- 도우미 ----

    private JsonNode months(Long buildingId) throws Exception {
        String body = mockMvc.perform(get(URL, buildingId)).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body).get("data").get("months");
    }

    private void save(YearMonth ym, int price, String cdealType) {
        saveFor(building, ym, price, cdealType);
    }

    private void saveFor(Building target, YearMonth ym, int price, String cdealType) {
        tradeRepository.save(TestFixtures.trade(target)
                .fingerprint(String.format("%064d", ++fingerprintSeq))
                .dealYm(ym.format(YM))
                .dealDate(ym.atDay(1))
                .dealAmount(price)
                .cdealType(cdealType)
                .build());
    }
}
