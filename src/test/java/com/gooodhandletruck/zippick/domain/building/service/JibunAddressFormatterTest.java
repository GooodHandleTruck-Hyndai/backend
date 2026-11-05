package com.gooodhandletruck.zippick.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 지번 주소 형식 테스트. (#6 에서 추가)
 * 명세 예시 「서울 마포구 상암동 1601」 처럼 시도는 줄여서 써요.
 */
class JibunAddressFormatterTest {

    @Test
    void 명세_예시와_같은_형식으로_만든다() {
        LegalDong mapo = TestFixtures.legalDong().sidoNm("서울특별시").sggNm("마포구").build();

        assertThat(JibunAddressFormatter.format(mapo, "상암동", "1601")).isEqualTo("서울 마포구 상암동 1601");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "서울특별시, 서울", "부산광역시, 부산", "대구광역시, 대구", "인천광역시, 인천", "광주광역시, 광주",
            "대전광역시, 대전", "울산광역시, 울산", "세종특별자치시, 세종", "경기도, 경기", "강원도, 강원",
            "강원특별자치도, 강원", "충청북도, 충북", "충청남도, 충남", "전라북도, 전북", "전북특별자치도, 전북",
            "전라남도, 전남", "경상북도, 경북", "경상남도, 경남", "제주특별자치도, 제주"
    })
    void 시도_이름을_줄여서_쓴다(String sidoNm, String shortName) {
        LegalDong region = TestFixtures.legalDong().sidoNm(sidoNm).sggNm("어느구").build();

        assertThat(JibunAddressFormatter.format(region, "어느동", "1")).isEqualTo(shortName + " 어느구 어느동 1");
    }

    @Test
    void 모르는_시도_이름은_그대로_쓴다() {
        LegalDong region = TestFixtures.legalDong().sidoNm("새로운특별시").sggNm("가구").build();

        assertThat(JibunAddressFormatter.format(region, "나동", "1")).isEqualTo("새로운특별시 가구 나동 1");
    }

    @Test
    void 일반구가_있는_시는_시군구_이름을_그대로_쓴다() {
        LegalDong bundang = TestFixtures.legalDong().sidoNm("경기도").sggNm("성남시 분당구").build();

        assertThat(JibunAddressFormatter.format(bundang, "정자동", "178-1")).isEqualTo("경기 성남시 분당구 정자동 178-1");
    }

    @Test
    void 법정동코드표에_구가_없으면_동과_지번만_쓴다() {
        assertThat(JibunAddressFormatter.format(null, "상암동", "1601")).isEqualTo("상암동 1601");
    }

    @ParameterizedTest(name = "지번 [{0}] → 동까지만")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void 지번이_비어_있으면_동까지만_쓴다(String jibun) {
        LegalDong mapo = TestFixtures.legalDong().sidoNm("서울특별시").sggNm("마포구").build();

        assertThat(JibunAddressFormatter.format(mapo, "상암동", jibun)).isEqualTo("서울 마포구 상암동");
    }

    @Test
    void 지번_앞뒤_공백은_지운다() {
        assertThat(JibunAddressFormatter.format(null, "상암동", " 1601 ")).isEqualTo("상암동 1601");
    }
}
