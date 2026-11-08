package com.gooodhandletruck.zippick.domain.interest.controller;

import com.gooodhandletruck.zippick.domain.interest.dto.InterestConditionRequest;
import com.gooodhandletruck.zippick.domain.interest.dto.InterestConditionResponse;
import com.gooodhandletruck.zippick.domain.interest.service.InterestConditionService;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "5. 관심 조건", description = "관심 조건과 알림 on/off")
@RestController
@RequestMapping("/api/v1/interest-conditions")
@RequiredArgsConstructor
public class InterestConditionController {

    private final InterestConditionService interestConditionService;

    @Operation(summary = "관심 조건 조회", description = """
            저장된 관심 조건과 알림 켜짐 여부.
            조건을 설정한 적이 없으면 data 가 없어요(null). 이때 알림은 나오지 않아요.""")
    @GetMapping
    public ApiResponse<InterestConditionResponse> get(@Parameter(hidden = true) CurrentDevice device) {
        return ApiResponse.ok(interestConditionService.get(device));
    }

    @Operation(summary = "관심 조건 저장", description = """
            전체 값을 보내면 그대로 바꿔요. 저장된 조건을 그대로 돌려줘요.
            가격은 만원, 면적은 ㎡. 범위의 한쪽이 없으면 null.
            에러: C001 입력값 오류 (최소 > 최대, 지역 코드 형식, 알 수 없는 유형 등)""")
    @PutMapping
    public ApiResponse<InterestConditionResponse> save(
            @Parameter(hidden = true) CurrentDevice device,
            @Valid @RequestBody InterestConditionRequest request) {
        return ApiResponse.ok(interestConditionService.save(device, request));
    }
}
