package com.gooodhandletruck.zippick.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 모음.
 *
 * 규칙: 공통은 C, 도메인별로 앞 글자를 정해서 번호를 붙여요.
 * 예) 기기 D001, 저장 F001 ...
 * 새 도메인을 만들면 여기에 섹션을 추가해 주세요.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않아요"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "C002", "요청한 리소스를 찾을 수 없어요"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C003", "지원하지 않는 HTTP 메서드예요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 내부 오류가 발생했어요"),

    // 기기 (D)
    MISSING_ANONYMOUS_ID(HttpStatus.UNAUTHORIZED, "D001", "X-Anonymous-Id 헤더가 필요해요"),
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "D002", "등록되지 않은 기기예요"),

    // 지역 (R)
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "해당 위치의 지역 정보를 찾을 수 없어요"),

    // 건물 (B, #6 에서 추가)
    BUILDING_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "건물을 찾을 수 없어요"),

    // 외부 API (E)
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "E001", "외부 서비스 호출에 실패했어요"),
    EXTERNAL_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "E002", "외부 서비스 응답이 늦어요. 잠시 후 다시 시도해 주세요"),

    // 저장 (F)
    FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "F001", "이미 저장한 건물이에요"),
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "F002", "저장하지 않은 건물이에요"),

    // 알림 (N, #18 에서 추가)
    ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없어요");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
