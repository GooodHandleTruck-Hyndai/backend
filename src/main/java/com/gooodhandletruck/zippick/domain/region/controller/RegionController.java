package com.gooodhandletruck.zippick.domain.region.controller;

import com.gooodhandletruck.zippick.domain.region.dto.RegionResponse;
import com.gooodhandletruck.zippick.domain.region.service.RegionService;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "2. 지역", description = "위치로 시군구 코드 조회")
@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    /** device 는 직접 안 쓰지만, 등록된 기기만 카카오 호출하도록 헤더 검사용으로 받음 */
    @Operation(summary = "현재 위치의 시군구 코드", description = """
            좌표로 시군구 코드(LAWD_CD)를 조회해요. 실거래가 조회에 이 코드를 써요.
            에러: C001 좌표 범위 밖, R001 지역 정보 없음(바다 등), E001/E002 카카오 호출 실패""")
    @GetMapping
    public ApiResponse<RegionResponse> getRegion(
        @Parameter(hidden = true) CurrentDevice device,

        @Parameter(description = "위도", example = "37.5013")
        @RequestParam
        @DecimalMin(value = "33.0", message = "위도는 33~39 사이여야 해요")
        @DecimalMax(value = "39.0", message = "위도는 33~39 사이여야 해요")
        double lat,

        @Parameter(description = "경도", example = "127.0396")
        @RequestParam
        @DecimalMin(value = "124.0", message = "경도는 124~132 사이여야 해요")
        @DecimalMax(value = "132.0", message = "경도는 124~132 사이여야 해요")
        double lon) {
        return ApiResponse.ok(regionService.findByCoordinate(lat, lon));
    }
}
