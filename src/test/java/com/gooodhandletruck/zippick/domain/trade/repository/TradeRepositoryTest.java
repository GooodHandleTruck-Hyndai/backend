package com.gooodhandletruck.zippick.domain.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import com.gooodhandletruck.zippick.global.config.JpaConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 실거래 저장 규칙 (trades). (#4 에서 추가, 테스트 보강에서 확장)
 *
 * 국토부 응답에는 거래 번호가 없어서 지문(fingerprint) + 순번(seq) 이 거래 고유 키예요.
 * 3번 적재·12번 재수집이 이 규칙에 기대고 있어서, 여기가 깨지면 거래가 중복되거나 빠져요.
 * H2(MySQL 모드) 로 돌아요. 실제 MySQL 고유 동작은 로컬 실행으로 따로 확인해요.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 팀 테스트 설정(H2 MODE=MySQL) 그대로
@Import(JpaConfig.class) // createdAt/updatedAt 자동 기록(Auditing) 을 테스트에서도 켜려고
class TradeRepositoryTest {

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private TestEntityManager em;

    private Building building;

    @BeforeEach
    void setUp() {
        building = buildingRepository.saveAndFlush(TestFixtures.building().build());
    }

    // ---- 지문 + 순번 ----

    @Test
    void 건물과_거래를_저장하고_구_계약월로_조회한다() {
        tradeRepository.saveAndFlush(TestFixtures.trade(building).build());

        assertThat(tradeRepository.findBySggCdAndDealYm("11440", "202610")).hasSize(1);
    }

    @ParameterizedTest(name = "같은 지문 {0}건은 순번 1~{0} 으로 모두 저장")
    @ValueSource(ints = {2, 3, 5})
    void 같은_지문이라도_순번이_다르면_모두_저장된다(int count) {
        // 모든 값이 같은 진짜 거래(예: 미원2 8/18 6층 8.2억 2건)를 빠뜨리지 않으려고
        for (int seq = 1; seq <= count; seq++) {
            tradeRepository.saveAndFlush(TestFixtures.trade(building).seq(seq).build());
        }

        assertThat(tradeRepository.count()).isEqualTo(count);
    }

    @ParameterizedTest(name = "지문·순번이 같으면 거부 (순번 {0})")
    @ValueSource(ints = {1, 2})
    void 지문과_순번이_모두_같으면_저장되지_않는다(int seq) {
        tradeRepository.saveAndFlush(TestFixtures.trade(building).seq(seq).build());

        assertThatThrownBy(() -> tradeRepository.saveAndFlush(TestFixtures.trade(building).seq(seq).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 지문이_다르면_순번이_같아도_저장된다() {
        tradeRepository.saveAndFlush(TestFixtures.trade(building).fingerprint("a".repeat(64)).build());
        tradeRepository.saveAndFlush(TestFixtures.trade(building).fingerprint("b".repeat(64)).build());

        assertThat(tradeRepository.count()).isEqualTo(2);
    }

    @Test
    void 지문과_순번이_같으면_다른_건물이어도_거부된다() {
        // 지문에 건물 구분 값(구·동·지번·단지명)이 들어가서, 건물이 달라도 키 하나로 충분해요
        Building other = buildingRepository.saveAndFlush(TestFixtures.building().aptNm("다른단지").build());
        tradeRepository.saveAndFlush(TestFixtures.trade(building).build());

        assertThatThrownBy(() -> tradeRepository.saveAndFlush(TestFixtures.trade(other).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 해제_여부만_다른_같은_키_거래도_거부된다() {
        // 해제 여부는 지문에서 빠지는 값이라, 해제되면 새 행이 아니라 기존 행을 고쳐야 해요
        tradeRepository.saveAndFlush(TestFixtures.trade(building).build());

        assertThatThrownBy(() -> tradeRepository.saveAndFlush(TestFixtures.trade(building).cdealType("O").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- 필수 칸·길이·숫자 범위 ----

    static Stream<Arguments> 필수_칸을_비운_거래() {
        return Stream.of(
                Arguments.of("fingerprint", (UnaryOperator<Trade.TradeBuilder>) b -> b.fingerprint(null)),
                Arguments.of("seq", (UnaryOperator<Trade.TradeBuilder>) b -> b.seq(null)),
                Arguments.of("sgg_cd", (UnaryOperator<Trade.TradeBuilder>) b -> b.sggCd(null)),
                Arguments.of("deal_ym", (UnaryOperator<Trade.TradeBuilder>) b -> b.dealYm(null)),
                Arguments.of("deal_date", (UnaryOperator<Trade.TradeBuilder>) b -> b.dealDate(null)),
                Arguments.of("deal_amount", (UnaryOperator<Trade.TradeBuilder>) b -> b.dealAmount(null)),
                Arguments.of("exclu_use_ar", (UnaryOperator<Trade.TradeBuilder>) b -> b.excluUseAr(null)),
                Arguments.of("floor", (UnaryOperator<Trade.TradeBuilder>) b -> b.floor(null)),
                Arguments.of("building", (UnaryOperator<Trade.TradeBuilder>) b -> b.building(null)));
    }

    @ParameterizedTest(name = "필수 칸 {0} 이 비면 거부")
    @MethodSource("필수_칸을_비운_거래")
    void 필수_칸이_비면_저장이_거부된다(String field, UnaryOperator<Trade.TradeBuilder> change) {
        Trade trade = change.apply(TestFixtures.trade(building)).build();

        assertThatThrownBy(() -> tradeRepository.saveAndFlush(trade))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    static Stream<Arguments> 최대_길이_거래() {
        return Stream.of(
                Arguments.of("fingerprint 64자", (UnaryOperator<Trade.TradeBuilder>) b -> b.fingerprint("f".repeat(64))),
                Arguments.of("sgg_cd 5자", (UnaryOperator<Trade.TradeBuilder>) b -> b.sggCd("11440")),
                Arguments.of("deal_ym 6자", (UnaryOperator<Trade.TradeBuilder>) b -> b.dealYm("202612")),
                Arguments.of("apt_dong 20자", (UnaryOperator<Trade.TradeBuilder>) b -> b.aptDong("동".repeat(20))),
                Arguments.of("cdeal_type 1자", (UnaryOperator<Trade.TradeBuilder>) b -> b.cdealType("O")),
                Arguments.of("dealing_gbn 10자", (UnaryOperator<Trade.TradeBuilder>) b -> b.dealingGbn("가".repeat(10))),
                Arguments.of("estate_agent_sgg_nm 200자",
                        (UnaryOperator<Trade.TradeBuilder>) b -> b.estateAgentSggNm("가".repeat(200))),
                Arguments.of("sler_gbn 10자", (UnaryOperator<Trade.TradeBuilder>) b -> b.slerGbn("가".repeat(10))),
                Arguments.of("buyer_gbn 10자", (UnaryOperator<Trade.TradeBuilder>) b -> b.buyerGbn("가".repeat(10))),
                Arguments.of("land_leasehold_gbn 1자", (UnaryOperator<Trade.TradeBuilder>) b -> b.landLeaseholdGbn("N")));
    }

    @ParameterizedTest(name = "{0} 는 저장")
    @MethodSource("최대_길이_거래")
    void 최대_길이까지는_저장된다(String field, UnaryOperator<Trade.TradeBuilder> change) {
        Trade saved = tradeRepository.saveAndFlush(change.apply(TestFixtures.trade(building)).build());

        assertThat(saved.getId()).isNotNull();
    }

    static Stream<Arguments> 최대_길이를_넘는_거래() {
        return Stream.of(
                Arguments.of("fingerprint 65자", (UnaryOperator<Trade.TradeBuilder>) b -> b.fingerprint("f".repeat(65))),
                Arguments.of("sgg_cd 6자", (UnaryOperator<Trade.TradeBuilder>) b -> b.sggCd("114400")),
                Arguments.of("deal_ym 7자", (UnaryOperator<Trade.TradeBuilder>) b -> b.dealYm("2026100")),
                Arguments.of("apt_dong 21자", (UnaryOperator<Trade.TradeBuilder>) b -> b.aptDong("동".repeat(21))),
                Arguments.of("cdeal_type 2자", (UnaryOperator<Trade.TradeBuilder>) b -> b.cdealType("OO")),
                Arguments.of("dealing_gbn 11자", (UnaryOperator<Trade.TradeBuilder>) b -> b.dealingGbn("가".repeat(11))),
                Arguments.of("estate_agent_sgg_nm 201자",
                        (UnaryOperator<Trade.TradeBuilder>) b -> b.estateAgentSggNm("가".repeat(201))),
                Arguments.of("sler_gbn 11자", (UnaryOperator<Trade.TradeBuilder>) b -> b.slerGbn("가".repeat(11))),
                Arguments.of("buyer_gbn 11자", (UnaryOperator<Trade.TradeBuilder>) b -> b.buyerGbn("가".repeat(11))),
                Arguments.of("land_leasehold_gbn 2자", (UnaryOperator<Trade.TradeBuilder>) b -> b.landLeaseholdGbn("NN")));
    }

    @ParameterizedTest(name = "{0} 는 거부")
    @MethodSource("최대_길이를_넘는_거래")
    void 최대_길이를_넘으면_거부된다(String field, UnaryOperator<Trade.TradeBuilder> change) {
        Trade trade = change.apply(TestFixtures.trade(building)).build();

        assertThatThrownBy(() -> tradeRepository.saveAndFlush(trade))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest(name = "전용면적 {0}㎡ 저장 후 {0} 그대로")
    @CsvSource({"84.97", "59.99", "0.01", "12.5", "300.00", "99999.99"})
    void 전용면적을_소수_2자리까지_그대로_읽는다(String area) {
        Trade saved = tradeRepository.saveAndFlush(
                TestFixtures.trade(building).excluUseAr(new BigDecimal(area)).build());
        em.clear();

        assertThat(tradeRepository.findById(saved.getId()).orElseThrow().getExcluUseAr())
                .isEqualByComparingTo(area);
    }

    @Test
    void 전용면적이_DECIMAL_7_2_범위를_넘으면_거부된다() {
        Trade trade = TestFixtures.trade(building).excluUseAr(new BigDecimal("100000.00")).build();

        assertThatThrownBy(() -> tradeRepository.saveAndFlush(trade))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest(name = "{0}층 저장")
    @ValueSource(ints = {-3, -1, 0, 1, 12, 69})
    void 지하층과_고층을_그대로_저장한다(int floor) {
        Trade saved = tradeRepository.saveAndFlush(TestFixtures.trade(building).floor(floor).build());
        em.clear();

        assertThat(tradeRepository.findById(saved.getId()).orElseThrow().getFloor()).isEqualTo(floor);
    }

    @ParameterizedTest(name = "거래금액 {0}만원 저장")
    @ValueSource(ints = {1, 8200, 125000, 1_000_000, Integer.MAX_VALUE})
    void 거래금액을_만원_정수로_그대로_저장한다(int amount) {
        Trade saved = tradeRepository.saveAndFlush(TestFixtures.trade(building).dealAmount(amount).build());
        em.clear();

        assertThat(tradeRepository.findById(saved.getId()).orElseThrow().getDealAmount()).isEqualTo(amount);
    }

    @ParameterizedTest(name = "계약일 {0} 저장")
    @CsvSource({"2026-01-01", "2026-02-28", "2024-02-29", "2026-12-31", "2006-01-01"})
    void 계약일_날짜_경계를_그대로_저장한다(LocalDate date) {
        Trade saved = tradeRepository.saveAndFlush(TestFixtures.trade(building).dealDate(date).build());
        em.clear();

        assertThat(tradeRepository.findById(saved.getId()).orElseThrow().getDealDate()).isEqualTo(date);
    }

    // ---- 구·계약월 조회 (12번 재수집 단위) ----

    @Test
    void 구_계약월_조회는_다른_구를_섞지_않는다() {
        tradeRepository.saveAndFlush(TestFixtures.trade(building).fingerprint("a".repeat(64)).build());
        tradeRepository.saveAndFlush(TestFixtures.trade(building).fingerprint("b".repeat(64)).sggCd("11410").build());

        assertThat(tradeRepository.findBySggCdAndDealYm("11440", "202610")).hasSize(1);
        assertThat(tradeRepository.findBySggCdAndDealYm("11410", "202610")).hasSize(1);
    }

    @Test
    void 구_계약월_조회는_다른_달을_섞지_않는다() {
        tradeRepository.saveAndFlush(TestFixtures.trade(building).fingerprint("a".repeat(64)).dealYm("202609").build());
        tradeRepository.saveAndFlush(TestFixtures.trade(building).fingerprint("b".repeat(64)).dealYm("202610").build());
        tradeRepository.saveAndFlush(TestFixtures.trade(building).fingerprint("c".repeat(64)).dealYm("202611").build());

        assertThat(tradeRepository.findBySggCdAndDealYm("11440", "202610"))
                .extracting(Trade::getDealYm).containsExactly("202610");
    }

    @Test
    void 구_계약월_조회는_해제된_거래도_포함한다() {
        // 재수집 비교는 해제 거래까지 다 봐야 해요. 화면용 조회에서만 해제 거래를 빼요
        tradeRepository.saveAndFlush(TestFixtures.trade(building).fingerprint("a".repeat(64)).build());
        tradeRepository.saveAndFlush(TestFixtures.trade(building).fingerprint("b".repeat(64)).cdealType("O").build());

        assertThat(tradeRepository.findBySggCdAndDealYm("11440", "202610")).hasSize(2);
    }

    @Test
    void 구_계약월_조회는_여러_건물의_거래를_모두_가져온다() {
        Building other = buildingRepository.saveAndFlush(TestFixtures.building().aptNm("다른단지").build());
        tradeRepository.saveAndFlush(TestFixtures.trade(building).fingerprint("a".repeat(64)).build());
        tradeRepository.saveAndFlush(TestFixtures.trade(other).fingerprint("b".repeat(64)).build());

        assertThat(tradeRepository.findBySggCdAndDealYm("11440", "202610")).hasSize(2);
    }

    @ParameterizedTest(name = "구 {0}, 계약월 {1} 은 거래 없음")
    @CsvSource({"11440, 202501", "11410, 202610", "99999, 999999"})
    void 거래가_없는_구_계약월은_빈_목록이다(String sggCd, String dealYm) {
        tradeRepository.saveAndFlush(TestFixtures.trade(building).build());

        assertThat(tradeRepository.findBySggCdAndDealYm(sggCd, dealYm)).isEmpty();
    }

    // ---- 재수집 수정 ----

    @Test
    void 재수집_수정이_DB에_반영된다() {
        Trade saved = tradeRepository.saveAndFlush(TestFixtures.trade(building).build());

        saved.updateChangeableFields("705", "O", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 1));
        em.flush();
        em.clear();

        Trade found = tradeRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getAptDong()).isEqualTo("705");
        assertThat(found.getCdealType()).isEqualTo("O");
        assertThat(found.getCdealDay()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(found.getRgstDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(found.getFingerprint()).isEqualTo(saved.getFingerprint());
        assertThat(found.getSeq()).isEqualTo(saved.getSeq());
    }

    @Test
    void 재수집_수정을_해도_처음_수집_시각은_그대로다() {
        // createdAt = 처음 수집된 시각이라, 새 신고 알림(17번)이 이 값으로 새 거래를 골라요
        Trade saved = tradeRepository.saveAndFlush(TestFixtures.trade(building).build());
        em.clear();
        // 메모리 값(나노초)과 DB 값(마이크로초, datetime(6))은 자릿수가 달라서, DB 에서 읽은 값끼리 비교해요
        Trade before = tradeRepository.findById(saved.getId()).orElseThrow();
        LocalDateTime firstSeen = before.getCreatedAt();

        before.updateChangeableFields("705", null, null, LocalDate.of(2026, 11, 1));
        em.flush();
        em.clear();

        Trade found = tradeRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getCreatedAt()).isEqualTo(firstSeen);
        assertThat(found.getUpdatedAt()).isAfterOrEqualTo(firstSeen);
    }

    @Test
    void 거래를_다시_읽으면_같은_건물에_연결돼_있다() {
        Trade saved = tradeRepository.saveAndFlush(TestFixtures.trade(building).build());
        em.clear();

        Trade found = tradeRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getBuilding().getId()).isEqualTo(building.getId());
    }

    @Test
    void 거래가_있는_건물은_지울_수_없다() {
        // 건물을 지우면 거래가 고아가 되니 FK 가 막아야 해요
        tradeRepository.saveAndFlush(TestFixtures.trade(building).build());
        // 메모리에 거래가 남아 있으면 Hibernate 가 DB 보다 먼저 막아서, 비우고 DB 의 FK 가 막는지 봐요
        em.clear();

        assertThatThrownBy(() -> {
            buildingRepository.deleteById(building.getId());
            buildingRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 한_달_치_거래를_지우면_그_달만_사라진다() {
        // 재수집에서 "받은 데이터에 없는 기존 거래 → 삭제" 가 다른 달을 건드리지 않아야 해요
        tradeRepository.saveAndFlush(TestFixtures.trade(building).fingerprint("a".repeat(64)).dealYm("202609").build());
        tradeRepository.saveAndFlush(TestFixtures.trade(building).fingerprint("b".repeat(64)).dealYm("202610").build());

        tradeRepository.deleteAll(tradeRepository.findBySggCdAndDealYm("11440", "202610"));
        tradeRepository.flush();

        assertThat(tradeRepository.findAll()).extracting(Trade::getDealYm).containsExactly("202609");
    }
}
