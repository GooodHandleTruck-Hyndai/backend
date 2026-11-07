package com.gooodhandletruck.zippick.domain.device.controller;

import com.gooodhandletruck.zippick.domain.device.dto.DeviceRegisterRequest;
import com.gooodhandletruck.zippick.domain.device.dto.DeviceResponse;
import com.gooodhandletruck.zippick.domain.device.service.DeviceService;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1. 기기", description = "익명 ID 발급과 헤더 확인")
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "기기 등록", description = """
            앱 첫 실행 때 한 번 호출. 받은 anonymousId 를 앱에 저장하고,
            이후 모든 요청에 X-Anonymous-Id 헤더로 보내요. 헤더 없이 호출해요.""")
    @SecurityRequirements
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeviceResponse> register(
            @Valid @RequestBody(required = false) DeviceRegisterRequest request) {
        return ApiResponse.ok(deviceService.register(request));
    }

    @Operation(summary = "내 기기 확인", description = """
            헤더가 제대로 붙는지 확인하는 용도예요.
            에러: D001 헤더 없음, D002 등록되지 않은 기기""")
    @GetMapping("/me")
    public ApiResponse<DeviceResponse> getMyDevice(@Parameter(hidden = true) CurrentDevice device) {
        return ApiResponse.ok(deviceService.getMyDevice(device));
    }
}
