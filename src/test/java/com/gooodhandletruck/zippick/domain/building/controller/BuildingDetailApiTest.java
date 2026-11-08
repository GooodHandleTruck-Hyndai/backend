package com.gooodhandletruck.zippick.domain.building.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingAddress;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingCoordinateResponse;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.building.service.BuildingCoordinateService;
import com.gooodhandletruck.zippick.domain.device.entity.Device;
import com.gooodhandletruck.zippick.domain.device.repository.DeviceRepository;
import com.gooodhandletruck.zippick.domain.favorite.entity.Favorite;
import com.gooodhandletruck.zippick.domain.favorite.repository.FavoriteRepository;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.domain.trade.entity.CollectRun;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import com.gooodhandletruck.zippick.domain.trade.repository.CollectRunRepository;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 건물 상세 API (7번, GET /api/v1/buildings/{buildingId}) 를 실제 HTTP 요청으로 확인해요. (#6 에서 추가)
 * 응답 필드 이름·값·에러 코드는 팀 API 명세 7번 기준이에요. H2(MySQL 모드), 테스트마다 롤백.
 * #37 변경: 좌표(#13 카카오 좌표 서비스)를 가짜로 바꿈 — 기본은 "못 찾음", 필요한 테스트만 좌표를 돌려줘요.
 * #42 변경: saved 테스트를 실제 저장 여부 기준으로 바꿈 (저장한 기기 true, 다른 기기·헤더 없음·등록 안 된 ID false).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BuildingDetailApiTest {

    private static final String URL = "/api/v1/buildings/{buildingId}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private LegalDongRepository legalDongRepository;

    @Autowired
    private CollectRunRepository collectRunRepository;

    @Autowired
    private DeviceRepository deviceRepository; // #42

    @Autowired
    private FavoriteRepository favoriteRepository; // #42

    @MockitoBean
    private BuildingCoordinateService buildingCoordinateService;

    private Building building;
    private int fingerprintSeq;

    @BeforeEach
    void setUp() {
        building = buildingRepository.save(TestFixtures.building().build());
        // #37: 기본은 카카오에서 못 찾음 → 좌표 null
        given(buildingCoordinateService.findCoordinate(any(BuildingAddress.class)))
                .willAnswer(inv -> BuildingCoordinateResponse.notFound(((BuildingAddress) inv.getArgument(0)).buildingId()));
    }

    // ---- 좌표 (#37) ----

    @Test
    void 좌표가_없으면_카카오로_찾아서_채운다() throws Exception {
        given(buildingCoordinateService.findCoordinate(any(BuildingAddress.class)))
                .willReturn(BuildingCoordinateResponse.found(String.valueOf(building.getId()), 37.54004, 126.94547));

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lat").value(37.54004))
                .andExpect(jsonPath("$.data.lon").value(126.94547));
    }

    @Test
    void 카카오에_동_지번으로_물어본다() throws Exception {
        mockMvc.perform(get(URL, building.getId())).andExpect(status().isOk());

        verify(buildingCoordinateService).findCoordinate(new BuildingAddress(
                String.valueOf(building.getId()), "11440", null, null, null, "아현동", "777"));
    }

    @Test
    void 카카오에서_못_찾으면_좌표만_null_이고_상세는_정상() throws Exception {
        mockMvc.perform(get(URL, building.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lat").value(nullValue()))
                .andExpect(jsonPath("$.data.lon").value(nullValue()));
    }

    @Test
    void 출처_없는_저장_좌표는_쓰지_않고_카카오로_찾는다() throws Exception {
        building.updateLocation(null, null, 37.5786, 126.8879, null); // 예전 샘플처럼 출처 없는 좌표
        buildingRepository.saveAndFlush(building);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.lat").value(nullValue())); // 카카오(가짜)가 못 찾음 → null

        verify(buildingCoordinateService).findCoordinate(any(BuildingAddress.class));
    }

    // ---- 정상 응답 ----

    @Test
    void 명세의_필드를_모두_담아_응답한다() throws Exception {
        LegalDong ahyeon = legalDongRepository.save(TestFixtures.legalDong().build());
        building.updateLocation(ahyeon, null, 37.5533, 126.9567, CoordSource.JUSO);
        saveTrade(LocalDate.of(2026, 10, 12), "705", null);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.buildingId").value(building.getId()))
                .andExpect(jsonPath("$.data.name").value("마포래미안푸르지오1단지"))
                .andExpect(jsonPath("$.data.type").value("APT"))
                .andExpect(jsonPath("$.data.jibunAddress").value("서울 마포구 아현동 777"))
                .andExpect(jsonPath("$.data.builtYear").value(2014))
                .andExpect(jsonPath("$.data.lat").value(37.5533))
                .andExpect(jsonPath("$.data.lon").value(126.9567))
                .andExpect(jsonPath("$.data.saved").value(false))
                .andExpect(jsonPath("$.data.dataBaseDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.data.recentTransactions", hasSize(1)))
                .andExpect(jsonPath("$.data.recentTransactions[0].dealDate").value("2026-10-12"))
                .andExpect(jsonPath("$.data.recentTransactions[0].areaM2").value(84.97))
                .andExpect(jsonPath("$.data.recentTransactions[0].areaPyeong").value(25.7))
                .andExpect(jsonPath("$.data.recentTransactions[0].floor").value(12))
                .andExpect(jsonPath("$.data.recentTransactions[0].dong").value("705"))
                .andExpect(jsonPath("$.data.recentTransactions[0].price").value(125000))
                .andExpect(jsonPath("$.data.hasMoreTransactions").value(false));

        verify(buildingCoordinateService, never()).findCoordinate(any(BuildingAddress.class)); // #37: 행안부 좌표면 카카오 안 불러요
    }

    @Test
    void 값이_없는_필드도_null_로_키는_내려준다() throws Exception {
        // 앱이 키가 빠진 걸 에러로 보지 않게, 명세에 있는 키는 값이 없어도 null 로 보내요
        String body = mockMvc.perform(get(URL, building.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"roadAddress\":null", "\"imageUrl\":null", "\"lat\":null", "\"lon\":null",
                "\"dataBaseDate\":null");
    }

    @Test
    void 응답_data_에는_명세에_없는_키가_없다() throws Exception {
        String body = mockMvc.perform(get(URL, building.getId())).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("sggCd", "umdNm", "aptNm", "fingerprint", "createdAt", "legalDong");
    }

    @Test
    void 가격과_면적은_숫자로_내려준다() throws Exception {
        saveTrade(LocalDate.of(2026, 10, 12), null, null);

        String body = mockMvc.perform(get(URL, building.getId())).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"price\":125000", "\"areaM2\":84.97", "\"areaPyeong\":25.7");
    }

    // ---- 없는 건물 ----

    @ParameterizedTest(name = "없는 건물 id={0} → 404 B001")
    @CsvSource({"999999", "0", "-1"})
    void 없는_건물은_404_B001(long id) throws Exception {
        mockMvc.perform(get(URL, id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("B001"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ---- 최근 거래 개수와 더보기 ----

    @ParameterizedTest(name = "정상 거래 {0}건 → {1}건, 더보기 {2}")
    @CsvSource({"0, 0, false", "1, 1, false", "2, 2, false", "3, 3, false", "4, 3, true", "10, 3, true"})
    void 최근_거래는_최대_3건이고_더_있으면_더보기(int count, int shown, boolean hasMore) throws Exception {
        for (int i = 0; i < count; i++) {
            saveTrade(LocalDate.of(2026, 10, 1).plusDays(i), null, null);
        }

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.recentTransactions", hasSize(shown)))
                .andExpect(jsonPath("$.data.hasMoreTransactions").value(hasMore));
    }

    @Test
    void 최근_거래는_계약일_최신순이다() throws Exception {
        saveTrade(LocalDate.of(2026, 9, 3), null, null);
        saveTrade(LocalDate.of(2026, 10, 12), null, null);
        saveTrade(LocalDate.of(2026, 8, 20), null, null);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.recentTransactions[*].dealDate",
                        contains("2026-10-12", "2026-09-03", "2026-08-20")));
    }

    @Test
    void 같은_날_거래는_나중에_수집된_것이_먼저다() throws Exception {
        saveTradeWithPrice(LocalDate.of(2026, 10, 12), 100000);
        saveTradeWithPrice(LocalDate.of(2026, 10, 12), 110000);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.recentTransactions[*].price", contains(110000, 100000)));
    }

    @Test
    void 가장_최근_4건_중_최신_3건만_보여준다() throws Exception {
        saveTrade(LocalDate.of(2026, 7, 1), null, null);
        saveTrade(LocalDate.of(2026, 8, 1), null, null);
        saveTrade(LocalDate.of(2026, 9, 1), null, null);
        saveTrade(LocalDate.of(2026, 10, 1), null, null);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.recentTransactions[*].dealDate",
                        contains("2026-10-01", "2026-09-01", "2026-08-01")));
    }

    // ---- 해제 거래 제외 ----

    @Test
    void 해제된_거래는_최근_거래에서_빠진다() throws Exception {
        saveTrade(LocalDate.of(2026, 10, 20), null, "O");
        saveTrade(LocalDate.of(2026, 10, 12), null, null);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.recentTransactions", hasSize(1)))
                .andExpect(jsonPath("$.data.recentTransactions[0].dealDate").value("2026-10-12"));
    }

    @Test
    void 해제된_거래는_더보기_판단에도_세지_않는다() throws Exception {
        // 정상 3건 + 해제 2건이면 더 볼 정상 거래가 없으니 더보기를 띄우면 안 돼요
        for (int i = 0; i < 3; i++) {
            saveTrade(LocalDate.of(2026, 9, 1).plusDays(i), null, null);
        }
        saveTrade(LocalDate.of(2026, 10, 1), null, "O");
        saveTrade(LocalDate.of(2026, 10, 2), null, "O");

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.recentTransactions", hasSize(3)))
                .andExpect(jsonPath("$.data.hasMoreTransactions").value(false));
    }

    @Test
    void 해제된_거래만_있으면_빈_목록이다() throws Exception {
        saveTrade(LocalDate.of(2026, 10, 1), null, "O");

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.recentTransactions", hasSize(0)))
                .andExpect(jsonPath("$.data.hasMoreTransactions").value(false));
    }

    @Test
    void 다른_건물의_거래는_섞이지_않는다() throws Exception {
        Building other = buildingRepository.save(TestFixtures.building().aptNm("다른단지").build());
        saveTradeFor(other, LocalDate.of(2026, 10, 30));
        saveTrade(LocalDate.of(2026, 10, 12), null, null);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.recentTransactions", hasSize(1)))
                .andExpect(jsonPath("$.data.recentTransactions[0].dealDate").value("2026-10-12"));
    }

    // ---- 개별 필드 ----

    @Test
    void 등기_전_거래의_동은_null() throws Exception {
        saveTrade(LocalDate.of(2026, 10, 12), null, null);

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.recentTransactions[0].dong").value(nullValue()));
    }

    // ---- 저장 여부 (#42) ----

    @Test
    void 저장한_기기가_보면_saved_는_true() throws Exception {
        Device me = deviceRepository.save(Device.register("0.1.0"));
        favoriteRepository.save(Favorite.of(me, building));

        mockMvc.perform(get(URL, building.getId()).header("X-Anonymous-Id", me.getAnonymousId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(true));
    }

    @Test
    void 다른_기기가_저장한_건물은_saved_false() throws Exception {
        Device other = deviceRepository.save(Device.register("0.1.0"));
        Device me = deviceRepository.save(Device.register("0.1.0"));
        favoriteRepository.save(Favorite.of(other, building));

        mockMvc.perform(get(URL, building.getId()).header("X-Anonymous-Id", me.getAnonymousId()))
                .andExpect(jsonPath("$.data.saved").value(false));
    }

    @Test
    void 헤더가_없으면_saved_false() throws Exception {
        Device me = deviceRepository.save(Device.register("0.1.0"));
        favoriteRepository.save(Favorite.of(me, building));

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(false));
    }

    @ParameterizedTest(name = "등록 안 된·빈 X-Anonymous-Id [{0}] → 에러 없이 saved=false")
    @CsvSource({"device-123", "''"})
    void 등록_안_된_ID_나_빈_헤더도_에러_없이_saved_false(String deviceId) throws Exception {
        mockMvc.perform(get(URL, building.getId()).header("X-Anonymous-Id", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(false));
    }

    @Test
    void 법정동코드표에_구가_없으면_주소는_동과_지번만() throws Exception {
        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.jibunAddress").value("아현동 777"));
    }

    @Test
    void 건물에_법정동이_연결_안_돼도_같은_구_코드로_시도_구_이름을_찾는다() throws Exception {
        legalDongRepository.save(TestFixtures.legalDong().admCd("1144010200").umdNm("공덕동").build());

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.jibunAddress").value("서울 마포구 아현동 777"));
    }

    @Test
    void 지번이_없는_건물은_동까지만_보여준다() throws Exception {
        Building noJibun = buildingRepository.save(TestFixtures.building().jibun(null).aptNm("지번없는단지").build());

        mockMvc.perform(get(URL, noJibun.getId()))
                .andExpect(jsonPath("$.data.jibunAddress").value("아현동"));
    }

    @Test
    void 자료_기준일은_같은_구에서_가장_최근_수집된_날짜다() throws Exception {
        // 이 건물엔 거래가 없어도, 같은 구 데이터를 받았다면 그 날짜가 자료 기준일이에요
        Building other = buildingRepository.save(TestFixtures.building().aptNm("다른단지").build());
        saveTradeFor(other, LocalDate.of(2026, 10, 1));

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.dataBaseDate").value(LocalDate.now().toString()));
    }

    // ---- 자료 기준일 = 마지막 성공 수집 날짜 (#19) ----

    @Test
    void 수집_이력이_있으면_마지막_성공_수집_날짜가_자료_기준일이다() throws Exception {
        // 오늘 거래가 들어왔어도(createdAt=오늘), 수집 이력이 있으면 그 날짜를 기준으로 봐요
        saveTradeFor(building, LocalDate.of(2026, 10, 1));
        saveRun("11440", "success", LocalDateTime.of(2026, 11, 3, 4, 0));
        saveRun("11440", "success", LocalDateTime.of(2026, 11, 5, 4, 0));

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.dataBaseDate").value("2026-11-05"));
    }

    @Test
    void 새_거래가_없어도_다시_받은_날짜로_바뀐다() throws Exception {
        // 명세 12번: 재수집했는데 바뀐 게 없어도(0/0/0) 다시 받은 날짜가 기준일이 돼요
        saveRun("11440", "success", LocalDateTime.of(2026, 11, 5, 4, 0));
        mockMvc.perform(get(URL, building.getId())).andExpect(jsonPath("$.data.dataBaseDate").value("2026-11-05"));

        saveRun("11440", "success", LocalDateTime.of(2026, 11, 6, 4, 0)); // 다음 날 재수집, 추가 0건

        mockMvc.perform(get(URL, building.getId())).andExpect(jsonPath("$.data.dataBaseDate").value("2026-11-06"));
    }

    @Test
    void 건너뜀이나_실패한_수집은_기준일에_넣지_않는다() throws Exception {
        // 반영하지 않은 회차라 DB 데이터는 그 전 성공 때 그대로예요
        saveRun("11440", "success", LocalDateTime.of(2026, 11, 5, 4, 0));
        saveRun("11440", "skip", LocalDateTime.of(2026, 11, 6, 4, 0));
        saveRun("11440", "fail", LocalDateTime.of(2026, 11, 7, 4, 0));

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.dataBaseDate").value("2026-11-05"));
    }

    @Test
    void 다른_구_수집_이력은_보지_않는다() throws Exception {
        saveRun("11410", "success", LocalDateTime.of(2026, 11, 5, 4, 0));

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.dataBaseDate").value(nullValue()));
    }

    @Test
    void 다른_구_데이터만_있으면_자료_기준일은_null() throws Exception {
        Building otherGu = buildingRepository.save(TestFixtures.building().sggCd("11410").aptNm("다른구단지").build());
        saveTradeFor(otherGu, LocalDate.of(2026, 10, 1));

        mockMvc.perform(get(URL, building.getId()))
                .andExpect(jsonPath("$.data.dataBaseDate").value(nullValue()));
    }

    @Test
    void 건축년도가_없으면_null() throws Exception {
        Building noYear = buildingRepository.save(TestFixtures.building().buildYear(null).aptNm("연도없음").build());

        mockMvc.perform(get(URL, noYear.getId()))
                .andExpect(jsonPath("$.data.builtYear").value(nullValue()));
    }

    // ---- 문서 ----

    @Test
    void Swagger_문서에_경로와_X_Device_Id_헤더가_나온다() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(docs).contains("/api/v1/buildings/{buildingId}", "X-Anonymous-Id");
    }

    // ---- 도우미 ----

    private void saveTrade(LocalDate dealDate, String dong, String cdealType) {
        tradeRepository.save(TestFixtures.trade(building)
                .fingerprint(nextFingerprint())
                .dealDate(dealDate)
                .aptDong(dong)
                .cdealType(cdealType)
                .build());
    }

    private void saveTradeWithPrice(LocalDate dealDate, int price) {
        tradeRepository.save(TestFixtures.trade(building)
                .fingerprint(nextFingerprint())
                .dealDate(dealDate)
                .dealAmount(price)
                .build());
    }

    private void saveTradeFor(Building target, LocalDate dealDate) {
        Trade trade = TestFixtures.trade(target)
                .fingerprint(nextFingerprint())
                .sggCd(target.getSggCd())
                .dealDate(dealDate)
                .excluUseAr(new BigDecimal("59.98"))
                .build();
        tradeRepository.save(trade);
    }

    /** 수집 이력 한 줄. 종료 시각은 now() 로 찍혀서 테스트에선 원하는 날짜로 덮어써요 */
    private void saveRun(String sggCd, String result, LocalDateTime finishedAt) {
        CollectRun run = CollectRun.start(sggCd, "202610");
        switch (result) {
            case "success" -> run.succeed(0, 0, 0);
            case "skip" -> run.skip("건수 불일치");
            default -> run.fail(null, "호출 실패");
        }
        ReflectionTestUtils.setField(run, "finishedAt", finishedAt);
        collectRunRepository.save(run);
    }

    private String nextFingerprint() {
        return String.format("%064d", ++fingerprintSeq);
    }
}
