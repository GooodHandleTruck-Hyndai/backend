package com.gooodhandletruck.zippick.domain;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 테스트용 기본 데이터. (#4 테스트 보강에서 추가)
 * 실제 국토부 응답(마포구 아현동 777 마포래미안푸르지오, 2026-10)과 비슷한 값으로 맞췄어요.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static Building.BuildingBuilder building() {
        return Building.builder()
                .buildingType(BuildingType.APT)
                .sggCd("11440")
                .umdNm("아현동")
                .jibun("777")
                .aptNm("마포래미안푸르지오1단지")
                .buildYear(2014);
    }

    public static Trade.TradeBuilder trade(Building building) {
        return Trade.builder()
                .building(building)
                .fingerprint("a".repeat(64))
                .seq(1)
                .sggCd("11440")
                .dealYm("202610")
                .dealDate(LocalDate.of(2026, 10, 12))
                .dealAmount(125000)
                .excluUseAr(new BigDecimal("84.97"))
                .floor(12);
    }

    public static LegalDong.LegalDongBuilder legalDong() {
        return LegalDong.builder()
                .admCd("1144010100")
                .sggCd("11440")
                .sidoNm("서울특별시")
                .sggNm("마포구")
                .umdNm("아현동");
    }
}
