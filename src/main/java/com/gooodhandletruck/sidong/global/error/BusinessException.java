package com.gooodhandletruck.sidong.global.error;

import lombok.Getter;

/**
 * 서비스 로직에서 의도적으로 던지는 예외.
 *
 * <pre>
 * throw new BusinessException(ErrorCode.NOT_FOUND);
 * </pre>
 *
 * GlobalExceptionHandler 가 받아서 ErrorCode 에 맞는 상태 코드와 응답으로 바꿔줘요.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
