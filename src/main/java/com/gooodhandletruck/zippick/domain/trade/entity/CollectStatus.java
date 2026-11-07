package com.gooodhandletruck.zippick.domain.trade.entity;

/**
 * 수집 한 회차의 결과. (#8 에서 추가)
 */
public enum CollectStatus {
    /** 진행 중 (저장 전에만 잠깐 쓰여요) */
    RUNNING,
    /** DB 에 반영함 */
    SUCCESS,
    /** 받은 건수가 totalCount 와 달라서 반영하지 않음 — 다음 회차에 다시 받아요 */
    SKIPPED,
    /** 국토부 호출 실패, 이상한 값, DB 오류 — 반영하지 않음(그 달 전체 롤백) */
    FAILED
}
