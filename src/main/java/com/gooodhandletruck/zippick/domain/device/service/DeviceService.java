package com.gooodhandletruck.zippick.domain.device.service;

import com.gooodhandletruck.zippick.domain.device.dto.DeviceRegisterRequest;
import com.gooodhandletruck.zippick.domain.device.dto.DeviceResponse;
import com.gooodhandletruck.zippick.domain.device.entity.Device;
import com.gooodhandletruck.zippick.domain.device.repository.DeviceRepository;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceService {

    private final DeviceRepository deviceRepository;

    @Transactional
    public DeviceResponse register(DeviceRegisterRequest request) {
        String appVersion = request == null ? null : request.appVersion();
        Device device = deviceRepository.saveAndFlush(Device.register(appVersion));
        return DeviceResponse.from(device);
    }

    public DeviceResponse getMyDevice(CurrentDevice currentDevice) {
        Device device = deviceRepository.findById(currentDevice.deviceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
        return DeviceResponse.from(device);
    }
}
