package com.gooodhandletruck.zippick.domain.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.gooodhandletruck.zippick.domain.region.dto.RegionResponse;
import com.gooodhandletruck.zippick.domain.region.service.RegionService;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.external.kakao.KakaoLocalClient;
import com.gooodhandletruck.zippick.global.external.kakao.dto.KakaoRegionResponse.Document;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegionServiceTest {

    private static final double LAT = 37.5013;
    private static final double LNG = 127.0396;

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    @InjectMocks
    private RegionService regionService;

    @Test
    void 법정동_결과로_시군구_코드를_만든다() {
        given(kakaoLocalClient.coordToRegion(LAT, LNG)).willReturn(List.of(
            new Document("H", "서울특별시 강남구 역삼1동", "서울특별시", "강남구", "역삼1동", "1168064000"),
            new Document("B", "서울특별시 강남구 역삼동", "서울특별시", "강남구", "역삼동", "1168010100")
        ));

        RegionResponse response = regionService.findByCoordinate(LAT, LNG);

        assertThat(response.lawdCd()).isEqualTo("11680");
        assertThat(response.legalDongCode()).isEqualTo("1168010100");
        assertThat(response.dongName()).isEqualTo("역삼동");
    }

    @Test
    void 법정동_결과가_없으면_R001() {
        given(kakaoLocalClient.coordToRegion(LAT, LNG)).willReturn(List.of());

        assertThatThrownBy(() -> regionService.findByCoordinate(LAT, LNG))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.REGION_NOT_FOUND);
    }
}
