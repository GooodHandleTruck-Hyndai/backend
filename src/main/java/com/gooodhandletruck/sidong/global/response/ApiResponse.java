package com.gooodhandletruck.sidong.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gooodhandletruck.sidong.global.error.ErrorCode;

/**
 * 모든 API 의 공통 응답 형식.
 *
 * <pre>
 * 성공: { "success": true,  "code": "OK",   "message": "요청 성공", "data": { ... } }
 * 실패: { "success": false, "code": "C001", "message": "입력값이 올바르지 않아요" }
 * </pre>
 *
 * 컨트롤러에서는 {@code return ApiResponse.ok(data);} 처럼 써요.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "요청 성공", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, "OK", "요청 성공", null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null);
    }
}
