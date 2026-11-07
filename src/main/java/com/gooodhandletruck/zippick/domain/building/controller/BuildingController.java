package com.gooodhandletruck.zippick.domain.building.controller;

import com.gooodhandletruck.zippick.domain.building.dto.BuildingDetailResponse;
import com.gooodhandletruck.zippick.domain.building.service.BuildingService;
import com.gooodhandletruck.zippick.domain.trade.dto.PriceTrendResponse;
import com.gooodhandletruck.zippick.domain.trade.dto.TransactionListResponse;
import com.gooodhandletruck.zippick.global.config.ApiHeaders;
import com.gooodhandletruck.zippick.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
 * #26 변경: 가격 추이(API 13번) 추가.
 * #33 변경: Swagger 태그를 팀 형식(번호 + 한글)으로 바꾸고, 경로·쿼리 파라미터에 설명·예시를 붙임. 동작은 그대로.
 *   에러 응답(400 C001, 404 B001)도 API 마다 실제 응답 JSON 예시로 문서화
 *   (참고: swagger-core 공식 어노테이션 문서의 @ApiResponse + @Content + @ExampleObject 형식,
 *    https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations ).
 *   스웨거 어노테이션 ApiResponse 는 팀 공통 응답 클래스 ApiResponse 와 이름이 같아서 전체 이름으로 써요.
 */
// #33: 팀원 태그(0. Health, 1. 기기, 2. 지역, 3. 건물 좌표) 다음 번호로 맞춤
@Tag(name = "4. 건물", description = "건물 상세 · 실거래 내역(더보기) · 가격 추이. 지금은 마포구 데이터만 있어요")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/buildings")
public class BuildingController {

    /** #33: 세 API 가 같은 경로 변수를 써서 설명 한 곳에 모음. 예시 21 = 한화오벨리스크(마포 도화동, 거래 많음) */
    private static final String BUILDING_ID_DESC = "건물 ID. 테스트용 마포 건물: 21(아파트, 거래 많음) · 9 · 10 · 254(오피스텔)";

    // #33: 에러 예시 JSON — 로컬 서버에서 실제로 호출해 받은 응답 그대로 (C001 메시지는 잘못된 값에 따라 달라짐)
    private static final String NOT_FOUND_EXAMPLE =
            "{\"success\": false, \"code\": \"B001\", \"message\": \"건물을 찾을 수 없어요\"}";
    private static final String BAD_ID_EXAMPLE =
            "{\"success\": false, \"code\": \"C001\", \"message\": \"Method parameter 'buildingId': Failed to convert value of type 'java.lang.String' to required type 'java.lang.Long'; For input string: \\\"abc\\\"\"}";
    private static final String BAD_SIZE_EXAMPLE =
            "{\"success\": false, \"code\": \"C001\", \"message\": \"size 는 1~50 이어야 해요: 100\"}";

    private final BuildingService buildingService;

    @Operation(
            summary = "건물 상세",
            description = "건물 기본 정보와 최근 거래 최대 3건(해제 거래 제외). 없는 건물은 B001(404)")
    // #33: 에러 응답 예시. 응답 코드를 직접 적으면 200 이 자동으로 안 생겨서 200 도 적음 (content 는 반환 타입에서 자동)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 건물 ID 가 숫자가 아님",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "숫자 아닌 ID", value = BAD_ID_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "B001 없는 건물",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "없는 건물", value = NOT_FOUND_EXAMPLE)))
    @GetMapping("/{buildingId}")
    public ApiResponse<BuildingDetailResponse> getBuilding(
            @Parameter(description = BUILDING_ID_DESC, example = "21") @PathVariable Long buildingId, // #33 예시 추가
            @Parameter(description = "기기 등록 때 받은 익명 ID. 보내면 저장 여부(saved)를 알려 줘요 (저장 기능 연결 전까지 항상 false)")
            // 선택 헤더라 팀 공통 리졸버(없으면 D001)를 쓰지 않고 직접 받아요 — 안 보내도 상세는 보여야 해서
            @RequestHeader(value = ApiHeaders.ANONYMOUS_ID, required = false) String anonymousId) {
        return ApiResponse.ok(buildingService.getDetail(buildingId, anonymousId));
    }

    @Operation(
            summary = "실거래 내역 (더보기)",
            description = "해제 거래를 뺀 최신순 거래를 쪽 단위로. page·size 범위 오류는 C001(400), 없는 건물은 B001(404)")
    // #33: 에러 응답 예시. 응답 코드를 직접 적으면 200 이 자동으로 안 생겨서 200 도 적음 (content 는 반환 타입에서 자동)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 건물 ID 가 숫자가 아니거나 page·size 범위 밖 (page 0 이상, size 1~50)",
            content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(name = "size 범위 밖", value = BAD_SIZE_EXAMPLE),
                    @ExampleObject(name = "숫자 아닌 ID", value = BAD_ID_EXAMPLE)}))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "B001 없는 건물",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "없는 건물", value = NOT_FOUND_EXAMPLE)))
    @GetMapping("/{buildingId}/transactions")
    public ApiResponse<TransactionListResponse> getTransactions(
            @Parameter(description = BUILDING_ID_DESC, example = "21") @PathVariable Long buildingId, // #33 예시 추가
            @Parameter(description = "쪽 번호, 0부터. 다음 쪽은 응답의 hasNext 가 true 일 때만 불러요", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "쪽 크기, 1~50", example = "20") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(buildingService.getTransactions(buildingId, page, size));
    }

    @Operation(
            summary = "가격 추이",
            description = "이번 달 포함 최근 12개월 달별 평균 거래가(만원)·건수. 오래된 달부터 항상 12개, 거래 없는 달은 avgPrice=null. 해제 거래 제외. 없는 건물은 B001(404)")
    // #33: 에러 응답 예시. 응답 코드를 직접 적으면 200 이 자동으로 안 생겨서 200 도 적음 (content 는 반환 타입에서 자동)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 건물 ID 가 숫자가 아님",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "숫자 아닌 ID", value = BAD_ID_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "B001 없는 건물",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "없는 건물", value = NOT_FOUND_EXAMPLE)))
    @GetMapping("/{buildingId}/price-trend")
    public ApiResponse<PriceTrendResponse> getPriceTrend(
            @Parameter(description = BUILDING_ID_DESC, example = "21") @PathVariable Long buildingId) { // #33 예시 추가
        return ApiResponse.ok(buildingService.getPriceTrend(buildingId));
    }
}
