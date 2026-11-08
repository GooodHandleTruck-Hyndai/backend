package com.gooodhandletruck.zippick.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingAddress;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import com.gooodhandletruck.zippick.domain.trade.client.MolitFixtures;
import com.gooodhandletruck.zippick.domain.trade.client.MolitTradeItem;
import com.gooodhandletruck.zippick.domain.trade.entity.CollectRun;
import com.gooodhandletruck.zippick.domain.trade.entity.CollectStatus;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import com.gooodhandletruck.zippick.global.util.KstClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * 실제 앱 사용 흐름 · 재수집 뒤 반영 · 외부 장애 · 데이터 경계 상황 (#45 에서 추가).
 * 실제 마포 2026-08 응답(121건)을 실제 적재 서비스로 넣은 상태에서 A 담당 API 를 앱 순서대로 불러요.
 */
@SpringBootTest(properties = {"zippick.nearby.kakao-chunk-pause-ms=0", "zippick.nearby.kakao-retry-pause-ms=0"})
@AutoConfigureMockMvc
class AppFlowScenarioTest extends ScenarioSupport {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ---- ① 앱 사용 흐름 ----

    @Test
    void 주변_건물에서_고른_건물의_상세_더보기_추이_값이_서로_맞다() throws Exception {
        long id = busiestBuildingId();
        JsonNode card = findCard(getData(nearbyUrl(3000)).get("buildings"), id);
        JsonNode detail = getData("/api/v1/buildings/" + id);
        JsonNode more = getData("/api/v1/buildings/" + id + "/transactions?page=0&size=3");
        JsonNode trend = getData("/api/v1/buildings/" + id + "/price-trend");

        // 주변 카드의 최근 거래 = 상세의 첫 거래 = 더보기 첫 줄
        JsonNode first = detail.get("recentTransactions").get(0);
        assertThat(card.get("latestDeal").get("price").asInt()).isEqualTo(first.get("price").asInt());
        assertThat(card.get("latestDeal").get("dealDate").asText()).isEqualTo(first.get("dealDate").asText());
        assertThat(card.get("latestDeal").get("areaM2").decimalValue())
                .isEqualByComparingTo(first.get("areaM2").decimalValue());
        for (int i = 0; i < detail.get("recentTransactions").size(); i++) {
            assertThat(more.get("transactions").get(i)).isEqualTo(detail.get("recentTransactions").get(i));
        }
        // 이름·유형·좌표도 카드와 상세가 같다
        assertThat(card.get("name").asText()).isEqualTo(detail.get("name").asText());
        assertThat(card.get("type").asText()).isEqualTo(detail.get("type").asText());
        assertThat(card.get("lat").asDouble()).isEqualTo(detail.get("lat").asDouble());
        assertThat(card.get("lon").asDouble()).isEqualTo(detail.get("lon").asDouble());
        // 최근 거래가 있는 달은 추이에 평균가와 건수가 있다
        String ym = first.get("dealDate").asText().substring(0, 7);
        JsonNode month = findMonth(trend.get("months"), ym);
        assertThat(month.get("avgPrice").isNull()).isFalse();
        assertThat(month.get("count").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 주변_건물_목록의_모든_건물을_상세로_열_수_있고_주소와_유형이_같다() throws Exception {
        JsonNode cards = getData(nearbyUrl(3000)).get("buildings");
        assertThat(cards.size()).isPositive();
        for (JsonNode card : cards) {
            long id = card.get("buildingId").asLong();
            JsonNode detail = getData("/api/v1/buildings/" + id);
            assertThat(detail.get("name").asText()).isEqualTo(card.get("name").asText());
            assertThat(detail.get("jibunAddress").asText()).isEqualTo(card.get("address").asText());
            assertThat(detail.get("type").asText()).isEqualTo(card.get("type").asText());
        }
    }

    @Test
    void 더보기를_끝까지_넘기면_건물의_해제_안_된_거래를_빠짐없이_한_번씩_받는다() throws Exception {
        long id = busiestBuildingId();
        long expected = tradeRepository.findAll().stream()
                .filter(t -> t.getBuilding().getId() == id && t.getCdealType() == null).count();

        List<String> seen = new ArrayList<>();
        int page = 0;
        JsonNode slice;
        do {
            slice = getData("/api/v1/buildings/" + id + "/transactions?page=" + page++ + "&size=2");
            slice.get("transactions").forEach(t -> seen.add(t.toString()));
        } while (slice.get("hasNext").asBoolean());

        assertThat(seen).hasSize((int) expected);
    }

    @Test
    void 저장하면_상세가_saved_true_목록에_보이고_취소하면_둘_다_원래대로() throws Exception {
        long id = busiestBuildingId();
        String me = registerDevice();

        assertThat(getData("/api/v1/buildings/" + id, me).get("saved").asBoolean()).isFalse();
        assertThat(save(me, id)).isEqualTo(201);
        assertThat(getData("/api/v1/buildings/" + id, me).get("saved").asBoolean()).isTrue();
        assertThat(getData("/api/v1/favorites", me).toString()).contains("\"buildingId\":" + id);

        assertThat(unsave(me, id)).isEqualTo(200);
        assertThat(getData("/api/v1/buildings/" + id, me).get("saved").asBoolean()).isFalse();
        assertThat(getData("/api/v1/favorites", me).toString()).doesNotContain("\"buildingId\":" + id);
    }

    @Test
    void 다른_기기의_저장은_내_상세에_영향이_없다() throws Exception {
        long id = busiestBuildingId();
        String other = registerDevice();
        String me = registerDevice();
        save(other, id);

        assertThat(getData("/api/v1/buildings/" + id, me).get("saved").asBoolean()).isFalse();
        assertThat(getData("/api/v1/buildings/" + id, other).get("saved").asBoolean()).isTrue();
    }

    // ---- ② 재수집 뒤 반영 ----

    @Test
    void 재수집에서_최근_거래가_해제되면_상세_더보기_추이_주변_카드에서_바로_빠진다() throws Exception {
        long id = busiestBuildingId();
        JsonNode before = getData("/api/v1/buildings/" + id).get("recentTransactions").get(0);
        String ym = before.get("dealDate").asText().substring(0, 7);
        int countBefore = findMonth(getData("/api/v1/buildings/" + id + "/price-trend").get("months"), ym)
                .get("count").asInt();

        // 국토부가 같은 달을 다시 보내면서 그 거래에 해제 표시(O)를 붙인 상황
        Trade latest = latestTradeOf(id);
        String aptNm = buildingRepository.findById(id).orElseThrow().getAptNm();
        List<MolitTradeItem> resent = new ArrayList<>();
        boolean marked = false; // 값이 똑같은 거래가 두 건일 수 있어서 한 건만 해제
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

        JsonNode after = getData("/api/v1/buildings/" + id);
        assertThat(after.get("recentTransactions").toString()).doesNotContain(before.toString());
        assertThat(getData("/api/v1/buildings/" + id + "/transactions?page=0&size=50").get("transactions").toString())
                .doesNotContain(before.toString());
        assertThat(findMonth(getData("/api/v1/buildings/" + id + "/price-trend").get("months"), ym)
                .get("count").asInt()).isEqualTo(countBefore - 1);
        JsonNode card = findCard(getData(nearbyUrl(3000)).get("buildings"), id);
        if (!card.get("latestDeal").isNull()) {
            assertThat(card.get("latestDeal").get("dealDate").asText() + card.get("latestDeal").get("price"))
                    .isEqualTo(after.get("recentTransactions").get(0).get("dealDate").asText()
                            + after.get("recentTransactions").get(0).get("price"));
        }
    }

    @Test
    void 이번_달_새_거래가_들어오면_상세_첫_거래_주변_카드_추이_이번_달에_바로_보인다() throws Exception {
        long id = busiestBuildingId();
        Trade latest = latestTradeOf(id);
        LocalDate today = KstClock.today();
        String thisYm = today.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String aptNm = buildingRepository.findById(id).orElseThrow().getAptNm();
        MolitTradeItem template = mapo.stream().filter(i -> sameDeal(i, latest, aptNm)).findFirst().orElseThrow();
        MolitTradeItem newDeal = MolitFixtures.copy(template, Map.of(
                "dealYear", String.valueOf(today.getYear()),
                "dealMonth", String.valueOf(today.getMonthValue()),
                "dealDay", String.valueOf(today.getDayOfMonth()),
                "dealAmount", "123,456",
                "cdealType", "", "cdealDay", ""));

        assertThat(collect(thisYm, List.of(newDeal)).getStatus()).isEqualTo(CollectStatus.SUCCESS);

        JsonNode first = getData("/api/v1/buildings/" + id).get("recentTransactions").get(0);
        assertThat(first.get("price").asInt()).isEqualTo(123456);
        assertThat(first.get("dealDate").asText()).isEqualTo(today.toString());
        JsonNode card = findCard(getData(nearbyUrl(3000)).get("buildings"), id);
        assertThat(card.get("latestDeal").get("price").asInt()).isEqualTo(123456);
        JsonNode thisMonth = findMonth(getData("/api/v1/buildings/" + id + "/price-trend").get("months"),
                today.toString().substring(0, 7));
        assertThat(thisMonth.get("count").asInt()).isEqualTo(1);
        assertThat(thisMonth.get("avgPrice").asInt()).isEqualTo(123456);
    }

    @Test
    void 재수집이_0건으로_와도_기존_화면_데이터가_그대로다() throws Exception {
        long id = busiestBuildingId();
        String before = getData("/api/v1/buildings/" + id).get("recentTransactions").toString();

        CollectRun run = collect(YM, List.of());

        assertThat(run.getStatus()).isEqualTo(CollectStatus.SKIPPED);
        assertThat(getData("/api/v1/buildings/" + id).get("recentTransactions").toString()).isEqualTo(before);
    }

    // ---- ③ 외부 장애 (카카오 좌표) ----

    @Test
    void 카카오_좌표_조회가_예상_못한_예외를_던져도_상세는_200이고_좌표만_null() throws Exception {
        long id = busiestBuildingId();
        given(buildingCoordinateService.findCoordinate(any(BuildingAddress.class)))
                .willThrow(new IllegalStateException("카카오 연결 오류 재현"));

        mockMvc.perform(get("/api/v1/buildings/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lat").isEmpty())
                .andExpect(jsonPath("$.data.recentTransactions").isNotEmpty());
    }

    @Test
    void 카카오_좌표_조회가_예상_못한_예외를_던지면_주변_건물은_500이_아니라_E001() throws Exception {
        given(buildingCoordinateService.findCoordinates(anyList()))
                .willThrow(new IllegalStateException("카카오 스레드 풀 거부 재현"));

        mockMvc.perform(get(nearbyUrl(1000)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("E001"));
    }

    @Test
    void 카카오가_일부_덩어리에서만_예외를_던지면_나머지_건물로_주변_건물을_보여준다() throws Exception {
        // 첫 덩어리(30개)만 실패, 나머지는 정상
        given(buildingCoordinateService.findCoordinates(anyList()))
                .willThrow(new IllegalStateException("첫 덩어리 실패"))
                .willAnswer(inv -> ((List<BuildingAddress>) inv.getArgument(0)).stream()
                        .map(ScenarioSupport::found).toList());

        mockMvc.perform(get(nearbyUrl(3000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buildings").isNotEmpty());
    }

    // ---- ④ 데이터 경계 ----

    @Test
    void 거래가_하나도_없는_건물은_네_API_가_모순_없이_빈_값을_준다() throws Exception {
        Building empty = buildingRepository.save(TestFixtures.building().aptNm("거래없는단지").umdNm("도화동").jibun("1").build());
        assertEmptyEverywhere(empty.getId());
    }

    @Test
    void 해제_거래만_있는_건물은_거래_없는_건물과_똑같이_보인다() throws Exception {
        Building b = buildingRepository.save(TestFixtures.building().aptNm("해제만단지").umdNm("도화동").jibun("2").build());
        LocalDate d = KstClock.today().minusMonths(1);
        tradeRepository.save(TestFixtures.trade(b).fingerprint("e".repeat(64)).dealYm(d.format(DateTimeFormatter.ofPattern("yyyyMM")))
                .dealDate(d).cdealType("O").build());
        assertEmptyEverywhere(b.getId());
    }

    @Test
    void 오피스텔_건물은_상세와_주변_카드_모두_OFFICETEL() throws Exception {
        Building o = buildingRepository.save(TestFixtures.building().buildingType(BuildingType.OFFICETEL)
                .aptNm("오피스텔단지").umdNm("도화동").jibun("3").build());
        LocalDate d = KstClock.today().minusMonths(1);
        tradeRepository.save(TestFixtures.trade(o).fingerprint("f".repeat(64))
                .dealYm(d.format(DateTimeFormatter.ofPattern("yyyyMM"))).dealDate(d).build());

        assertThat(getData("/api/v1/buildings/" + o.getId()).get("type").asText()).isEqualTo("OFFICETEL");
        assertThat(findCard(getData(nearbyUrl(3000)).get("buildings"), o.getId()).get("type").asText())
                .isEqualTo("OFFICETEL");
    }

    @Test
    void 아주_큰_page_는_빈_목록과_hasNext_false() throws Exception {
        long id = busiestBuildingId();
        JsonNode slice = getData("/api/v1/buildings/" + id + "/transactions?page=100000&size=50");
        assertThat(slice.get("transactions")).isEmpty();
        assertThat(slice.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void 같은_날_거래가_여러_건이어도_쪽을_넘길_때_겹치거나_빠지지_않는다() throws Exception {
        Building b = buildingRepository.save(TestFixtures.building().aptNm("같은날단지").umdNm("도화동").jibun("4").build());
        LocalDate d = KstClock.today().minusDays(3);
        for (int i = 0; i < 7; i++) {
            tradeRepository.save(TestFixtures.trade(b).fingerprint(String.format("%064d", 900 + i))
                    .dealYm(d.format(DateTimeFormatter.ofPattern("yyyyMM"))).dealDate(d)
                    .dealAmount(100000 + i).floor(i + 1).build());
        }
        Set<Integer> prices = new HashSet<>();
        int total = 0;
        for (int page = 0; page < 4; page++) {
            for (JsonNode t : getData("/api/v1/buildings/" + b.getId() + "/transactions?page=" + page + "&size=2")
                    .get("transactions")) {
                prices.add(t.get("price").asInt());
                total++;
            }
        }
        assertThat(total).isEqualTo(7);
        assertThat(prices).hasSize(7);
    }

    // ---- 도우미 ----

    private void assertEmptyEverywhere(long id) throws Exception {
        JsonNode detail = getData("/api/v1/buildings/" + id);
        assertThat(detail.get("recentTransactions")).isEmpty();
        assertThat(detail.get("hasMoreTransactions").asBoolean()).isFalse();
        JsonNode more = getData("/api/v1/buildings/" + id + "/transactions");
        assertThat(more.get("transactions")).isEmpty();
        assertThat(more.get("hasNext").asBoolean()).isFalse();
        JsonNode months = getData("/api/v1/buildings/" + id + "/price-trend").get("months");
        assertThat(months).hasSize(12);
        months.forEach(m -> {
            assertThat(m.get("avgPrice").isNull()).isTrue();
            assertThat(m.get("count").asInt()).isZero();
        });
        assertThat(findCard(getData(nearbyUrl(3000)).get("buildings"), id).get("latestDeal").isNull()).isTrue();
    }

    private JsonNode findCard(JsonNode cards, long id) {
        for (JsonNode c : cards) {
            if (c.get("buildingId").asLong() == id) {
                return c;
            }
        }
        throw new AssertionError("주변 건물 목록에 건물 " + id + " 이 없어요");
    }

    private JsonNode findMonth(JsonNode months, String ym) {
        for (JsonNode m : months) {
            if (m.get("yearMonth").asText().equals(ym)) {
                return m;
            }
        }
        throw new AssertionError("가격 추이에 " + ym + " 이 없어요");
    }

    private Trade latestTradeOf(long buildingId) {
        return tradeRepository.findTop4ByBuildingIdAndCdealTypeIsNullOrderByDealDateDescIdDesc(buildingId).get(0);
    }

    /** 픽스처 항목이 DB 의 그 거래와 같은 거래인지 (계약일·금액·층·면적) */
    private boolean sameDeal(MolitTradeItem item, Trade trade, String aptNm) {
        LocalDate date = LocalDate.of(Integer.parseInt(item.dealYear().trim()),
                Integer.parseInt(item.dealMonth().trim()), Integer.parseInt(item.dealDay().trim()));
        return date.equals(trade.getDealDate())
                && Integer.parseInt(item.dealAmount().replace(",", "").trim()) == trade.getDealAmount()
                && Integer.parseInt(item.floor().trim()) == trade.getFloor()
                && new BigDecimal(item.excluUseAr().trim()).compareTo(trade.getExcluUseAr()) == 0
                && item.aptNm().trim().equals(aptNm);
    }
}
