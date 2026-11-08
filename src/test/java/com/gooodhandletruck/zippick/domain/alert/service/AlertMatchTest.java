package com.gooodhandletruck.zippick.domain.alert.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import com.gooodhandletruck.zippick.domain.interest.entity.InterestCondition;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlertMatchTest {

    @Test
    void 범위의_한쪽이_null_이면_그쪽은_제한_없음() {
        InterestCondition condition = InterestCondition.of(null);
        condition.update(List.of("11440"), List.of(BuildingType.APT), null, null, null, null, true);
        Building building = TestFixtures.building().build();
        Trade trade = TestFixtures.trade(building).dealAmount(999_999).excluUseAr(new BigDecimal("300")).build();

        assertThat(AlertGenerateService.matches(condition, trade)).isTrue();
    }

    @Test
    void 면적_경계값은_포함한다() {
        InterestCondition condition = InterestCondition.of(null);
        condition.update(List.of("11440"), List.of(BuildingType.APT), null, null, 59, 85, true);
        Building building = TestFixtures.building().build();

        assertThat(AlertGenerateService.matches(condition,
                TestFixtures.trade(building).excluUseAr(new BigDecimal("85.0000")).build())).isTrue();
        assertThat(AlertGenerateService.matches(condition,
                TestFixtures.trade(building).excluUseAr(new BigDecimal("85.0001")).build())).isFalse();
    }

    @Test
    void 유형이_다르면_알리지_않는다() {
        InterestCondition condition = InterestCondition.of(null);
        condition.update(List.of("11440"), List.of(BuildingType.OFFICETEL), null, null, null, null, true);
        Trade trade = TestFixtures.trade(TestFixtures.building().build()).build();

        assertThat(AlertGenerateService.matches(condition, trade)).isFalse();
    }
}
