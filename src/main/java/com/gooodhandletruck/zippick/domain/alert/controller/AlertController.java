package com.gooodhandletruck.zippick.domain.alert.controller;

import com.gooodhandletruck.zippick.domain.alert.dto.AlertListResponse;
import com.gooodhandletruck.zippick.domain.alert.dto.AlertReadResponse;
import com.gooodhandletruck.zippick.domain.alert.service.AlertService;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "6. 알림", description = "관심 건물 알림 목록 / 읽음")
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @Operation(summary = "관심 건물 알림 목록", description = """
            since 이후 알림만, 최신순 (최대 100개). since 가 없으면 최근 7일.
            서버 배치가 새 거래를 찾아 알림을 만들고, 앱이 이 API 로 가져가요.
            URL 에 since 를 넣을 때 + 는 %2B 로 인코딩해요 (안 해도 서버가 처리하긴 해요).
            에러: C001 since 형식 오류""")
    @GetMapping
    public ApiResponse<AlertListResponse> getAlerts(
            @Parameter(hidden = true) CurrentDevice device,
            @Parameter(description = "이 시각 이후 알림만", example = "2026-11-03T09:00:00+09:00")
            @RequestParam(required = false) String since) {
        return ApiResponse.ok(alertService.getAlerts(device, since));
    }

    @Operation(summary = "알림 읽음", description = """
            이미 읽은 알림에 다시 보내도 에러 없이 처음 읽은 시각을 돌려줘요.
            에러: N001 내 알림이 아니거나 없는 알림 (404)""")
    @PatchMapping("/{alertId}/read")
    public ApiResponse<AlertReadResponse> markRead(
            @Parameter(hidden = true) CurrentDevice device,
            @Parameter(description = "알림 ID", example = "42") @PathVariable Long alertId) {
        return ApiResponse.ok(alertService.markRead(device, alertId));
    }
}
