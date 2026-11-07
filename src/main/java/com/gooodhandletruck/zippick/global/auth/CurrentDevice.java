package com.gooodhandletruck.zippick.global.auth;

/**
 * 요청을 보낸 기기. X-Anonymous-Id 헤더로 찾은 결과예요.
 *
 * <pre>
 * &#64;GetMapping("/api/v1/favorites")
 * public ApiResponse&lt;...&gt; getFavorites(&#64;Parameter(hidden = true) CurrentDevice device) {
 *     return ApiResponse.ok(favoriteService.getFavorites(device.deviceId()));
 * }
 * </pre>
 *
 * 컨트롤러 파라미터에 이 타입을 쓰기만 하면 헤더 검사(D001)와
 * 등록 여부 검사(D002)가 자동으로 끝난 상태로 들어와요.
 *
 * @param deviceId    DB 의 기기 PK. 다른 테이블에서 외래키로 써요
 * @param anonymousId 앱이 보낸 익명 ID
 */
public record CurrentDevice(Long deviceId, String anonymousId) {
}
