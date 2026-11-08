package com.gooodhandletruck.zippick.domain.interest.service;

import com.gooodhandletruck.zippick.domain.device.repository.DeviceRepository;
import com.gooodhandletruck.zippick.domain.interest.dto.InterestConditionRequest;
import com.gooodhandletruck.zippick.domain.interest.dto.InterestConditionResponse;
import com.gooodhandletruck.zippick.domain.interest.entity.InterestCondition;
import com.gooodhandletruck.zippick.domain.interest.repository.InterestConditionRepository;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관심 조건 조회 / 저장 (API 15·16번, #14 에서 추가) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterestConditionService {

    private final InterestConditionRepository interestConditionRepository;
    private final DeviceRepository deviceRepository;

    /** 15번. 설정한 적이 없으면 null (명세: data 가 null) */
    public InterestConditionResponse get(CurrentDevice currentDevice) {
        return interestConditionRepository.findByDeviceId(currentDevice.deviceId())
                .map(InterestConditionResponse::from)
                .orElse(null);
    }

    /** 16번. 없으면 만들고, 있으면 전체 값을 바꿔요 */
    @Transactional
    public InterestConditionResponse save(CurrentDevice currentDevice, InterestConditionRequest request) {
        validateRange("price", request.priceMin(), request.priceMax());
        validateRange("area", request.areaMinM2(), request.areaMaxM2());

        InterestCondition condition = interestConditionRepository.findByDeviceId(currentDevice.deviceId())
                .orElseGet(() -> InterestCondition.of(deviceRepository.getReferenceById(currentDevice.deviceId())));

        // 같은 값을 두 번 보내도 한 번만 저장 (순서는 보낸 그대로)
        condition.update(
                request.regionCodes().stream().distinct().toList(),
                request.types().stream().distinct().toList(),
                request.priceMin(),
                request.priceMax(),
                request.areaMinM2(),
                request.areaMaxM2(),
                request.alertEnabled());
        return InterestConditionResponse.from(interestConditionRepository.save(condition));
    }

    private static void validateRange(String name, Integer min, Integer max) {
        if (min != null && max != null && min > max) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    name + "Min 은 " + name + "Max 보다 클 수 없어요: " + min + " > " + max);
        }
    }
}
