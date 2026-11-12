package com.gooodhandletruck.zippick.domain.building.controller;

import com.gooodhandletruck.zippick.domain.building.dto.BuildingIdentifyResponse;
import com.gooodhandletruck.zippick.domain.building.service.BuildingIdentifyService;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앞쪽 건물 찾기 API (API 20번). (#50 에서 추가)
 * 주변 건물(#35) 처럼 파일을 따로 둬서 다른 건물 API 작업과 충돌을 줄였어요.
 * /identify 는 고정 경로라 /{buildingId} 보다 먼저 매칭돼요 (Spring 은 고정 경로를 변수 경로보다 우선).
 * 헤더 X-Anonymous-Id 는 명세상 필수 → 팀 공통 CurrentDevice 로 받아요 (없으면 D001, 모르는 ID 면 D002).
 */
@Tag(name = "4. 건물", description = "건물 상세 · 실거래 내역(더보기) · 가격 추이. 지금은 마포구 데이터만 있어요")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/buildings")
public class BuildingIdentifyController {

    // 에러 예시 JSON — 로컬 서버에서 실제로 호출해 받은 응답 형식 그대로
    private static final String BAD_INPUT_EXAMPLE =
            "{\"success\": false, \"code\": \"C001\", \"message\": \"heading 은 0~359 이어야 해요: 400.0\"}";
    private static final String NO_HEADER_EXAMPLE =
            "{\"success\": false, \"code\": \"D001\", \"message\": \"X-Anonymous-Id 헤더가 필요해요\"}";

    private final BuildingIdentifyService buildingIdentifyService;

    @Operation(summary = "앞쪽 건물 찾기", description = """
            주행 중 [주변 단지 듣기] 버튼(S-08)에서 불러요. 차 앞쪽(진행 방향 좌우 90°, 1000m 안) 건물을 가까운 순으로 최대 3개 줘요.
            후보 필드는 주변 건물 API 와 같고, side(AHEAD/RIGHT/LEFT)·direction(8방향)·saved·latestDeal.floor 가 더 있어요.
            speechText 는 첫 번째 후보 안내 문장이에요. 저장된 좌표만 보고 외부 API 는 부르지 않아요.
            지금은 마포구 데이터만 있어요 — 테스트 위치 예: 도화동 lat=37.5404, lon=126.9455, heading=0""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "성공 (앞쪽에 건물이 없으면 candidates 가 [], speechText 는 「주변에서 거래된 건물을 찾지 못했어요.」)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "C001 lat·lon·heading 이 없거나 범위 밖 (heading 0~359, 360 은 0 으로 봐요)",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "heading 범위 밖", value = BAD_INPUT_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "D001 X-Anonymous-Id 헤더 없음",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "헤더 없음", value = NO_HEADER_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "D002 등록되지 않은 기기")
    @GetMapping("/identify")
    public ApiResponse<BuildingIdentifyResponse> identify(
            @Parameter(hidden = true) CurrentDevice device,
            @Parameter(description = "차 위치 위도 (-90~90)", example = "37.5404") @RequestParam double lat,
            @Parameter(description = "차 위치 경도 (-180~180)", example = "126.9455") @RequestParam double lon,
            @Parameter(description = "진행 방향. 0 = 북쪽, 시계 방향으로 0~359", example = "0") @RequestParam double heading) {
        return ApiResponse.ok(buildingIdentifyService.identify(device, lat, lon, heading));
    }
}
