package com.gooodhandletruck.zippick.global.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 한국 시간(KST) 기준 시간 도우미 */
public final class KstClock {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private KstClock() {
    }

    public static OffsetDateTime now() {
        return OffsetDateTime.now(KST);
    }

    public static LocalDate today() {
        return LocalDate.now(KST);
    }
}
