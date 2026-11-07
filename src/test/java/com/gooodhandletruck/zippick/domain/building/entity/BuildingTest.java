package com.gooodhandletruck.zippick.domain.building.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Building 엔티티 단위 테스트 (DB 없이). (#4 테스트 보강에서 추가)
 * 지번 정리 규칙은 건물 구분 키(UNIQUE)에 들어가서, 바뀌면 같은 건물이 두 번 저장될 수 있어요.
 */
class BuildingTest {

    @ParameterizedTest(name = "지번 [{0}] → 빈 문자열")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void 비어_있는_지번은_빈_문자열로_통일한다(String jibun) {
        Building building = TestFixtures.building().jibun(jibun).build();

        assertThat(building.getJibun()).isEmpty();
    }

    @ParameterizedTest(name = "지번 [{0}] → [{1}]")
    @CsvSource(delimiter = '|', value = {
            "777|777",
            "' 777'|777",
            "'777 '|777",
            "' 12-3 '|12-3",
            "산 12|산 12"
    })
    void 지번_앞뒤_공백은_지운다(String jibun, String expected) {
        Building building = TestFixtures.building().jibun(jibun).build();

        assertThat(building.getJibun()).isEqualTo(expected);
    }

    @Test
    void 새로_만든_건물은_좌표와_주소가_비어_있다() {
        Building building = TestFixtures.building().build();

        assertThat(building.getId()).isNull();
        assertThat(building.getLegalDong()).isNull();
        assertThat(building.getRoadAddr()).isNull();
        assertThat(building.getLatitude()).isNull();
        assertThat(building.getLongitude()).isNull();
        assertThat(building.getCoordSource()).isNull();
    }

    @Test
    void 빌더로_넣은_건물_구분_값이_그대로_들어간다() {
        Building building = TestFixtures.building().build();

        assertThat(building.getBuildingType()).isEqualTo(BuildingType.APT);
        assertThat(building.getSggCd()).isEqualTo("11440");
        assertThat(building.getUmdNm()).isEqualTo("아현동");
        assertThat(building.getAptNm()).isEqualTo("마포래미안푸르지오1단지");
        assertThat(building.getBuildYear()).isEqualTo(2014);
    }

    @Test
    void 주소와_좌표를_채운다() {
        Building building = TestFixtures.building().build();
        LegalDong ahyeon = TestFixtures.legalDong().build();

        building.updateLocation(ahyeon, "서울특별시 마포구 마포대로 195", 37.5533, 126.9567, CoordSource.JUSO);

        assertThat(building.getLegalDong()).isSameAs(ahyeon);
        assertThat(building.getRoadAddr()).isEqualTo("서울특별시 마포구 마포대로 195");
        assertThat(building.getLatitude()).isEqualTo(37.5533);
        assertThat(building.getLongitude()).isEqualTo(126.9567);
        assertThat(building.getCoordSource()).isEqualTo(CoordSource.JUSO);
    }

    @Test
    void 주소_매칭에_실패하면_좌표_없이_NONE_으로_남긴다() {
        Building building = TestFixtures.building().build();

        building.updateLocation(null, null, null, null, CoordSource.NONE);

        assertThat(building.getLatitude()).isNull();
        assertThat(building.getCoordSource()).isEqualTo(CoordSource.NONE);
    }

    @Test
    void 좌표를_다시_채우면_마지막_값으로_바뀐다() {
        Building building = TestFixtures.building().build();
        building.updateLocation(null, null, 1.0, 2.0, CoordSource.KAKAO);

        building.updateLocation(null, "새 주소", 37.5, 126.9, CoordSource.JUSO);

        assertThat(building.getLatitude()).isEqualTo(37.5);
        assertThat(building.getLongitude()).isEqualTo(126.9);
        assertThat(building.getRoadAddr()).isEqualTo("새 주소");
        assertThat(building.getCoordSource()).isEqualTo(CoordSource.JUSO);
    }

    @Test
    void 좌표를_채워도_건물_구분_값은_바뀌지_않는다() {
        Building building = TestFixtures.building().build();

        building.updateLocation(null, "주소", 37.5, 126.9, CoordSource.JUSO);

        assertThat(building.getSggCd()).isEqualTo("11440");
        assertThat(building.getUmdNm()).isEqualTo("아현동");
        assertThat(building.getJibun()).isEqualTo("777");
        assertThat(building.getAptNm()).isEqualTo("마포래미안푸르지오1단지");
    }

    @ParameterizedTest
    @EnumSource(BuildingType.class)
    void 건물_유형은_API_명세의_type_값과_같은_이름이다(BuildingType type) {
        // API 응답 type 에 enum 이름을 그대로 쓰기로 해서, 이름이 바뀌면 앱과 어긋나요
        assertThat(type.name()).isIn("APT", "OFFICETEL");
    }

    @ParameterizedTest
    @EnumSource(CoordSource.class)
    void 좌표_출처는_세_가지다(CoordSource source) {
        assertThat(source.name()).isIn("JUSO", "KAKAO", "NONE");
    }
}
