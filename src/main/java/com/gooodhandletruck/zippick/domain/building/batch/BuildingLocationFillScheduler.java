package com.gooodhandletruck.zippick.domain.building.batch;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingLocationRepository;
import com.gooodhandletruck.zippick.domain.building.service.BuildingLocationService;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.external.juso.JusoProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 좌표 없는 건물을 행안부 API 로 채우는 배치. (#40 에서 추가)
 *
 * zippick.juso.fill.enabled=true 일 때만 만들어져요. 꺼져 있으면 테스트·CI·다른 팀원 로컬에서 행안부를 안 불러요.
 * 서버 시작 30초 뒤 처음 돌고, 끝나면 delay-ms 만큼 쉬고 다시 돌아요. 실거래 적재로 새 건물이 생기면 다음 번에 채워져요.
 * 건물 사이에는 pause-ms 만큼 쉬어요 (행안부 다량 요청 E0007 방지)
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "zippick.juso.fill.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BuildingLocationFillScheduler {

    /** 행안부 키·네트워크·다량 요청 오류가 이만큼 연속되면 이번 실행은 그만둬요 (그 건물들은 다음 실행 때 다시) */
    private static final int MAX_CONSECUTIVE_ERRORS = 3;

    private final BuildingLocationRepository buildingLocationRepository;
    private final BuildingLocationService buildingLocationService;
    private final JusoProperties properties;

    @Scheduled(initialDelay = 30_000, fixedDelayString = "${zippick.juso.fill.delay-ms:600000}")
    public void fill() {
        List<Building> targets = buildingLocationRepository.findByCoordSourceIsNullOrderByIdAsc(
                PageRequest.of(0, properties.fill().batchSize()));
        if (targets.isEmpty()) {
            return;
        }

        int found = 0;
        int notFound = 0;
        int errors = 0;
        for (int i = 0; i < targets.size(); i++) {
            if (i > 0 && !pause(properties.fill().pauseMs())) {
                break;
            }
            Building building = targets.get(i);
            try {
                if (buildingLocationService.fill(building.getId()) == CoordSource.JUSO) {
                    found++;
                } else {
                    notFound++;
                }
                errors = 0;
            } catch (BusinessException e) {
                if (++errors >= MAX_CONSECUTIVE_ERRORS) {
                    log.warn("행안부 API 오류가 {}번 연속이라 이번 좌표 채우기는 멈춤", errors);
                    break;
                }
            }
        }
        log.info("건물 좌표 채우기: 대상 {} 찾음 {} 못 찾음 {}", targets.size(), found, notFound);
    }

    /** @return 끊기지 않고 다 쉬었으면 true. 서버 종료 등으로 끊기면 false → 이번 실행은 그만둬요 */
    private static boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
