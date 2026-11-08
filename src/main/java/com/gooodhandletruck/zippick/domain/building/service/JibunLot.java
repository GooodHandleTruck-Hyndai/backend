package com.gooodhandletruck.zippick.domain.building.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 국토부 지번 문자열 → 산 여부·본번·부번. 예) "777", "736-1", "산12-3"
 *
 * @param mountain 산 여부
 * @param main     본번
 * @param sub      부번 (없으면 0)
 */
record JibunLot(boolean mountain, int main, int sub) {

    private static final Pattern LOT = Pattern.compile("^(산)?\\s*(\\d+)(?:-(\\d+))?$");

    static Optional<JibunLot> parse(String jibun) {
        if (jibun == null) {
            return Optional.empty();
        }
        Matcher matcher = LOT.matcher(jibun.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int sub = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        return Optional.of(new JibunLot(matcher.group(1) != null, Integer.parseInt(matcher.group(2)), sub));
    }

    /** 행안부 검색 결과(mtYn, lnbrMnnm, lnbrSlno)와 같은 지번인지 */
    boolean matches(String mtYn, String lnbrMnnm, String lnbrSlno) {
        try {
            boolean resultMountain = "1".equals(mtYn);
            int resultMain = Integer.parseInt(lnbrMnnm.trim());
            int resultSub = lnbrSlno == null || lnbrSlno.isBlank() ? 0 : Integer.parseInt(lnbrSlno.trim());
            return mountain == resultMountain && main == resultMain && sub == resultSub;
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }
}
