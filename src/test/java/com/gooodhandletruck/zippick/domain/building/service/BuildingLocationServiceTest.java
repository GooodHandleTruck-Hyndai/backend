package com.gooodhandletruck.zippick.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingLocationRepository;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.external.juso.JusoClient;
import com.gooodhandletruck.zippick.global.external.juso.dto.JusoSearchResponse.Juso;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuildingLocationServiceTest {

    /** 아현동(37.5540, 126.9560)의 UTM-K */
    private static final double[] AHYEON_UTMK = {951950.261, 1950656.287};

    @Mock
    private BuildingLocationRepository buildingLocationRepository;

    @Mock
    private LegalDongRepository legalDongRepository;

    @Mock
    private JusoClient jusoClient;

    @InjectMocks
    private BuildingLocationService service;

    private Building building;

    @BeforeEach
    void setUp() {
        // 마포구(11440) 아현동 777 마포래미안푸르지오1단지
        building = TestFixtures.building().build();
        given(buildingLocationRepository.findById(1L)).willReturn(Optional.of(building));
        given(legalDongRepository.findFirstBySggCd("11440")).willReturn(Optional.of(TestFixtures.legalDong().build()));
        given(legalDongRepository.findById(anyString())).willReturn(Optional.empty());
    }

    @Test
    void 같은_구_같은_지번을_찾아_좌표를_저장한다() {
        Juso juso = juso("1144010100", "777", "0", "마포래미안푸르지오1단지");
        given(jusoClient.search("서울특별시 마포구 아현동 777")).willReturn(List.of(juso));
        given(jusoClient.coordinate(juso)).willReturn(Optional.of(AHYEON_UTMK));

        CoordSource result = service.fill(1L);

        assertThat(result).isEqualTo(CoordSource.JUSO);
        assertThat(building.getCoordSource()).isEqualTo(CoordSource.JUSO);
        assertThat(building.getLatitude()).isCloseTo(37.5540, within(0.000001));
        assertThat(building.getLongitude()).isCloseTo(126.9560, within(0.000001));
        assertThat(building.getRoadAddr()).isEqualTo("도로명 마포래미안푸르지오1단지");
    }

    @Test
    void 한_지번에_단지가_여러_개면_단지명이_겹치는_걸_고른다() {
        Juso second = juso("1144010100", "777", "0", "마포래미안푸르지오2단지");
        Juso first = juso("1144010100", "777", "0", "마포래미안푸르지오1단지");
        given(jusoClient.search(anyString())).willReturn(List.of(second, first));
        given(jusoClient.coordinate(first)).willReturn(Optional.of(AHYEON_UTMK));

        service.fill(1L);

        assertThat(building.getRoadAddr()).isEqualTo("도로명 마포래미안푸르지오1단지");
        verify(jusoClient, never()).coordinate(second);
    }

    @Test
    void 다른_구나_다른_지번만_나오면_NONE() {
        given(jusoClient.search(anyString())).willReturn(List.of(
                juso("2611010100", "777", "0", "부산 어딘가"),
                juso("1144010100", "778", "0", "옆 건물")));

        CoordSource result = service.fill(1L);

        assertThat(result).isEqualTo(CoordSource.NONE);
        assertThat(building.getCoordSource()).isEqualTo(CoordSource.NONE);
        assertThat(building.getLatitude()).isNull();
        verify(jusoClient, never()).coordinate(any());
    }

    @Test
    void 지번이_비어_있으면_행안부를_안_부르고_NONE() {
        Building noLot = TestFixtures.building().jibun("").build();
        given(buildingLocationRepository.findById(2L)).willReturn(Optional.of(noLot));

        assertThat(service.fill(2L)).isEqualTo(CoordSource.NONE);
        verify(jusoClient, never()).search(anyString());
    }

    @Test
    void 행안부_API_오류면_저장하지_않고_예외를_던진다() {
        given(jusoClient.search(anyString())).willThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));

        assertThatThrownBy(() -> service.fill(1L)).isInstanceOf(BusinessException.class);
        assertThat(building.getCoordSource()).isNull(); // 다음 배치에서 다시 시도
    }

    private Juso juso(String admCd, String lnbrMnnm, String lnbrSlno, String bdNm) {
        return new Juso("도로명 " + bdNm, "지번 " + bdNm, bdNm, admCd, "114403000001", "0", "100", "0",
                "0", lnbrMnnm, lnbrSlno);
    }
}
