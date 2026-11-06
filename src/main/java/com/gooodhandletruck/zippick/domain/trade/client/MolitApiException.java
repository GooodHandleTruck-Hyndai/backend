package com.gooodhandletruck.zippick.domain.trade.client;

/**
 * 국토부 API 호출이 실패했을 때 (HTTP 오류, 인증키 오류, resultCode 가 000 이 아님, XML 을 못 읽음). (#7 에서 추가)
 *
 * 적재 배치(#8, 5·12번)가 이 예외를 받으면 그 달은 반영하지 않고 건너뛰어요.
 * 그래서 앱 응답용 BusinessException 이 아니라 서버 내부용 예외로 따로 둬요.
 */
public class MolitApiException extends RuntimeException {

    /** 국토부 결과 코드 (resultCode 또는 returnReasonCode). HTTP·파싱 실패면 null */
    private final String resultCode;

    public MolitApiException(String message, String resultCode) {
        super(message);
        this.resultCode = resultCode;
    }

    public MolitApiException(String message, Throwable cause) {
        super(message, cause);
        this.resultCode = null;
    }

    public String getResultCode() {
        return resultCode;
    }
}
