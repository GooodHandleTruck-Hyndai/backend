package com.gooodhandletruck.zippick.global.external.kakao;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 카카오 키 설정 여부 (#55 에서 추가)
 *
 * - 로컬 e2e(심사위원이 Docker 로 실행)에는 카카오 키 없음
 * - 키 없이 호출하면 매번 401 실패, 주변 건물은 실패 건물을 1초 쉬고 재시도 → 요청마다 1초 이상 지연
 * - 키 없으면 카카오 호출 생략, 카카오가 필요한 곳은 DB 로 대체 (지역 찾기·좌표 없는 건물)
 * - 키 있으면 기존 동작 그대로
 */
@Component
public class KakaoAvailability {

    private final boolean enabled;

    public KakaoAvailability(KakaoProperties properties) {
        this.enabled = StringUtils.hasText(properties.restApiKey());
    }

    /** 카카오 키 있으면 true */
    public boolean enabled() {
        return enabled;
    }
}
