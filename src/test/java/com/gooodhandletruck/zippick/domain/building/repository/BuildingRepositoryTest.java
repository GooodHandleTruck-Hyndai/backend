package com.gooodhandletruck.zippick.domain.building.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.global.config.JpaConfig;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 건물 저장 규칙 (buildings). (#4 테스트 보강에서 추가)
 * 건물 구분 키 (유형, sggCd, umdNm, jibun, aptNm) 가 DB 수준에서 지켜지는지가 핵심이에요.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 팀 테스트 설정(H2 MODE=MySQL) 그대로
@Import(JpaConfig.class)
class BuildingRepositoryTest {

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private LegalDongRepository legalDongRepository;

    @Autowired
    private TestEntityManager em;

    /** 건물 구분 키 5칸 중 한 칸만 다르게 바꾸는 방법들 */
    static Stream<Arguments> 키_한_칸만_다른_건물() {
        return Stream.of(
                Arguments.of("유형", (UnaryOperator<Building.BuildingBuilder>) b -> b.buildingType(BuildingType.OFFICETEL)),
                Arguments.of("구 코드", (UnaryOperator<Building.BuildingBuilder>) b -> b.sggCd("11410")),
                Arguments.of("법정동", (UnaryOperator<Building.BuildingBuilder>) b -> b.umdNm("공덕동")),
                Arguments.of("지번", (UnaryOperator<Building.BuildingBuilder>) b -> b.jibun("778")),
                Arguments.of("단지명", (UnaryOperator<Building.BuildingBuilder>) b -> b.aptNm("마포래미안푸르지오2단지")));
    }

    @ParameterizedTest(name = "{0}만 다르면 다른 건물")
    @MethodSource("키_한_칸만_다른_건물")
    void 건물_구분_키가_한_칸이라도_다르면_따로_저장된다(String field, UnaryOperator<Building.BuildingBuilder> change) {
        buildingRepository.saveAndFlush(TestFixtures.building().build());

        buildingRepository.saveAndFlush(change.apply(TestFixtures.building()).build());

        assertThat(buildingRepository.count()).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0}만 다르면 기존 건물로 못 찾음")
    @MethodSource("키_한_칸만_다른_건물")
    void 건물_구분_키로_찾을_때_한_칸이라도_다르면_못_찾는다(String field, UnaryOperator<Building.BuildingBuilder> change) {
        buildingRepository.saveAndFlush(TestFixtures.building().build());
        Building other = change.apply(TestFixtures.building()).build();

        assertThat(findByKey(other)).isNotPresent();
    }

    @Test
    void 건물_구분_키가_모두_같으면_저장이_거부된다() {
        buildingRepository.saveAndFlush(TestFixtures.building().build());

        assertThatThrownBy(() -> buildingRepository.saveAndFlush(TestFixtures.building().build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 건축년도가_달라도_키가_같으면_같은_건물이라_거부된다() {
        // 건축년도는 키가 아니라서, 국토부가 값을 정정해도 건물이 둘로 갈라지지 않아요
        buildingRepository.saveAndFlush(TestFixtures.building().buildYear(2014).build());

        assertThatThrownBy(() -> buildingRepository.saveAndFlush(TestFixtures.building().buildYear(2015).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 아현동_777의_단지_세_개는_모두_따로_저장된다() {
        for (String apt : new String[] {"마포래미안푸르지오1단지", "마포래미안푸르지오2단지", "마포래미안푸르지오4단지"}) {
            buildingRepository.saveAndFlush(TestFixtures.building().aptNm(apt).build());
        }

        assertThat(buildingRepository.count()).isEqualTo(3);
    }

    @ParameterizedTest(name = "지번 [{0}] 와 [{1}] 는 같은 건물")
    @CsvSource(delimiter = '|', value = {"''|' '", "' '|'   '", "777|' 777 '"})
    void 정리하면_같은_지번은_중복으로_거부된다(String first, String second) {
        buildingRepository.saveAndFlush(TestFixtures.building().jibun(first).build());

        assertThatThrownBy(() -> buildingRepository.saveAndFlush(TestFixtures.building().jibun(second).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 지번이_없는_건물도_빈_지번으로_찾을_수_있다() {
        buildingRepository.saveAndFlush(TestFixtures.building().jibun(null).build());

        assertThat(buildingRepository.findByBuildingTypeAndSggCdAndUmdNmAndJibunAndAptNm(
                BuildingType.APT, "11440", "아현동", "", "마포래미안푸르지오1단지")).isPresent();
    }

    @Test
    void 건물_구분_키로_저장한_건물을_찾는다() {
        Building saved = buildingRepository.saveAndFlush(TestFixtures.building().build());

        assertThat(findByKey(TestFixtures.building().build())).get()
                .extracting(Building::getId).isEqualTo(saved.getId());
    }

    @Test
    void 건물_id는_자동으로_1씩_늘어나는_숫자다() {
        Building first = buildingRepository.saveAndFlush(TestFixtures.building().build());
        Building second = buildingRepository.saveAndFlush(TestFixtures.building().aptNm("다른단지").build());

        assertThat(first.getId()).isPositive();
        assertThat(second.getId()).isGreaterThan(first.getId());
    }

    @ParameterizedTest(name = "필수 칸 {0} 이 비면 거부")
    @MethodSource("필수_칸을_비운_건물")
    void 필수_칸이_비면_저장이_거부된다(String field, Building building) {
        assertThatThrownBy(() -> buildingRepository.saveAndFlush(building))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    static Stream<Arguments> 필수_칸을_비운_건물() {
        return Stream.of(
                Arguments.of("building_type", TestFixtures.building().buildingType(null).build()),
                Arguments.of("sgg_cd", TestFixtures.building().sggCd(null).build()),
                Arguments.of("umd_nm", TestFixtures.building().umdNm(null).build()),
                Arguments.of("apt_nm", TestFixtures.building().aptNm(null).build()));
    }

    @ParameterizedTest(name = "{0} 최대 {1}자는 저장")
    @MethodSource("최대_길이_건물")
    void 최대_길이까지는_저장된다(String field, int length, Building building) {
        assertThat(buildingRepository.saveAndFlush(building).getId()).isNotNull();
    }

    static Stream<Arguments> 최대_길이_건물() {
        return Stream.of(
                Arguments.of("sgg_cd", 5, TestFixtures.building().sggCd("1".repeat(5)).build()),
                Arguments.of("umd_nm", 20, TestFixtures.building().umdNm("동".repeat(20)).build()),
                Arguments.of("jibun", 20, TestFixtures.building().jibun("1".repeat(20)).build()),
                Arguments.of("apt_nm", 100, TestFixtures.building().aptNm("단".repeat(100)).build()));
    }

    @ParameterizedTest(name = "{0} {1}자는 거부")
    @MethodSource("최대_길이를_넘는_건물")
    void 최대_길이를_넘으면_거부된다(String field, int length, Building building) {
        assertThatThrownBy(() -> buildingRepository.saveAndFlush(building))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    static Stream<Arguments> 최대_길이를_넘는_건물() {
        return Stream.of(
                Arguments.of("sgg_cd", 6, TestFixtures.building().sggCd("1".repeat(6)).build()),
                Arguments.of("umd_nm", 21, TestFixtures.building().umdNm("동".repeat(21)).build()),
                Arguments.of("jibun", 21, TestFixtures.building().jibun("1".repeat(21)).build()),
                Arguments.of("apt_nm", 101, TestFixtures.building().aptNm("단".repeat(101)).build()));
    }

    @Test
    void 도로명주소가_200자를_넘으면_거부된다() {
        Building building = buildingRepository.saveAndFlush(TestFixtures.building().build());
        building.updateLocation(null, "가".repeat(201), null, null, CoordSource.NONE);

        assertThatThrownBy(() -> em.flush()).isInstanceOf(Exception.class);
    }

    @Test
    void 법정동을_연결하고_다시_읽으면_같은_법정동이다() {
        LegalDong ahyeon = legalDongRepository.saveAndFlush(TestFixtures.legalDong().build());
        Building building = buildingRepository.saveAndFlush(TestFixtures.building().build());
        building.updateLocation(ahyeon, "서울특별시 마포구 마포대로 195", 37.5533, 126.9567, CoordSource.JUSO);
        em.flush();
        em.clear();

        Building found = buildingRepository.findById(building.getId()).orElseThrow();

        assertThat(found.getLegalDong().getAdmCd()).isEqualTo("1144010100");
        assertThat(found.getRoadAddr()).isEqualTo("서울특별시 마포구 마포대로 195");
        assertThat(found.getCoordSource()).isEqualTo(CoordSource.JUSO);
    }

    @Test
    void 없는_법정동코드로_연결하면_거부된다() {
        LegalDong notSaved = TestFixtures.legalDong().admCd("9999999999").build();
        Building building = buildingRepository.saveAndFlush(TestFixtures.building().build());
        building.updateLocation(em.getEntityManager().getReference(LegalDong.class, notSaved.getAdmCd()),
                null, null, null, CoordSource.NONE);

        assertThatThrownBy(() -> em.flush()).isInstanceOf(Exception.class);
    }

    @ParameterizedTest(name = "좌표 ({0}, {1}) 를 그대로 다시 읽는다")
    @CsvSource({"37.5533, 126.9567", "37.1234567, 127.7654321", "33.0, 124.5", "38.6, 131.9", "-0.0001, 0.0001"})
    void 좌표를_소수점_그대로_저장하고_읽는다(double lat, double lng) {
        Building building = buildingRepository.saveAndFlush(TestFixtures.building().build());
        building.updateLocation(null, null, lat, lng, CoordSource.JUSO);
        em.flush();
        em.clear();

        Building found = buildingRepository.findById(building.getId()).orElseThrow();

        assertThat(found.getLatitude()).isEqualTo(lat);
        assertThat(found.getLongitude()).isEqualTo(lng);
    }

    @ParameterizedTest(name = "유형 {0} 을 이름 그대로 저장")
    @CsvSource({"APT", "OFFICETEL"})
    void 건물_유형을_이름_그대로_저장한다(BuildingType type) {
        Building saved = buildingRepository.saveAndFlush(TestFixtures.building().buildingType(type).build());
        em.clear();

        Object stored = em.getEntityManager()
                .createNativeQuery("select building_type from buildings where id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();

        // 숫자(ORDINAL)로 저장되면 enum 순서만 바뀌어도 데이터가 뒤섞여요
        assertThat(stored.toString()).isEqualTo(type.name());
    }

    @Test
    void 생성_시각과_수정_시각이_자동으로_채워진다() {
        Building saved = buildingRepository.saveAndFlush(TestFixtures.building().build());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void 좌표를_고쳐도_생성_시각은_그대로다() {
        Building saved = buildingRepository.saveAndFlush(TestFixtures.building().build());
        var createdAt = saved.getCreatedAt();

        saved.updateLocation(null, null, 37.5, 126.9, CoordSource.JUSO);
        em.flush();

        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    void 한글_단지명과_동명을_그대로_다시_읽는다() {
        Building saved = buildingRepository.saveAndFlush(TestFixtures.building()
                .umdNm("신공덕동").aptNm("e편한세상 마포리버파크(래미안)").build());
        em.clear();

        Building found = buildingRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getUmdNm()).isEqualTo("신공덕동");
        assertThat(found.getAptNm()).isEqualTo("e편한세상 마포리버파크(래미안)");
    }

    private java.util.Optional<Building> findByKey(Building b) {
        return buildingRepository.findByBuildingTypeAndSggCdAndUmdNmAndJibunAndAptNm(
                b.getBuildingType(), b.getSggCd(), b.getUmdNm(), b.getJibun(), b.getAptNm());
    }
}
