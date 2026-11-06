package com.gooodhandletruck.zippick.domain.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gooodhandletruck.zippick.domain.trade.client.MolitFixtures;
import com.gooodhandletruck.zippick.domain.trade.client.MolitTradeItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 값 정리·지문·순번 규칙. 실제 마포구 2026-08 응답 121건으로 확인해요. (#8 에서 추가)
 *
 * 지문이 한 번 정해진 뒤 바뀌면 재수집 때 모든 거래가 "삭제 + 추가"로 잡혀서, 규칙을 여기서 고정해요.
 */
class MolitTradeNormalizerTest {

    private static final List<MolitTradeItem> MAPO = MolitFixtures.mapo202608().items();

    private static List<NormalizedTrade> normalize(List<MolitTradeItem> items) {
        return MolitTradeNormalizer.normalize("11440", "202608", items);
    }

    private static MolitTradeItem first(Map<String, String> overrides) {
        return MolitFixtures.copy(MAPO.get(0), overrides);
    }

    // ---- 정상: 실제 응답 ----

    @Test
    void 실제_응답_121건을_모두_정리한다() {
        List<NormalizedTrade> rows = normalize(MAPO);

        assertThat(rows).hasSize(121);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.fingerprint()).hasSize(64).matches("[0-9a-f]{64}");
            assertThat(r.sggCd()).isEqualTo("11440");
            assertThat(r.dealYm()).isEqualTo("202608");
            assertThat(r.dealDate()).hasYear(2026).hasMonthValue(8);
            assertThat(r.dealAmount()).isPositive();
        });
    }

    @Test
    void 첫_거래의_값을_정리한다() {
        // 원본: aptDong " ", dealAmount "139,000", cdealDay "26.10.07", rgstDate " "
        NormalizedTrade row = normalize(MAPO).get(0);

        assertThat(row.aptNm()).isEqualTo("상암월드컵파크7단지");
        assertThat(row.umdNm()).isEqualTo("상암동");
        assertThat(row.jibun()).isEqualTo("1660");
        assertThat(row.buildYear()).isEqualTo(2005);
        assertThat(row.dealDate()).isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(row.dealAmount()).isEqualTo(139000);
        assertThat(row.excluUseAr()).isEqualByComparingTo("84.45");
        assertThat(row.floor()).isEqualTo(2);
        assertThat(row.aptDong()).isNull();
        assertThat(row.cdealType()).isEqualTo("O");
        assertThat(row.cdealDay()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(row.rgstDate()).isNull();
        assertThat(row.dealingGbn()).isEqualTo("중개거래");
        assertThat(row.landLeaseholdGbn()).isEqualTo("N");
        assertThat(row.seq()).isEqualTo(1);
    }

    @Test
    void 실제_응답의_값이_같은_거래_6쌍은_순번_1_2로_나뉜다() {
        List<NormalizedTrade> rows = normalize(MAPO);

        Map<String, List<Integer>> seqsByFingerprint = rows.stream().collect(Collectors.groupingBy(
                NormalizedTrade::fingerprint, Collectors.mapping(NormalizedTrade::seq, Collectors.toList())));
        assertThat(seqsByFingerprint.values().stream().filter(s -> s.size() > 1))
                .hasSize(6)
                .allSatisfy(seqs -> assertThat(seqs).containsExactly(1, 2));
        // 거래 고유 키(지문+순번)는 121개 모두 달라야 해요
        assertThat(rows.stream().map(NormalizedTrade::tradeKey).distinct()).hasSize(121);
    }

    @Test
    void 소수_3_4자리_면적을_반올림하지_않는다() {
        assertThat(normalize(MAPO)).extracting(NormalizedTrade::excluUseAr)
                .anySatisfy(a -> assertThat(a).isEqualByComparingTo("59.9236"))
                .anySatisfy(a -> assertThat(a).isEqualByComparingTo("113.803"));
    }

    // ---- 지문 ----

    @Test
    void 같은_입력이면_지문이_항상_같다() {
        assertThat(normalize(MAPO)).extracting(NormalizedTrade::fingerprint)
                .containsExactlyElementsOf(normalize(MAPO).stream().map(NormalizedTrade::fingerprint).toList());
    }

    @Test
    void 바뀌는_칸이_달라져도_지문은_같다() {
        // 동·해제·등기·참고 칸은 나중에 바뀌어서 지문에 넣지 않아요 → 재수집 때 같은 거래로 찾아야 해요
        Map<String, String> changed = new HashMap<>();
        changed.put("aptDong", "101");
        changed.put("cdealType", " ");
        changed.put("cdealDay", " ");
        changed.put("rgstDate", "26.11.01");
        changed.put("dealingGbn", "직거래");
        changed.put("estateAgentSggNm", " ");
        changed.put("buildYear", "2006");

        assertThat(normalize(List.of(first(changed))).get(0).fingerprint())
                .isEqualTo(normalize(List.of(MAPO.get(0))).get(0).fingerprint());
    }

    @ParameterizedTest(name = "{0} 가 바뀌면 다른 거래")
    @CsvSource({"umdNm, 중동", "jibun, 1661", "aptNm, 상암월드컵파크6단지", "dealDay, 28", "excluUseAr, 84.46",
            "floor, 3", "dealAmount, '139,001'"})
    void 바뀌지_않는_칸이_다르면_지문이_다르다(String field, String value) {
        assertThat(normalize(List.of(first(Map.of(field, value)))).get(0).fingerprint())
                .isNotEqualTo(normalize(List.of(MAPO.get(0))).get(0).fingerprint());
    }

    @Test
    void 공백_쉼표_끝자리_0_차이는_같은_지문이다() {
        // 국토부가 표기만 바꿔 보내도("84.45" → "84.450", 앞 공백) 같은 거래로 봐야 중복 저장이 안 생겨요
        MolitTradeItem reformatted = first(Map.of("excluUseAr", "84.450", "dealAmount", "   139,000",
                "aptNm", " 상암월드컵파크7단지 ", "floor", "02"));

        assertThat(normalize(List.of(reformatted)).get(0).fingerprint())
                .isEqualTo(normalize(List.of(MAPO.get(0))).get(0).fingerprint());
    }

    @Test
    void 칸_경계가_섞여도_지문이_다르다() {
        // "1660" + "상암월드컵..." 과 "166" + "0상암월드컵..." 이 같은 문자열로 이어지지 않게 구분자를 넣었어요
        MolitTradeItem shifted = first(Map.of("jibun", "166", "aptNm", "0상암월드컵파크7단지"));

        assertThat(normalize(List.of(shifted)).get(0).fingerprint())
                .isNotEqualTo(normalize(List.of(MAPO.get(0))).get(0).fingerprint());
    }

    // ---- 빈 값 ----

    @Test
    void 빈_지번은_빈_문자열로_통일한다() {
        assertThat(normalize(List.of(first(Map.of("jibun", " ")))).get(0).jibun()).isEqualTo("");
    }

    @Test
    void 태그가_없는_선택_칸은_null_이다() {
        Map<String, String> missing = new HashMap<>();
        missing.put("buildYear", null);
        missing.put("aptDong", null);
        missing.put("cdealType", null);
        missing.put("cdealDay", null);
        missing.put("jibun", null);

        NormalizedTrade row = normalize(List.of(first(missing))).get(0);

        assertThat(row.buildYear()).isNull();
        assertThat(row.aptDong()).isNull();
        assertThat(row.cdealType()).isNull();
        assertThat(row.cdealDay()).isNull();
        assertThat(row.jibun()).isEqualTo("");
    }

    @Test
    void 지하층은_음수로_받는다() {
        assertThat(normalize(List.of(first(Map.of("floor", "-1")))).get(0).floor()).isEqualTo(-1);
    }

    // ---- 이상한 값: 그 달 전체 실패 ----

    @ParameterizedTest(name = "{0}=\"{1}\" → 실패")
    @CsvSource({
            "umdNm, ' '", "aptNm, ' '", "dealAmount, ' '", "excluUseAr, ' '", "floor, ' '", "dealDay, ' '",
            "dealAmount, 십억", "dealAmount, 0", "dealAmount, '-1,000'",
            "excluUseAr, abc", "excluUseAr, 0", "excluUseAr, 84.12345", "excluUseAr, 123456.1",
            "floor, 2층", "dealDay, 32", "dealMonth, 13",
            "cdealType, X", "cdealDay, 2026-10-07", "rgstDate, 26.02.30",
            "buildYear, 이천오년"
    })
    void 형식이_이상하면_몇_번째_거래인지와_함께_실패한다(String field, String value) {
        List<MolitTradeItem> items = List.of(MAPO.get(0), first(Map.of(field, value)));

        assertThatThrownBy(() -> normalize(items))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("2번째 거래");
    }

    @Test
    void 요청한_구와_다른_거래가_오면_실패한다() {
        // 다른 구 거래를 이 구로 저장하면, 이 구 재수집 때 엉뚱한 거래를 지워요
        assertThatThrownBy(() -> normalize(List.of(first(Map.of("sggCd", "11410")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("11410");
    }

    @Test
    void 요청한_달과_다른_거래가_오면_실패한다() {
        assertThatThrownBy(() -> normalize(List.of(first(Map.of("dealMonth", "9")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("202609");
    }

    @Test
    void 빈_목록이면_빈_결과다() {
        assertThat(normalize(List.of())).isEmpty();
    }

    @Test
    void 면적_경계_소수_4자리와_정수_5자리는_받는다() {
        assertThat(normalize(List.of(first(Map.of("excluUseAr", "99999.9999")))).get(0).excluUseAr())
                .isEqualByComparingTo(new BigDecimal("99999.9999"));
    }
}
