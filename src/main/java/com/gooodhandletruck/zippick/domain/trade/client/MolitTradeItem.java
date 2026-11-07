package com.gooodhandletruck.zippick.domain.trade.client;

/**
 * 국토부 응답의 거래 한 건(item) — 받은 문자열 그대로예요. (#7 에서 추가)
 *
 * 값 정리(공백 제거, 쉼표 제거, 날짜 변환)는 적재 서비스(#8)가 해요.
 * 지문(거래 고유 키) 규칙이 한 곳에만 있어야 나중에 규칙이 어긋나지 않아서, 여기서는 일부러 손대지 않아요.
 * 빈 값은 국토부가 공백 한 칸(" ")으로 보내고, 태그가 없으면 null 이에요.
 */
public record MolitTradeItem(
        String sggCd,
        String umdNm,
        String jibun,
        String aptNm,
        String aptDong,
        String buildYear,
        String dealYear,
        String dealMonth,
        String dealDay,
        String dealAmount,
        String excluUseAr,
        String floor,
        String cdealType,
        String cdealDay,
        String rgstDate,
        String dealingGbn,
        String estateAgentSggNm,
        String slerGbn,
        String buyerGbn,
        String landLeaseholdGbn) {
}
