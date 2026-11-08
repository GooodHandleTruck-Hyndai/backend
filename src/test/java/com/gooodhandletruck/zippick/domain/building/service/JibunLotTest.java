package com.gooodhandletruck.zippick.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JibunLotTest {

    @Test
    void 본번만_있는_지번() {
        assertThat(JibunLot.parse("777")).contains(new JibunLot(false, 777, 0));
    }

    @Test
    void 본번_부번이_있는_지번() {
        assertThat(JibunLot.parse("736-1")).contains(new JibunLot(false, 736, 1));
    }

    @Test
    void 산_지번() {
        assertThat(JibunLot.parse("산12-3")).contains(new JibunLot(true, 12, 3));
    }

    @Test
    void 비어_있거나_이상한_지번은_없음() {
        assertThat(JibunLot.parse("")).isEmpty();
        assertThat(JibunLot.parse(null)).isEmpty();
        assertThat(JibunLot.parse("블록1")).isEmpty();
    }

    @Test
    void 행안부_결과와_지번을_비교한다() {
        JibunLot lot = new JibunLot(false, 736, 1);

        assertThat(lot.matches("0", "736", "1")).isTrue();
        assertThat(lot.matches("0", "736", "0")).isFalse();
        assertThat(lot.matches("1", "736", "1")).isFalse();
    }
}
