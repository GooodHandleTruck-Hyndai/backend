package com.gooodhandletruck.zippick.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import com.gooodhandletruck.zippick.domain.device.dto.DeviceRegisterRequest;
import com.gooodhandletruck.zippick.domain.device.dto.DeviceResponse;
import com.gooodhandletruck.zippick.domain.device.repository.DeviceRepository;
import com.gooodhandletruck.zippick.domain.device.service.DeviceService;
import com.gooodhandletruck.zippick.domain.interest.dto.InterestConditionRequest;
import com.gooodhandletruck.zippick.domain.interest.dto.InterestConditionResponse;
import com.gooodhandletruck.zippick.domain.interest.repository.InterestConditionRepository;
import com.gooodhandletruck.zippick.domain.interest.service.InterestConditionService;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class InterestConditionServiceTest {

    @Autowired
    private InterestConditionService service;

    @Autowired
    private InterestConditionRepository repository;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private EntityManager entityManager;

    private CurrentDevice me;
    private CurrentDevice other;

    @BeforeEach
    void setUp() {
        me = registerDevice();
        other = registerDevice();
    }

    @Test
    void 설정한_적이_없으면_null() {
        assertThat(service.get(me)).isNull();
    }

    @Test
    void 저장하면_명세와_같은_모양으로_돌려준다() {
        service.save(me, request(List.of("11440"), 80000, 150000, List.of(BuildingType.APT), 59, 85, true));
        entityManager.flush();
        entityManager.clear(); // DB 에서 다시 읽어서 목록 변환까지 확인

        InterestConditionResponse response = service.get(me);

        assertThat(response.regionCodes()).containsExactly("11440");
        assertThat(response.priceMin()).isEqualTo(80000);
        assertThat(response.priceMax()).isEqualTo(150000);
        assertThat(response.types()).containsExactly(BuildingType.APT);
        assertThat(response.areaMinM2()).isEqualTo(59);
        assertThat(response.areaMaxM2()).isEqualTo(85);
        assertThat(response.alertEnabled()).isTrue();
    }

    @Test
    void 다시_저장하면_전체_값이_바뀌고_기기당_하나만_남는다() {
        service.save(me, request(List.of("11440"), 80000, 150000, List.of(BuildingType.APT), 59, 85, true));

        InterestConditionResponse response = service.save(me, request(
                List.of("11680", "11650"), null, 200000, List.of(BuildingType.APT, BuildingType.OFFICETEL),
                null, null, false));

        assertThat(response.regionCodes()).containsExactly("11680", "11650");
        assertThat(response.priceMin()).isNull();
        assertThat(response.types()).containsExactly(BuildingType.APT, BuildingType.OFFICETEL);
        assertThat(response.alertEnabled()).isFalse();
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void 중복된_지역과_유형은_한_번만_저장한다() {
        InterestConditionResponse response = service.save(me, request(
                List.of("11440", "11440"), null, null, List.of(BuildingType.APT, BuildingType.APT), null, null, true));

        assertThat(response.regionCodes()).containsExactly("11440");
        assertThat(response.types()).containsExactly(BuildingType.APT);
    }

    @Test
    void 최소가_최대보다_크면_C001() {
        assertThatThrownBy(() -> service.save(me, request(
                List.of("11440"), 150000, 80000, List.of(BuildingType.APT), null, null, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> service.save(me, request(
                List.of("11440"), null, null, List.of(BuildingType.APT), 85, 59, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 다른_기기의_조건은_안_보인다() {
        service.save(other, request(List.of("11440"), null, null, List.of(BuildingType.APT), null, null, true));

        assertThat(service.get(me)).isNull();
    }

    private InterestConditionRequest request(List<String> regionCodes, Integer priceMin, Integer priceMax,
            List<BuildingType> types, Integer areaMin, Integer areaMax, boolean alertEnabled) {
        return new InterestConditionRequest(regionCodes, priceMin, priceMax, types, areaMin, areaMax, alertEnabled);
    }

    private CurrentDevice registerDevice() {
        DeviceResponse response = deviceService.register(new DeviceRegisterRequest("0.1.0"));
        Long deviceId = deviceRepository.findByAnonymousId(response.anonymousId()).orElseThrow().getId();
        return new CurrentDevice(deviceId, response.anonymousId());
    }
}
