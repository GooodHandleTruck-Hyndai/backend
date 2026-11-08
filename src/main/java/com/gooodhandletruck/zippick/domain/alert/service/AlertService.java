package com.gooodhandletruck.zippick.domain.alert.service;

import com.gooodhandletruck.zippick.domain.alert.dto.AlertListResponse;
import com.gooodhandletruck.zippick.domain.alert.dto.AlertReadResponse;
import com.gooodhandletruck.zippick.domain.alert.dto.AlertResponse;
import com.gooodhandletruck.zippick.domain.alert.entity.Alert;
import com.gooodhandletruck.zippick.domain.alert.repository.AlertRepository;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.util.KstClock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관심 건물 알림 목록 / 알림 읽음 (API 18·19번, #18 에서 추가) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertService {

    /** since 가 없을 때 기본 범위 (명세: 최근 7일) */
    static final int DEFAULT_DAYS = 7;

    /** 한 번에 돌려주는 최대 개수 */
    static final int MAX_ALERTS = 100;

    private final AlertRepository alertRepository;

    /** 18번. since 이후 알림만, 최신순 */
    public AlertListResponse getAlerts(CurrentDevice device, String since) {
        LocalDateTime from = parseSince(since);
        return new AlertListResponse(alertRepository.findRecent(device.deviceId(), from, PageRequest.of(0, MAX_ALERTS))
                .stream()
                .map(AlertResponse::from)
                .toList());
    }

    /** 19번. 이미 읽었으면 처음 읽은 시각 그대로. 내 알림이 아니거나 없으면 N001 */
    @Transactional
    public AlertReadResponse markRead(CurrentDevice device, Long alertId) {
        Alert alert = alertRepository.findByIdAndDeviceId(alertId, device.deviceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND));
        alert.markRead(KstClock.now().toLocalDateTime());
        return new AlertReadResponse(alert.getId(), true, alert.getReadAt().atZone(KstClock.KST).toOffsetDateTime());
    }

    /**
     * "2026-11-03T09:00:00+09:00" → KST 시각. 없으면 최근 7일.
     * URL 에서 + 를 인코딩하지 않으면 공백으로 바뀌어 와서 다시 + 로 돌려요
     */
    static LocalDateTime parseSince(String since) {
        if (since == null || since.isBlank()) {
            return KstClock.now().toLocalDateTime().minusDays(DEFAULT_DAYS);
        }
        try {
            return OffsetDateTime.parse(since.trim().replace(' ', '+'))
                    .atZoneSameInstant(KstClock.KST)
                    .toLocalDateTime();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "since 는 2026-11-03T09:00:00+09:00 형식이어야 해요: " + since);
        }
    }
}
