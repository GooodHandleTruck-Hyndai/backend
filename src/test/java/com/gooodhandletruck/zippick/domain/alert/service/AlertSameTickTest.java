package com.gooodhandletruck.zippick.domain.alert.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.alert.repository.AlertTradeRepository;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import com.gooodhandletruck.zippick.global.util.KstClock;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * #52: 관심 조건과 거래가 같은 시계 틱에 저장됐을 때 알림 대상에서 빠지지 않는지. (#52 에서 추가)
 *
 * 실제로 깨지던 상황(AlertServiceTest 간헐 실패)을 시각을 고정해서 매번 똑같이 만들어요.
 *   - 조건 시각(메모리) = tick (나노초 7자리, 예: .1234564)
 *   - 거래 시각(DB)     = 같은 tick 을 저장 → DB 가 6자리로 반올림 (.123456)
 * 시계 틱 0.5ms·H2 반올림은 11/12 로컬 실측 (이슈 #52 본문).
 */
@SpringBootTest
@Transactional
class AlertSameTickTest {

    private static final List<String> MAPO = List.of("11440");

    @Autowired
    private AlertTradeRepository alertTradeRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private EntityManager em;

    private final LocalDate today = KstClock.today();
    private Building mapo;

    @BeforeEach
    void setUp() {
        mapo = buildingRepository.save(TestFixtures.building().build());
    }

    @ParameterizedTest(name = "7번째 자리 {0}")
    @ValueSource(ints = {0, 1, 4, 5, 9})
    void 조건과_같은_틱에_저장된_거래는_새_거래로_본다(int digit7) {
        LocalDateTime tick = LocalDateTime.of(2026, 11, 12, 14, 39, 0, 123_456_000 + digit7 * 100);
        Long tradeId = tradeSavedAt(tick);

        List<Trade> found = alertTradeRepository.findNewInRegions(
                MAPO, AlertGenerateService.newSince(tick), today.minusDays(30));

        assertThat(found).extracting(Trade::getId).containsExactly(tradeId);
    }

    @Test
    void 고치기_전_방식은_같은_틱_거래를_놓쳤다() {
        // 버그 재현: 메모리 조건 시각(7자리 .1234564)을 그대로 넣으면 DB 거래 시각(.123456)이 더 이른 걸로 보여요
        LocalDateTime tick = LocalDateTime.of(2026, 11, 12, 14, 39, 0, 123_456_400);
        tradeSavedAt(tick);

        assertThat(alertTradeRepository.findNewInRegions(MAPO, tick, today.minusDays(30))).isEmpty();
    }

    @Test
    void 조건보다_1마이크로초_이상_먼저_들어온_거래는_여전히_뺀다() {
        LocalDateTime tradeAt = LocalDateTime.of(2026, 11, 12, 14, 39, 0, 123_455_000);
        tradeSavedAt(tradeAt);
        LocalDateTime conditionAt = LocalDateTime.of(2026, 11, 12, 14, 39, 0, 123_456_400);

        assertThat(alertTradeRepository.findNewInRegions(
                MAPO, AlertGenerateService.newSince(conditionAt), today.minusDays(30))).isEmpty();
    }

    @Test
    void 기준_시각은_마이크로초로_내린다() {
        assertThat(AlertGenerateService.newSince(LocalDateTime.of(2026, 11, 12, 14, 39, 0, 123_456_999)))
                .isEqualTo(LocalDateTime.of(2026, 11, 12, 14, 39, 0, 123_456_000));
    }

    /** 거래를 저장하고 DB 의 created_at 을 at 으로 바꿔요 (DB 가 저장할 때 6자리로 반올림). 거래 id 반환 */
    private Long tradeSavedAt(LocalDateTime at) {
        Trade trade = tradeRepository.save(TestFixtures.trade(mapo)
                .sggCd(mapo.getSggCd())
                .dealDate(today.minusDays(3))
                .dealYm(today.minusDays(3).toString().substring(0, 7).replace("-", ""))
                .build());
        em.flush();
        em.createNativeQuery("update trades set created_at = ?1 where id = ?2")
                .setParameter(1, at).setParameter(2, trade.getId()).executeUpdate();
        em.clear(); // 다음 조회가 DB 값을 읽게
        return trade.getId();
    }
}
