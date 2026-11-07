package com.gooodhandletruck.zippick.domain.building.entity;

/**
 * 건물 좌표를 어디서 받았는지. (#4 에서 추가)
 * 좌표 API 가 아직 확정 전이라(행안부 기본, 카카오 예비) 출처를 같이 남겨 둬요.
 */
public enum CoordSource {
    /** 행안부 주소정보 좌표제공 API */
    JUSO,
    /** 카카오 로컬 API (예비) */
    KAKAO,
    /** 주소 매칭에 실패해서 좌표가 없음 */
    NONE
}
