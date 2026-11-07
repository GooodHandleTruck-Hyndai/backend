package com.gooodhandletruck.zippick.domain.trade.service;

import com.gooodhandletruck.zippick.domain.trade.client.MolitTradeItem;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 국토부 원본 문자열 → 정리된 거래 + 지문·순번. 지문 규칙은 이 클래스 한 곳에만 있어요. (#8 에서 추가)
 *
 * 규칙 (노션 「집픽 백엔드 개발 분담」 실거래 적재 규칙, ERD trades.fingerprint 주석):
 * - 정리: 앞뒤 공백 제거, 빈 값(국토부는 공백 한 칸)은 null, 금액 쉼표 제거, 날짜 변환
 * - 지문: 바뀌지 않는 8칸(sggCd, umdNm, jibun, aptNm, 계약일, 면적, 층, 금액)의 정리값 → SHA-256
 * - 순번: 같은 지문이 한 달 안에 여러 번 오면 응답 순서대로 1, 2, ...
 * 한 건이라도 형식이 이상하면 IllegalArgumentException — 그 달은 통째로 반영하지 않아요 (부분 반영하면 비교·삭제가 틀어짐).
 */
public final class MolitTradeNormalizer {

    /** 칸 구분자. 값 안에 나올 일 없는 제어 문자라 "1|23" + "4" 와 "1" + "23|4" 가 섞이지 않아요 */
    private static final String SEP = "\u001F";
    /** 해제일·등기일은 "26.10.07" 처럼 와요 (uu = 2000년대 두 자리) */
    private static final DateTimeFormatter SHORT_DATE =
            DateTimeFormatter.ofPattern("uu.MM.dd").withResolverStyle(ResolverStyle.STRICT);
    private static final int AREA_MAX_SCALE = 4;    // trades.exclu_use_ar DECIMAL(9,4)
    private static final int AREA_MAX_INT_DIGITS = 5;

    private MolitTradeNormalizer() {
    }

    /**
     * @param sggCd  요청한 구 코드 — 응답의 구 코드가 다르면 실패
     * @param dealYm 요청한 계약월 — 응답의 계약 연·월이 다르면 실패 (다른 달을 지워 버리는 사고 방지)
     */
    public static List<NormalizedTrade> normalize(String sggCd, String dealYm, List<MolitTradeItem> items) {
        List<NormalizedTrade> result = new ArrayList<>(items.size());
        Map<String, Integer> seqByFingerprint = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            try {
                Row row = clean(sggCd, dealYm, items.get(i));
                String fingerprint = fingerprint(row);
                int seq = seqByFingerprint.merge(fingerprint, 1, Integer::sum);
                result.add(row.toNormalized(fingerprint, seq));
            } catch (IllegalArgumentException | DateTimeException e) {
                // 몇 번째 거래가 문제인지 남겨야 국토부 데이터 변화를 찾아갈 수 있어요
                throw new IllegalArgumentException((i + 1) + "번째 거래 값이 이상해요: " + e.getMessage(), e);
            }
        }
        return result;
    }

    private static Row clean(String sggCd, String dealYm, MolitTradeItem item) {
        String itemSggCd = required("sggCd", item.sggCd());
        if (!itemSggCd.equals(sggCd)) {
            throw new IllegalArgumentException("요청한 구(" + sggCd + ")와 다른 구 코드: " + itemSggCd);
        }
        int year = parseInt("dealYear", required("dealYear", item.dealYear()));
        int month = parseInt("dealMonth", required("dealMonth", item.dealMonth()));
        int day = parseInt("dealDay", required("dealDay", item.dealDay()));
        LocalDate dealDate = LocalDate.of(year, month, day);
        String itemDealYm = String.format("%04d%02d", year, month);
        if (!itemDealYm.equals(dealYm)) {
            throw new IllegalArgumentException("요청한 계약월(" + dealYm + ")과 다른 계약월: " + itemDealYm);
        }

        String cdealType = trimOrNull(item.cdealType());
        if (cdealType != null && !cdealType.equals("O")) {
            throw new IllegalArgumentException("해제 여부는 O 또는 빈 값이어야 해요: " + cdealType);
        }

        String buildYear = trimOrNull(item.buildYear());
        String jibun = trimOrNull(item.jibun());
        return new Row(
                itemSggCd,
                required("umdNm", item.umdNm()),
                jibun == null ? "" : jibun, // 건물 구분 키라 NULL 대신 "" (Building 과 같은 규칙)
                required("aptNm", item.aptNm()),
                buildYear == null ? null : parseInt("buildYear", buildYear),
                dealYm,
                dealDate,
                parseAmount(required("dealAmount", item.dealAmount())),
                parseArea(required("excluUseAr", item.excluUseAr())),
                parseInt("floor", required("floor", item.floor())),
                trimOrNull(item.aptDong()),
                cdealType,
                parseShortDate(item.cdealDay()),
                parseShortDate(item.rgstDate()),
                trimOrNull(item.dealingGbn()),
                trimOrNull(item.estateAgentSggNm()),
                trimOrNull(item.slerGbn()),
                trimOrNull(item.buyerGbn()),
                trimOrNull(item.landLeaseholdGbn()));
    }

    static String fingerprint(Row row) {
        String source = String.join(SEP,
                row.sggCd, row.umdNm, row.jibun, row.aptNm,
                row.dealDate.toString(),
                // "84.90" 과 "84.9" 가 같은 지문이 되게 끝의 0 을 지우고, 지수 표기(8E+1) 없이 써요
                row.excluUseAr.stripTrailingZeros().toPlainString(),
                Integer.toString(row.floor),
                Integer.toString(row.dealAmount));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없어요", e); // 모든 JDK 에 있어야 하는 알고리즘
        }
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip(); // strip: 일반 공백 외 전각 공백 등도 지워요
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String required(String name, String value) {
        String trimmed = trimOrNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(name + " 가 비어 있어요");
        }
        return trimmed;
    }

    private static int parseInt(String name, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " 가 정수가 아니에요: " + value);
        }
    }

    /** "    139,000" → 139000 (만원) */
    private static int parseAmount(String value) {
        int amount = parseInt("dealAmount", value.replace(",", ""));
        if (amount <= 0) {
            throw new IllegalArgumentException("거래금액은 0보다 커야 해요: " + value);
        }
        return amount;
    }

    private static BigDecimal parseArea(String value) {
        BigDecimal area;
        try {
            area = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("전용면적이 숫자가 아니에요: " + value);
        }
        BigDecimal stripped = area.stripTrailingZeros();
        int intDigits = stripped.precision() - stripped.scale();
        if (area.signum() <= 0 || stripped.scale() > AREA_MAX_SCALE || intDigits > AREA_MAX_INT_DIGITS) {
            // DB 에 넣을 때 반올림·잘림이 생기면 지문과 저장 값이 어긋나서 미리 막아요
            throw new IllegalArgumentException("전용면적이 저장 범위(DECIMAL(9,4), 0 초과)를 벗어나요: " + value);
        }
        return area;
    }

    private static LocalDate parseShortDate(String value) {
        String trimmed = trimOrNull(value);
        return trimmed == null ? null : LocalDate.parse(trimmed, SHORT_DATE);
    }

    /** 정리만 끝난 한 줄 (지문·순번 붙이기 전) */
    record Row(String sggCd, String umdNm, String jibun, String aptNm, Integer buildYear, String dealYm,
            LocalDate dealDate, int dealAmount, BigDecimal excluUseAr, int floor, String aptDong,
            String cdealType, LocalDate cdealDay, LocalDate rgstDate, String dealingGbn,
            String estateAgentSggNm, String slerGbn, String buyerGbn, String landLeaseholdGbn) {

        NormalizedTrade toNormalized(String fingerprint, int seq) {
            return new NormalizedTrade(sggCd, umdNm, jibun, aptNm, buildYear, fingerprint, seq, dealYm,
                    dealDate, dealAmount, excluUseAr, floor, aptDong, cdealType, cdealDay, rgstDate,
                    dealingGbn, estateAgentSggNm, slerGbn, buyerGbn, landLeaseholdGbn);
        }
    }
}
