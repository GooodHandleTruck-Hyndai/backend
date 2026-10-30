package com.gooodhandletruck.sidong.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 모음.
 *
 * 규칙: 공통은 C, 도메인별로 앞 글자를 정해서 번호를 붙여요.
 * 예) 운전자 D001, 주행 기록 T001 ...
 * 새 도메인을 만들면 여기에 섹션을 추가해 주세요.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않아요"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "C002", "요청한 리소스를 찾을 수 없어요"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C003", "지원하지 않는 HTTP 메서드예요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 내부 오류가 발생했어요");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
