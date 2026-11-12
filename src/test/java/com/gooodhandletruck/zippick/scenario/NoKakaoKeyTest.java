package com.gooodhandletruck.zippick.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingAddress;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingIdentifyResponse;
import com.gooodhandletruck.zippick.domain.building.dto.NearbyBuildingsResponse;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.building.service.BuildingCoordinateService;
import com.gooodhandletruck.zippick.domain.building.service.BuildingIdentifyService;
import com.gooodhandletruck.zippick.domain.building.service.NearbyBuildingService;
import com.gooodhandletruck.zippick.domain.device.dto.DeviceRegisterRequest;
import com.gooodhandletruck.zippick.domain.device.dto.DeviceResponse;
import com.gooodhandletruck.zippick.domain.device.repository.DeviceRepository;
import com.gooodhandletruck.zippick.domain.device.service.DeviceService;
import com.gooodhandletruck.zippick.domain.region.dto.RegionResponse;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.domain.region.service.RegionService;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.external.kakao.KakaoAvailability;
import com.gooodhandletruck.zippick.global.external.kakao.KakaoLocalClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * #55: 카카오 키 없을 때(로컬 e2e — 심사위원이 Docker 로 실행) 카카오 없이 동작하는지 확인 (#55 에서 추가)
 *
 * - 지역 찾기(/regions): DB 에서 가장 가까운 행안부 좌표 건물의 구·동으로 대체
 * - 주변 건물·앞쪽 건물: 카카오 호출 없이 행안부 좌표 건물만으로 응답 (키 없이 호출하면 매번 401 + 1초 재시도)
 * - 카카오 쪽은 목으로 대체 후 "호출 0번" 확인
 */
@SpringBootTest(properties = {"kakao.rest-api-key=", // 키 없음
        "zippick.nearby.kakao-chunk-pause-ms=0", "zippick.nearby.kakao-retry-pause-ms=0"})
@Transactional
class NoKakaoKeyTest {

    // 마포 공덕역 근처
    private static final double LAT = 37.5440;
    private static final double LON = 126.9515;

    @Autowired
    private KakaoAvailability kakaoAvailability;
    @Autowired
    private RegionService regionService;
    @Autowired
    private NearbyBuildingService nearbyBuildingService;
    @Autowired
    private BuildingIdentifyService buildingIdentifyService;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private LegalDongRepository legalDongRepository;
    @Autowired
    private DeviceService deviceService;
    @Autowired
    private DeviceRepository deviceRepository;

    @MockitoBean
    private KakaoLocalClient kakaoLocalClient;
    @MockitoBean
    private BuildingCoordinateService buildingCoordinateService;

    private LegalDong gongdeok;
    private Building near;      // 행안부 좌표, 공덕동, 약 100m 북쪽
    private Building noCoord;   // 행안부가 못 찾은 건물 (카카오로만 찾을 수 있음)

    @BeforeEach
    void setUp() {
        legalDongRepository.save(TestFixtures.legalDong().build()); // 마포구 아현동 (구 이름 출처)
        gongdeok = legalDongRepository.save(TestFixtures.legalDong().admCd("1144010200").umdNm("공덕동").build());
        near = buildingRepository.save(TestFixtures.building().umdNm("공덕동").jibun("1").aptNm("가까운 단지").build());
        near.updateLocation(gongdeok, null, LAT + 0.0009, LON, CoordSource.JUSO);
        Building far = buildingRepository.save(TestFixtures.building().umdNm("상암동").jibun("2").aptNm("먼 단지").build());
        far.updateLocation(null, null, 37.5790, 126.8890, CoordSource.JUSO); // 상암, 약 7km
        noCoord = buildingRepository.save(TestFixtures.building().umdNm("공덕동").jibun("3").aptNm("좌표 없는 단지").build());
        noCoord.updateLocation(null, null, null, null, CoordSource.NONE);
        buildingRepository.flush();
    }

    @Test
    void 키가_비어_있으면_키_없음_모드다() {
        assertThat(kakaoAvailability.enabled()).isFalse();
    }

    @Test
    void 지역은_가장_가까운_건물의_구_동으로_답하고_카카오는_안_부른다() {
        RegionResponse region = regionService.findByCoordinate(LAT, LON);

        assertThat(region.lawdCd()).isEqualTo("11440");          // 앱이 쓰는 값 (관심 조건 지역)
        assertThat(region.legalDongCode()).isEqualTo("1144010200");
        assertThat(region.dongName()).isEqualTo("공덕동");
        assertThat(region.addressName()).isEqualTo("서울특별시 마포구 공덕동");
        verify(kakaoLocalClient, never()).coordToRegion(anyDouble(), anyDouble());
    }

    @Test
    void 법정동이_매칭_안_된_건물이면_구_이름만_법정동표에서_가져온다() {
        // 상암 건물 바로 앞 — 법정동 매칭 없는 건물 (실데이터 346개 중 21개만 매칭)
        RegionResponse region = regionService.findByCoordinate(37.5791, 126.8890);

        assertThat(region.lawdCd()).isEqualTo("11440");
        assertThat(region.legalDongCode()).isNull();
        assertThat(region.addressName()).isEqualTo("서울특별시 마포구 상암동");
    }

    @Test
    void 데이터가_있는_곳에서_10km_넘게_떨어지면_R001() {
        assertThatThrownBy(() -> regionService.findByCoordinate(35.1796, 129.0756)) // 부산
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REGION_NOT_FOUND);
    }

    @Test
    void 주변_건물은_카카오_없이_행안부_좌표_건물만_돌려준다() {
        NearbyBuildingsResponse response = nearbyBuildingService.findNearby(LAT, LON, 1000);

        assertThat(response.buildings()).extracting(NearbyBuildingsResponse.NearbyBuilding::buildingId)
                .containsExactly(near.getId());
        verify(buildingCoordinateService, never()).findCoordinates(anyList());
        verify(buildingCoordinateService, never()).findCoordinate(any(BuildingAddress.class));
    }

    @Test
    void 앞쪽_건물도_카카오_없이_행안부_좌표_건물만_본다() {
        BuildingIdentifyResponse response = buildingIdentifyService.identify(registerDevice(), LAT, LON, 0);

        assertThat(response.candidates()).extracting(BuildingIdentifyResponse.Candidate::buildingId)
                .containsExactly(near.getId());
        verify(buildingCoordinateService, never()).findCoordinates(anyList());
    }

    private CurrentDevice registerDevice() {
        DeviceResponse response = deviceService.register(new DeviceRegisterRequest("0.1.0"));
        Long id = deviceRepository.findByAnonymousId(response.anonymousId()).orElseThrow().getId();
        return new CurrentDevice(id, response.anonymousId());
    }
}
