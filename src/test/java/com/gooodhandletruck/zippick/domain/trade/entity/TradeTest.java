package com.gooodhandletruck.zippick.domain.trade.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Trade 엔티티 단위 테스트 (DB 없이). (#4 테스트 보강에서 추가)
 * 재수집(12번)은 "지문에 든 값은 그대로, 바뀌는 4칸만 수정" 에 기대고 있어요.
 */
class TradeTest {

    private final Building building = TestFixtures.building().build();

    @Test
    void 빌더로_넣은_지문_값이_그대로_들어간다() {
        Trade trade = TestFixtures.trade(building).build();

        assertThat(trade.getBuilding()).isSameAs(building);
        assertThat(trade.getFingerprint()).hasSize(64);
        assertThat(trade.getSeq()).isEqualTo(1);
        assertThat(trade.getSggCd()).isEqualTo("11440");
        assertThat(trade.getDealYm()).isEqualTo("202610");
        assertThat(trade.getDealDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(trade.getDealAmount()).isEqualTo(125000);
        assertThat(trade.getExcluUseAr()).isEqualByComparingTo("84.97");
        assertThat(trade.getFloor()).isEqualTo(12);
    }

    @Test
    void 선택_값은_안_넣으면_비어_있다() {
        Trade trade = TestFixtures.trade(building).build();

        assertThat(trade.getAptDong()).isNull();
        assertThat(trade.getCdealType()).isNull();
        assertThat(trade.getCdealDay()).isNull();
        assertThat(trade.getRgstDate()).isNull();
        assertThat(trade.getDealingGbn()).isNull();
        assertThat(trade.getEstateAgentSggNm()).isNull();
        assertThat(trade.getSlerGbn()).isNull();
        assertThat(trade.getBuyerGbn()).isNull();
        assertThat(trade.getLandLeaseholdGbn()).isNull();
    }

    @Test
    void 참고용_값도_빌더로_넣을_수_있다() {
        Trade trade = TestFixtures.trade(building)
                .dealingGbn("중개거래")
                .estateAgentSggNm("서울 마포구")
                .slerGbn("개인")
                .buyerGbn("개인")
                .landLeaseholdGbn("N")
                .build();

        assertThat(trade.getDealingGbn()).isEqualTo("중개거래");
        assertThat(trade.getEstateAgentSggNm()).isEqualTo("서울 마포구");
        assertThat(trade.getSlerGbn()).isEqualTo("개인");
        assertThat(trade.getBuyerGbn()).isEqualTo("개인");
        assertThat(trade.getLandLeaseholdGbn()).isEqualTo("N");
    }

    @Test
    void 재수집_수정은_바뀌는_4칸만_바꾼다() {
        Trade trade = TestFixtures.trade(building).build();

        trade.updateChangeableFields("705", "O", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 1));

        assertThat(trade.getAptDong()).isEqualTo("705");
        assertThat(trade.getCdealType()).isEqualTo("O");
        assertThat(trade.getCdealDay()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(trade.getRgstDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void 재수집_수정은_지문에_든_값을_건드리지_않는다() {
        Trade trade = TestFixtures.trade(building).build();

        trade.updateChangeableFields("705", "O", LocalDate.of(2026, 10, 20), null);

        assertThat(trade.getFingerprint()).isEqualTo("a".repeat(64));
        assertThat(trade.getSeq()).isEqualTo(1);
        assertThat(trade.getDealDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(trade.getDealAmount()).isEqualTo(125000);
        assertThat(trade.getExcluUseAr()).isEqualByComparingTo(new BigDecimal("84.97"));
        assertThat(trade.getFloor()).isEqualTo(12);
        assertThat(trade.getBuilding()).isSameAs(building);
    }

    @Test
    void 해제가_취소되면_해제_값을_다시_비운다() {
        Trade trade = TestFixtures.trade(building).cdealType("O").cdealDay(LocalDate.of(2026, 10, 20)).build();

        trade.updateChangeableFields(null, null, null, null);

        assertThat(trade.getCdealType()).isNull();
        assertThat(trade.getCdealDay()).isNull();
    }

    @Test
    void 지하층은_음수로_담는다() {
        Trade trade = TestFixtures.trade(building).floor(-1).build();

        assertThat(trade.getFloor()).isNegative();
    }
}
