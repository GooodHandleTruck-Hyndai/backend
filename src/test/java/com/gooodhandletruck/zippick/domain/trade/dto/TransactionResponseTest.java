package com.gooodhandletruck.zippick.domain.trade.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 거래 응답 변환 테스트. (#6 에서 추가)
 * 평 환산은 명세 예시(84.97㎡ → 25.7평, 59.98㎡ → 18.1평)와 같아야 앱 화면이 명세와 어긋나지 않아요.
 */
class TransactionResponseTest {

    @ParameterizedTest(name = "{0}㎡ → {1}평")
    @CsvSource({
            "84.97, 25.7",   // 명세 예시
            "59.98, 18.1",   // 명세 예시
            "59.99, 18.1",
            "114.99, 34.8",
            "100.00, 30.2",  // 30.2498.. → 반올림 경계 바로 아래
            "135.00, 40.8",
            "33.06, 10.0",
            "39.67, 12.0",
            "49.59, 15.0",
            "1.00, 0.3",
            "0.01, 0.0",
            "99999.99, 30249.9"
    })
    void 제곱미터를_평으로_소수_1자리_반올림한다(String areaM2, String expected) {
        assertThat(TransactionResponse.toPyeong(new BigDecimal(areaM2))).isEqualByComparingTo(expected);
    }

    @Test
    void 평은_항상_소수_1자리로_나온다() {
        assertThat(TransactionResponse.toPyeong(new BigDecimal("33.06")).scale()).isEqualTo(1);
    }

    @Test
    void 거래_엔티티를_명세_필드로_바꾼다() {
        Trade trade = TestFixtures.trade(TestFixtures.building().build()).aptDong("705").build();

        TransactionResponse response = TransactionResponse.from(trade);

        assertThat(response.dealDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(response.areaM2()).isEqualByComparingTo("84.97");
        assertThat(response.areaPyeong()).isEqualByComparingTo("25.7");
        assertThat(response.floor()).isEqualTo(12);
        assertThat(response.dong()).isEqualTo("705");
        assertThat(response.price()).isEqualTo(125000);
    }

    @Test
    void 면적은_소수_2자리로_맞춘다() {
        Trade trade = TestFixtures.trade(TestFixtures.building().build()).excluUseAr(new BigDecimal("84.9")).build();

        assertThat(TransactionResponse.from(trade).areaM2()).isEqualTo(new BigDecimal("84.90"));
    }

    @Test
    void 등기_전_거래는_동이_null() {
        Trade trade = TestFixtures.trade(TestFixtures.building().build()).aptDong(null).build();

        assertThat(TransactionResponse.from(trade).dong()).isNull();
    }

    @Test
    void 지하층은_음수_그대로() {
        Trade trade = TestFixtures.trade(TestFixtures.building().build()).floor(-1).build();

        assertThat(TransactionResponse.from(trade).floor()).isEqualTo(-1);
    }
}
