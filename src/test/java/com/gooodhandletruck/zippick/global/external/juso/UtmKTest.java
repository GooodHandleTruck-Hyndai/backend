package com.gooodhandletruck.zippick.global.external.juso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UtmKTest {

    /** 기대값은 pyproj(EPSG:4326 → 5179)로 만든 값 */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "서울시청, 953901.165, 1952032.081, 37.5665, 126.9780",
            "아현동, 951950.261, 1950656.287, 37.5540, 126.9560",
            "부산, 1143467.380, 1688281.982, 35.1796, 129.0756",
            "제주, 910010.546, 1501279.789, 33.4996, 126.5312"
    })
    void UTM_K_를_위도_경도로_바꾼다(String name, double x, double y, double lat, double lon) {
        double[] result = UtmK.toWgs84(x, y);

        // 0.000001도 ≈ 0.1m
        assertThat(result[0]).isCloseTo(lat, within(0.000001));
        assertThat(result[1]).isCloseTo(lon, within(0.000001));
    }
}
