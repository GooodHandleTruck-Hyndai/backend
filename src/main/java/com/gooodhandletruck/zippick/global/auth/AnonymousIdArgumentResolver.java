package com.gooodhandletruck.zippick.global.auth;

import com.gooodhandletruck.zippick.domain.device.entity.Device;
import com.gooodhandletruck.zippick.domain.device.repository.DeviceRepository;
import com.gooodhandletruck.zippick.global.config.ApiHeaders;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 파라미터에 CurrentDevice 가 있으면
 * X-Anonymous-Id 헤더로 기기를 찾아서 넣어줘요.
 *
 * - 헤더가 없으면 D001 (401)
 * - 등록되지 않은 ID 면 D002 (404)
 */
@Component
@RequiredArgsConstructor
public class AnonymousIdArgumentResolver implements HandlerMethodArgumentResolver {

    private final DeviceRepository deviceRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentDevice.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentDevice resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String anonymousId = webRequest.getHeader(ApiHeaders.ANONYMOUS_ID);
        if (!StringUtils.hasText(anonymousId)) {
            throw new BusinessException(ErrorCode.MISSING_ANONYMOUS_ID);
        }

        Device device = deviceRepository.findByAnonymousId(anonymousId.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

        return new CurrentDevice(device.getId(), device.getAnonymousId());
    }
}
