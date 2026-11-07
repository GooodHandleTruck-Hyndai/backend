package com.gooodhandletruck.zippick.domain.building.controller;

import com.gooodhandletruck.zippick.domain.building.dto.BuildingDetailResponse;
import com.gooodhandletruck.zippick.domain.building.service.BuildingService;
import com.gooodhandletruck.zippick.domain.trade.dto.TransactionListResponse;
import com.gooodhandletruck.zippick.global.config.ApiHeaders;
import com.gooodhandletruck.zippick.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 건물 API. (#6 에서 추가: 건물 상세)
 * 헤더는 팀 공통 X-Anonymous-Id(ApiHeaders) — 명세의 X-Device-Id 와 달랐던 것을 main 코드(#9 기기 등록)에 맞춤.
 * #16 변경: 실거래 내역(API 8번, 더보기) 추가.
 */
@Tag(name = "Building", description = "건물 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/buildings")
public class BuildingController {

    private final BuildingService buildingService;

    @Operation(
            summary = "건물 상세",
            description = "건물 기본 정보와 최근 거래 최대 3건(해제 거래 제외). 없는 건물은 B001(404)")
    @GetMapping("/{buildingId}")
    public ApiResponse<BuildingDetailResponse> getBuilding(
            @PathVariable Long buildingId,
            @Parameter(description = "기기 등록 때 받은 익명 ID. 보내면 저장 여부(saved)를 알려 줘요 (저장 기능 연결 전까지 항상 false)")
            // 선택 헤더라 팀 공통 리졸버(없으면 D001)를 쓰지 않고 직접 받아요 — 안 보내도 상세는 보여야 해서
            @RequestHeader(value = ApiHeaders.ANONYMOUS_ID, required = false) String anonymousId) {
        return ApiResponse.ok(buildingService.getDetail(buildingId, anonymousId));
    }

    @Operation(
            summary = "실거래 내역 (더보기)",
            description = "해제 거래를 뺀 최신순 거래를 쪽 단위로. page·size 범위 오류는 C001(400), 없는 건물은 B001(404)")
    @GetMapping("/{buildingId}/transactions")
    public ApiResponse<TransactionListResponse> getTransactions(
            @PathVariable Long buildingId,
            @Parameter(description = "쪽 번호, 0부터") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "쪽 크기, 1~50") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(buildingService.getTransactions(buildingId, page, size));
    }
}
