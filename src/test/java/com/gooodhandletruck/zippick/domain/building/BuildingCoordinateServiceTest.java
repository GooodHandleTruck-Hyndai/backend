package com.gooodhandletruck.zippick.domain.building;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gooodhandletruck.zippick.domain.building.dto.BuildingAddress;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingCoordinateResponse;
import com.gooodhandletruck.zippick.domain.building.service.BuildingCoordinateService;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.external.kakao.KakaoLocalClient;
import com.gooodhandletruck.zippick.global.external.kakao.dto.KakaoAddressResponse.Document;
import com.gooodhandletruck.zippick.global.external.kakao.dto.KakaoAddressResponse.JibunAddress;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BuildingCoordinateServiceTest {

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    private BuildingCoordinateService service;

    @BeforeEach
    void setUp() {
        // 테스트에서는 스레드 풀 대신 바로 실행
        service = new BuildingCoordinateService(kakaoLocalClient, Runnable::run);
    }

    @Test
    void 도로명_주소로_좌표를_찾는다() {
        given(kakaoLocalClient.searchAddress("테헤란로 427")).willReturn(List.of(
            document("서울 강남구 테헤란로 427", "127.0364", "37.5009", "1168010100")));

        BuildingCoordinateResponse response = service.findCoordinate(building("테헤란로", "00427", "00000"));

        assertThat(response.found()).isTrue();
        assertThat(response.lat()).isEqualTo(37.5009);
        assertThat(response.lng()).isEqualTo(127.0364);
    }

    @Test
    void 다른_지역의_같은_도로명은_무시하고_지번으로_찾는다() {
        given(kakaoLocalClient.searchAddress("테헤란로 427")).willReturn(List.of(
            document("부산 어딘가 테헤란로 427", "129.0", "35.1", "2611010100")));
        given(kakaoLocalClient.searchAddress("역삼동 736-1")).willReturn(List.of(
            document("서울 강남구 역삼동 736-1", "127.0364", "37.5009", "1168010100")));

        BuildingCoordinateResponse response = service.findCoordinate(building("테헤란로", "00427", "00000"));

        assertThat(response.found()).isTrue();
        assertThat(response.lat()).isEqualTo(37.5009);
    }

    @Test
    void 도로명이_없으면_바로_지번으로_찾는다() {
        given(kakaoLocalClient.searchAddress("역삼동 736-1")).willReturn(List.of(
            document("서울 강남구 역삼동 736-1", "127.0364", "37.5009", "1168010100")));

        BuildingCoordinateResponse response = service.findCoordinate(building(null, null, null));

        assertThat(response.found()).isTrue();
        verify(kakaoLocalClient, never()).searchAddress("테헤란로 427");
    }

    @Test
    void 카카오_호출이_실패하면_found_false() {
        given(kakaoLocalClient.searchAddress(anyString()))
            .willThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));

        BuildingCoordinateResponse response = service.findCoordinate(building("테헤란로", "00427", "00000"));

        assertThat(response.found()).isFalse();
        assertThat(response.lat()).isNull();
    }

    @Test
    void 여러_건물을_요청_순서대로_돌려준다() {
        given(kakaoLocalClient.searchAddress(anyString())).willReturn(List.of());

        List<BuildingCoordinateResponse> responses = service.findCoordinates(List.of(
            new BuildingAddress("A-1", "11680", null, null, null, "역삼동", "1"),
            new BuildingAddress("A-2", "11680", null, null, null, "역삼동", "2")));

        assertThat(responses).extracting(BuildingCoordinateResponse::aptSeq).containsExactly("A-1", "A-2");
    }

    private BuildingAddress building(String roadNm, String bonbun, String bubun) {
        return new BuildingAddress("11680-3482", "11680", roadNm, bonbun, bubun, "역삼동", "736-1");
    }

    private Document document(String addressName, String x, String y, String legalDongCode) {
        return new Document(addressName, x, y, new JibunAddress(legalDongCode));
    }
}
