package com.gooodhandletruck.zippick.domain.building.controller;

import com.gooodhandletruck.zippick.domain.building.dto.NearbyBuildingsResponse;
import com.gooodhandletruck.zippick.domain.building.service.NearbyBuildingService;
import com.gooodhandletruck.zippick.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주변 건물 API (API 2번). (#35 에서 추가)
 * 건물 상세 컨트롤러와 같은 /api/v1/buildings 아래지만 파일을 나눠서, 열려 있는 Swagger 정리 PR(#34)과 충돌하지 않게 했어요.
 * /nearby 는 고정 경로라 /{buildingId} 보다 먼저 매칭돼요 (Spring 은 고정 경로를 변수 경로보다 우선).
 * 스웨거 어노테이션 ApiResponse 는 팀 공통 응답 클래스 ApiResponse 와 이름이 같아서 전체 이름으로 써요.
 */
@Tag(name = "4. 건물", description = "건물 상세 · 실거래 내역(더보기) · 가격 추이. 지금은 마포구 데이터만 있어요")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/buildings")
public class BuildingNearbyController {

    // 에러 예시 JSON — 로컬 서버에서 실제로 호출해 받은 응답 형식 그대로
    private static final String BAD_INPUT_EXAMPLE =
            "{\"success\": false, \"code\": \"C001\", \"message\": \"radius 는 100~3000 이어야 해요: 5000\"}";
    private static final String EXTERNAL_EXAMPLE =
            "{\"success\": false, \"code\": \"E001\", \"message\": \"외부 서비스 호출에 실패했어요\"}";

    private final NearbyBuildingService nearbyBuildingService;

    @Operation(summary = "주변 건물", description = """
            차 위치에서 반경 안의 실거래 건물을 가까운 순으로 보여 줘요. 홈을 열 때와 새로고침할 때 한 번 호출해요.
            건물마다 최근 1년 거래 1건(latestDeal, 해제 거래 제외)이 같이 와요. 헤더는 필요 없어요.
            지금은 마포구 데이터만 있어요 — 테스트 위치 예: 도화동 lat=37.5404, lon=126.9455""")
    @SecurityRequirements // 명세: 헤더 필요 없음 → Swagger 의 Authorize 헤더를 이 API 에서는 빼요 (팀원 기기 등록과 같은 방식)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공 (주변에 건물이 없으면 buildings 가 [])")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "C001 lat·lon 이 없거나 범위 밖, radius 가 100~3000 밖",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "radius 범위 밖", value = BAD_INPUT_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502",
            description = "E001 좌표 조회(카카오) 실패 → 마지막으로 받은 목록 + 「최신 정보가 아닐 수 있어요」",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "외부 호출 실패", value = EXTERNAL_EXAMPLE)))
    @GetMapping("/nearby")
    public ApiResponse<NearbyBuildingsResponse> getNearby(
            @Parameter(description = "차 위치 위도 (-90~90)", example = "37.5404") @RequestParam double lat,
            @Parameter(description = "차 위치 경도 (-180~180)", example = "126.9455") @RequestParam double lon,
            @Parameter(description = "반경(m). 기본 1000, 100~3000", example = "1000")
            @RequestParam(required = false) Integer radius) {
        return ApiResponse.ok(nearbyBuildingService.findNearby(lat, lon, radius));
    }
}
