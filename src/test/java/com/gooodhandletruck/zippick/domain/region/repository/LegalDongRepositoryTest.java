package com.gooodhandletruck.zippick.domain.region.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.global.config.JpaConfig;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 법정동코드표 저장 규칙 (legal_dongs). (#4 테스트 보강에서 추가)
 * 법정동코드는 공식 코드라 자동 증가가 아니고, 같은 코드로 다시 저장하면 새 행이 아니라 덮어써요.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 팀 테스트 설정(H2 MODE=MySQL) 그대로
@Import(JpaConfig.class)
class LegalDongRepositoryTest {

    @Autowired
    private LegalDongRepository legalDongRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void 법정동코드로_저장하고_찾는다() {
        legalDongRepository.saveAndFlush(TestFixtures.legalDong().build());
        em.clear();

        LegalDong found = legalDongRepository.findById("1144010100").orElseThrow();

        assertThat(found.getSggCd()).isEqualTo("11440");
        assertThat(found.getSidoNm()).isEqualTo("서울특별시");
        assertThat(found.getSggNm()).isEqualTo("마포구");
        assertThat(found.getUmdNm()).isEqualTo("아현동");
    }

    @Test
    void 없는_법정동코드는_못_찾는다() {
        assertThat(legalDongRepository.findById("0000000000")).isNotPresent();
    }

    @Test
    void 같은_법정동코드로_다시_저장하면_한_행으로_덮어쓴다() {
        // 법정동코드 파일을 다시 넣어도 중복 행이 생기지 않아야 해요
        legalDongRepository.saveAndFlush(TestFixtures.legalDong().build());
        legalDongRepository.saveAndFlush(TestFixtures.legalDong().umdNm("아현동(변경)").build());
        em.clear();

        assertThat(legalDongRepository.count()).isEqualTo(1);
        assertThat(legalDongRepository.findById("1144010100").orElseThrow().getUmdNm()).isEqualTo("아현동(변경)");
    }

    @Test
    void 한_구에_여러_법정동을_저장한다() {
        legalDongRepository.saveAndFlush(TestFixtures.legalDong().build());
        legalDongRepository.saveAndFlush(TestFixtures.legalDong().admCd("1144010200").umdNm("공덕동").build());

        assertThat(legalDongRepository.count()).isEqualTo(2);
    }

    @ParameterizedTest(name = "필수 칸 {0} 이 비면 거부")
    @MethodSource("필수_칸을_비운_법정동")
    void 필수_칸이_비면_저장이_거부된다(String field, LegalDong legalDong) {
        assertThatThrownBy(() -> legalDongRepository.saveAndFlush(legalDong))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    static Stream<Arguments> 필수_칸을_비운_법정동() {
        return Stream.of(
                Arguments.of("sgg_cd", TestFixtures.legalDong().sggCd(null).build()),
                Arguments.of("sido_nm", TestFixtures.legalDong().sidoNm(null).build()),
                Arguments.of("sgg_nm", TestFixtures.legalDong().sggNm(null).build()),
                Arguments.of("umd_nm", TestFixtures.legalDong().umdNm(null).build()));
    }

    @ParameterizedTest(name = "{0} 길이 초과는 거부")
    @MethodSource("길이를_넘는_법정동")
    void 최대_길이를_넘으면_거부된다(String field, LegalDong legalDong) {
        assertThatThrownBy(() -> legalDongRepository.saveAndFlush(legalDong))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    static Stream<Arguments> 길이를_넘는_법정동() {
        return Stream.of(
                Arguments.of("sgg_cd 6자", TestFixtures.legalDong().sggCd("114400").build()),
                Arguments.of("umd_nm 21자", TestFixtures.legalDong().umdNm("동".repeat(21)).build()));
    }
}
