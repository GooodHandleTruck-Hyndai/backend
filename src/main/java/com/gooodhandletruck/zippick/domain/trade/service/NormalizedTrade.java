package com.gooodhandletruck.zippick.domain.trade.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 값 정리를 마친 거래 한 건 + 거래 고유 키(지문, 순번). DB 에 넣기 직전 모양이에요. (#8 에서 추가)
 *
 * 빈 값은 모두 null (단, 지번은 건물 구분 키라 "" 로 통일).
 */
public record NormalizedTrade(
        // 건물 구분 키
        String sggCd,
        String umdNm,
        String jibun,
        String aptNm,
        Integer buildYear,
        // 거래 고유 키
        String fingerprint,
        int seq,
        // 지문에 들어가는(바뀌지 않는) 값
        String dealYm,
        LocalDate dealDate,
        int dealAmount,
        BigDecimal excluUseAr,
        int floor,
        // 나중에 바뀌는 값 (재수집 때 이 4칸만 수정)
        String aptDong,
        String cdealType,
        LocalDate cdealDay,
        LocalDate rgstDate,
        // 참고용
        String dealingGbn,
        String estateAgentSggNm,
        String slerGbn,
        String buyerGbn,
        String landLeaseholdGbn) {

    /** fingerprint + seq — 한 달 안에서 거래를 맞춰 볼 때 써요 */
    public String tradeKey() {
        return fingerprint + "#" + seq;
    }
}
