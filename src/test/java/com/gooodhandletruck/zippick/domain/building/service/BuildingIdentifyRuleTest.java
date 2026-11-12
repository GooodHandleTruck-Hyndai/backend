package com.gooodhandletruck.zippick.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.gooodhandletruck.zippick.domain.building.dto.BuildingIdentifyResponse.Candidate;
import com.gooodhandletruck.zippick.domain.building.dto.BuildingIdentifyResponse.LatestDeal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 앞쪽 건물 찾기(API 20번) 계산 규칙이 프론트(frontend PR #8)와 같은지 확인해요. (#50 에서 추가)
 *
 * 프론트 코드를 그대로 옮긴 "프론트 기준" 함수(FRONT_*)와 서버 함수를 아주 많은 각도·좌표로 비교해요.
 * 옮긴 곳: Geo.kt(bearingDegrees), FakeRepository.kt(isAhead), RelativeDirection.kt(of), PriceFormat.kt(formatPriceVoice).
 * DB·스프링 없이 도는 순수 계산 테스트예요.
 */
class BuildingIdentifyRuleTest {

    // ---- 프론트 코드를 Java 로 그대로 옮긴 기준 (바꾸지 말 것: 프론트와 비교하는 정답) ----

    private static double frontBearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private static boolean frontIsAhead(double bearing, double heading) {
        double relative = ((bearing - heading) % 360.0 + 360.0) % 360.0;
        return relative <= 90.0 || relative >= 360.0 - 90.0;
    }

    private static final String[] FRONT_ENTRIES =
            {"FRONT", "FRONT_RIGHT", "RIGHT", "BACK_RIGHT", "BACK", "BACK_LEFT", "LEFT", "FRONT_LEFT"};

    private static String frontDirection(double bearing, double heading) {
        double relative = ((bearing - heading) % 360.0 + 360.0) % 360.0;
        int index = (int) ((relative + 45.0 / 2) / 45.0) % FRONT_ENTRIES.length;
        return FRONT_ENTRIES[index];
    }

    // ---- 방위각 ----

    @ParameterizedTest(name = "{0} 쪽 → {3}°")
    @CsvSource({
            "북, 37.5500, 126.9450, 0",
            "동, 37.5400, 126.9550, 90",
            "남, 37.5300, 126.9450, 180",
            "서, 37.5400, 126.9350, 270"})
    void 방위각은_북_0_시계방향(String name, double lat2, double lon2, double expected) {
        double bearing = BuildingIdentifyService.bearingDegrees(37.5400, 126.9450, lat2, lon2);
        // 동·서는 대권 초기 방위라 90°/270° 에서 아주 조금(0.01° 미만) 벗어나요
        assertThat(bearing).isCloseTo(expected, within(0.01));
    }

    @Test
    void 방위각은_마포_둘레_무작위_좌표_2만개에서_프론트와_완전히_같다() {
        Random random = new Random(20);
        for (int i = 0; i < 20_000; i++) {
            double lat1 = 37.53 + random.nextDouble() * 0.03;
            double lon1 = 126.89 + random.nextDouble() * 0.07;
            double lat2 = lat1 + (random.nextDouble() - 0.5) * 0.02;
            double lon2 = lon1 + (random.nextDouble() - 0.5) * 0.02;
            assertThat(BuildingIdentifyService.bearingDegrees(lat1, lon1, lat2, lon2))
                    .isEqualTo(frontBearing(lat1, lon1, lat2, lon2));
        }
    }

    @Test
    void 방위각은_항상_0_이상_360_미만() {
        Random random = new Random(7);
        for (int i = 0; i < 5_000; i++) {
            double b = BuildingIdentifyService.bearingDegrees(37.54, 126.94,
                    37.54 + (random.nextDouble() - 0.5) * 0.02, 126.94 + (random.nextDouble() - 0.5) * 0.02);
            assertThat(b).isGreaterThanOrEqualTo(0).isLessThan(360);
        }
    }

    // ---- 앞쪽 판정 (좌우 90°) ----

    @ParameterizedTest(name = "상대 {0}° → 앞쪽 {1}")
    @CsvSource({
            "0, true", "45, true", "89.999, true", "90, true", "90.001, false",
            "135, false", "180, false", "225, false",
            "269.999, false", "270, true", "270.001, true", "315, true", "359.999, true"})
    void 진행_방향_좌우_90도_경계를_포함한다(double relative, boolean expected) {
        assertThat(BuildingIdentifyService.isAhead(relative)).isEqualTo(expected);
    }

    @Test
    void 앞쪽_판정은_heading_과_방위각_0_1도_간격_전부에서_프론트와_같다() {
        // heading 0~359(1° 간격) × 방위각 0~359.9(0.1° 간격) = 129만 6천 가지. 다른 경우를 모아서 한 번에 확인
        List<String> mismatches = new java.util.ArrayList<>();
        for (int h = 0; h < 360; h++) {
            for (int b10 = 0; b10 < 3600; b10++) {
                double bearing = b10 / 10.0;
                double relative = BuildingIdentifyService.relative(bearing, h);
                if (BuildingIdentifyService.isAhead(relative) != frontIsAhead(bearing, h)) {
                    mismatches.add("heading=" + h + " bearing=" + bearing);
                }
            }
        }
        assertThat(mismatches).isEmpty();
    }

    @Test
    void 북쪽을_걸치는_heading_도_맞게_돈다() {
        // heading 350: 방위각 80(상대 90) 앞쪽 경계, 260(상대 270) 앞쪽 경계, 170(상대 180) 뒤
        assertThat(BuildingIdentifyService.isAhead(BuildingIdentifyService.relative(80, 350))).isTrue();
        assertThat(BuildingIdentifyService.isAhead(BuildingIdentifyService.relative(260, 350))).isTrue();
        assertThat(BuildingIdentifyService.isAhead(BuildingIdentifyService.relative(170, 350))).isFalse();
        // heading 10: 방위각 350(상대 340) 앞
        assertThat(BuildingIdentifyService.isAhead(BuildingIdentifyService.relative(350, 10))).isTrue();
    }

    // ---- 8방향 · side ----

    @ParameterizedTest(name = "상대 {0}° → {1} / {2}")
    @CsvSource({
            "0, FRONT, AHEAD", "22.499, FRONT, AHEAD", "22.5, FRONT_RIGHT, RIGHT", "45, FRONT_RIGHT, RIGHT",
            "67.499, FRONT_RIGHT, RIGHT", "67.5, RIGHT, RIGHT", "90, RIGHT, RIGHT",
            "270, LEFT, LEFT", "292.499, LEFT, LEFT", "292.5, FRONT_LEFT, LEFT", "337.499, FRONT_LEFT, LEFT",
            "337.5, FRONT, AHEAD", "359.999, FRONT, AHEAD"})
    void 칸_경계는_프론트처럼_시계방향_쪽_칸(double relative, String direction, String side) {
        assertThat(BuildingIdentifyService.direction(relative)).isEqualTo(direction);
        assertThat(BuildingIdentifyService.side(relative)).isEqualTo(side);
    }

    @Test
    void 방향_8칸은_heading_과_방위각_0_1도_간격_전부에서_프론트와_같다() {
        List<String> mismatches = new java.util.ArrayList<>();
        for (int h = 0; h < 360; h++) {
            for (int b10 = 0; b10 < 3600; b10++) {
                double bearing = b10 / 10.0;
                if (!BuildingIdentifyService.direction(BuildingIdentifyService.relative(bearing, h))
                        .equals(frontDirection(bearing, h))) {
                    mismatches.add("heading=" + h + " bearing=" + bearing);
                }
            }
        }
        assertThat(mismatches).isEmpty();
    }

    @Test
    void 앞쪽에서_나오는_방향은_5가지뿐이고_side_와_어긋나지_않는다() {
        for (int r10 = 0; r10 < 3600; r10++) {
            double relative = r10 / 10.0;
            if (!BuildingIdentifyService.isAhead(relative)) {
                continue;
            }
            String direction = BuildingIdentifyService.direction(relative);
            String side = BuildingIdentifyService.side(relative);
            assertThat(direction).isIn("FRONT", "FRONT_RIGHT", "RIGHT", "FRONT_LEFT", "LEFT");
            switch (direction) {
                case "FRONT" -> assertThat(side).isEqualTo("AHEAD");
                case "FRONT_RIGHT", "RIGHT" -> assertThat(side).isEqualTo("RIGHT");
                default -> assertThat(side).isEqualTo("LEFT");
            }
        }
    }

    // ---- 가격·문장 ----

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "123000|12억 3,000만 원", "120000|12억 원", "9500|9,500만 원", "59700|5억 9,700만 원",
            "10000|1억 원", "10001|1억 1만 원", "1|1만 원", "0|0만 원", "1234567|123억 4,567만 원"})
    void 가격_말은_프론트_formatPriceVoice_와_같다(int price, String expected) {
        assertThat(BuildingIdentifyService.priceVoice(price)).isEqualTo(expected);
    }

    @Test
    void 후보가_없으면_명세_문장() {
        assertThat(BuildingIdentifyService.speech(List.of())).isEqualTo("주변에서 거래된 건물을 찾지 못했어요.");
    }

    @Test
    void 거래가_있으면_방향_이름_면적_가격과_저장_질문() {
        Candidate c = candidate("FRONT_LEFT", false, new LatestDeal(59700, LocalDate.of(2026, 9, 30),
                new BigDecimal("36.49"), new BigDecimal("11.0"), 12));
        assertThat(BuildingIdentifyService.speech(List.of(c)))
                .isEqualTo("왼쪽 앞, 한화오벨리스크. 전용 36제곱미터, 최근 거래 5억 9,700만 원이에요. 저장할까요?");
    }

    @Test
    void 이미_저장한_건물이면_저장_질문을_안_한다() {
        Candidate c = candidate("FRONT", true, new LatestDeal(125000, LocalDate.of(2026, 10, 12),
                new BigDecimal("84.97"), new BigDecimal("25.7"), 3));
        assertThat(BuildingIdentifyService.speech(List.of(c)))
                .isEqualTo("앞, 한화오벨리스크. 전용 84제곱미터, 최근 거래 12억 5,000만 원이에요.");
    }

    @Test
    void 최근_1년_거래가_없으면_그렇게_말한다() {
        assertThat(BuildingIdentifyService.speech(List.of(candidate("RIGHT", false, null))))
                .isEqualTo("오른쪽, 한화오벨리스크. 최근 12개월 거래가 없어요. 저장할까요?");
    }

    @Test
    void 문장은_첫_번째_후보로만_만든다() {
        Candidate first = candidate("LEFT", false, null);
        Candidate second = candidate("RIGHT", true, null);
        assertThat(BuildingIdentifyService.speech(List.of(first, second))).startsWith("왼쪽, ");
    }

    private static Candidate candidate(String direction, boolean saved, LatestDeal deal) {
        return new Candidate(21L, "한화오벨리스크", "APT", "서울 마포구 도화동 555", 37.54, 126.94, 100, null, deal,
                BuildingIdentifyService.side(switch (direction) {
                    case "FRONT" -> 0;
                    case "FRONT_RIGHT" -> 45;
                    case "RIGHT" -> 90;
                    case "FRONT_LEFT" -> 315;
                    default -> 270;
                }), direction, saved);
    }
}
