package com.gooodhandletruck.zippick.domain.trade.batch;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 실거래 수집 배치 설정 (application.yml 의 zippick.collect). (#17 에서 추가: 시연 지역 사전 저장)
 * #18 변경: 재수집(API 12번) refresh 설정 추가.
 *
 * 값이 잘못되면 서버가 켜질 때 바로 실패해요 — 잘못된 구 코드로 국토부를 12번 부르고 나서야 알게 되는 것보다 낫고,
 * 수집 이력 sgg_cd(5자리) 저장이 실패하는 것도 막아요.
 *
 * @param sggCds  수집할 구 코드 5자리 목록 (예: 마포구 11440)
 * @param preload 서버 시작 때 사전 저장 (API 5번)
 * @param refresh 매일 최근 몇 개월 재수집 (API 12번)
 */
@ConfigurationProperties("zippick.collect")
public record CollectProperties(
        @DefaultValue("11440") List<String> sggCds,
        @DefaultValue Preload preload,
        @DefaultValue Refresh refresh) {

    private static final Pattern SGG_CD = Pattern.compile("\\d{5}");
    static final int MAX_MONTHS = 36;

    public CollectProperties {
        if (sggCds == null || sggCds.isEmpty()) {
            throw new IllegalArgumentException("zippick.collect.sgg-cds 가 비어 있어요");
        }
        for (String sggCd : sggCds) {
            if (sggCd == null || !SGG_CD.matcher(sggCd).matches()) {
                throw new IllegalArgumentException("zippick.collect.sgg-cds 는 숫자 5자리예요: " + sggCd);
            }
        }
        sggCds = List.copyOf(sggCds);
    }

    /**
     * @param enabled 켜면 서버 시작 직후 한 번 돌아요. 기본 꺼짐 — 테스트·CI 에서 실제 국토부를 부르지 않게
     * @param months  이번 달 포함 몇 개월 (명세: 최근 12개월)
     */
    public record Preload(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("12") int months) {

        public Preload {
            checkMonths("zippick.collect.preload.months", months);
        }
    }

    /**
     * 실행 시각은 cron(zippick.collect.refresh.cron, 기본 매일 04:00 KST)으로 정해요 — 스케줄러가 직접 읽어요.
     *
     * @param enabled 켜면 스케줄이 등록돼요. 기본 꺼짐 — 테스트·CI 에서 실제 국토부를 부르지 않게
     * @param months  이번 달 포함 몇 개월 (명세: 2~3개월 중 3개월로 결정 — 신고가 최대 30일 늦고 취소는 더 늦게 표시돼서)
     */
    public record Refresh(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("3") int months) {

        public Refresh {
            checkMonths("zippick.collect.refresh.months", months);
        }
    }

    static void checkMonths(String name, int months) {
        if (months < 1 || months > MAX_MONTHS) {
            throw new IllegalArgumentException(name + " 는 1~" + MAX_MONTHS + " 이에요: " + months);
        }
    }
}
