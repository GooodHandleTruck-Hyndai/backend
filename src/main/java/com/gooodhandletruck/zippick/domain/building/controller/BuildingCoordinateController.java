package com.gooodhandletruck.zippick.domain.building.controller;

import com.gooodhandletruck.zippick.domain.building.dto.BuildingCoordinateRequest;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingCoordinateResponse;
import com.gooodhandletruck.zippick.domain.building.service.BuildingCoordinateService;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "3. 건물 좌표", description = "실거래가 건물의 지도 좌표")
@RestController
@RequestMapping("/api/v1/buildings")
@RequiredArgsConstructor
public class BuildingCoordinateController {

    private final BuildingCoordinateService buildingCoordinateService;

    /** device 는 직접 안 쓰지만, 등록된 기기만 카카오 호출하도록 헤더 검사용으로 받음 */
    @Operation(summary = "건물 좌표 조회", description = """
            실거래가 API 의 건물 필드로 지도 좌표를 찾아요. 한 번에 30개까지.
            도로명 주소로 먼저 찾고, 없으면 지번 주소로 찾아요.
            못 찾은 건물은 found = false 로 와요 (요청 전체가 실패하지 않음).""")
    @PostMapping("/coordinates")
    public ApiResponse<List<BuildingCoordinateResponse>> findCoordinates(
        @Parameter(hidden = true) CurrentDevice device,
        @Valid @RequestBody BuildingCoordinateRequest request) {
        return ApiResponse.ok(buildingCoordinateService.findCoordinates(request.buildings()));
    }
}
