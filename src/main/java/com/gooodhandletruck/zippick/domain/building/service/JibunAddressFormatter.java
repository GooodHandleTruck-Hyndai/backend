package com.gooodhandletruck.zippick.domain.building.service;

import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 지번 주소 문자열 만들기 (예: 「서울 마포구 상암동 1601」). (#6 에서 추가)
 * API 명세 예시처럼 시도 이름은 줄여서 써요. 법정동코드표에 그 구가 없으면 「상암동 1601」 처럼 동·지번만 써요.
 */
final class JibunAddressFormatter {

    /** 법정동코드표의 정식 시도 이름 → 짧은 이름. 목록에 없으면 정식 이름 그대로 */
    private static final Map<String, String> SHORT_SIDO = Map.ofEntries(
            Map.entry("서울특별시", "서울"),
            Map.entry("부산광역시", "부산"),
            Map.entry("대구광역시", "대구"),
            Map.entry("인천광역시", "인천"),
            Map.entry("광주광역시", "광주"),
            Map.entry("대전광역시", "대전"),
            Map.entry("울산광역시", "울산"),
            Map.entry("세종특별자치시", "세종"),
            Map.entry("경기도", "경기"),
            Map.entry("강원도", "강원"),
            Map.entry("강원특별자치도", "강원"),
            Map.entry("충청북도", "충북"),
            Map.entry("충청남도", "충남"),
            Map.entry("전라북도", "전북"),
            Map.entry("전북특별자치도", "전북"),
            Map.entry("전라남도", "전남"),
            Map.entry("경상북도", "경북"),
            Map.entry("경상남도", "경남"),
            Map.entry("제주특별자치도", "제주"));

    private JibunAddressFormatter() {
    }

    static String format(LegalDong region, String umdNm, String jibun) {
        List<String> parts = new ArrayList<>();
        if (region != null) {
            parts.add(SHORT_SIDO.getOrDefault(region.getSidoNm(), region.getSidoNm()));
            parts.add(region.getSggNm());
        }
        parts.add(umdNm);
        // 국토부 지번은 빈 값으로 올 수 있어서(옵션 항목), 비어 있으면 동까지만 써요
        if (jibun != null && !jibun.isBlank()) {
            parts.add(jibun.trim());
        }
        return String.join(" ", parts);
    }
}
