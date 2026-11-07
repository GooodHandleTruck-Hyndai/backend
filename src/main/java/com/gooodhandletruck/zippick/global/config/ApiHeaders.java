package com.gooodhandletruck.zippick.global.config;

/** API 공통 헤더 이름 */
public final class ApiHeaders {

    /** 기기 등록(POST /api/v1/devices) 때 받은 익명 ID */
    public static final String ANONYMOUS_ID = "X-Anonymous-Id";

    /** Swagger 의 Authorize 버튼에 표시되는 이름 */
    public static final String ANONYMOUS_ID_SCHEME = "AnonymousId";

    private ApiHeaders() {
    }
}
