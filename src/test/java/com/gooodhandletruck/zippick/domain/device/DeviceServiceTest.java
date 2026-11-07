package com.gooodhandletruck.zippick.domain.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.gooodhandletruck.zippick.domain.device.dto.DeviceRegisterRequest;
import com.gooodhandletruck.zippick.domain.device.dto.DeviceResponse;
import com.gooodhandletruck.zippick.domain.device.repository.DeviceRepository;
import com.gooodhandletruck.zippick.domain.device.service.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DeviceServiceTest {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Test
    void 기기를_등록하면_익명ID가_발급되고_저장된다() {
        DeviceResponse response = deviceService.register(new DeviceRegisterRequest("0.1.0"));

        assertThat(response.anonymousId()).hasSize(36);
        assertThat(deviceRepository.findByAnonymousId(response.anonymousId())).isPresent();
    }

    @Test
    void 요청_본문이_없어도_등록된다() {
        DeviceResponse response = deviceService.register(null);

        assertThat(response.anonymousId()).isNotBlank();
    }
}
