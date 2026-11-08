package com.gooodhandletruck.zippick.domain.building.service;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingLocationRepository;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.external.juso.JusoClient;
import com.gooodhandletruck.zippick.global.external.juso.UtmK;
import com.gooodhandletruck.zippick.global.external.juso.dto.JusoSearchResponse.Juso;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 건물 좌표를 행안부 API 로 찾아서 buildings 에 저장해요. (#40 에서 추가)
 *
 * 1) "시도 시군구 동 지번" 으로 도로명주소 검색
 * 2) 결과 중 같은 구(admCd 앞 5자리) + 같은 지번만 남기고, 단지명이 겹치는 걸 먼저 골라요
 * 3) 좌표제공 API → UTM-K → 위도·경도 변환 → coordSource = JUSO 로 저장
 *
 * 못 찾으면 NONE 으로 남겨서 다시 찾지 않아요 (건물 상세·주변 건물은 NONE 이면 카카오 실시간으로 찾음).
 * 행안부 API 오류(키·네트워크)는 저장하지 않고 예외를 그대로 던져요 → 다음 배치에서 다시 시도
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildingLocationService {

    private final BuildingLocationRepository buildingLocationRepository;
    private final LegalDongRepository legalDongRepository;
    private final JusoClient jusoClient;

    /** @return 저장한 좌표 출처 (JUSO 또는 NONE) */
    @Transactional
    public CoordSource fill(Long buildingId) {
        Building building = buildingLocationRepository.findById(buildingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUILDING_NOT_FOUND));

        Optional<JibunLot> lot = JibunLot.parse(building.getJibun());
        if (lot.isEmpty()) {
            return markNotFound(building);
        }

        LegalDong region = building.getLegalDong() != null
                ? building.getLegalDong()
                : legalDongRepository.findFirstBySggCd(building.getSggCd()).orElse(null);
        Optional<Juso> matched = pick(jusoClient.search(keyword(region, building)), building, lot.get());
        if (matched.isEmpty()) {
            return markNotFound(building);
        }

        Juso juso = matched.get();
        Optional<double[]> utmk = jusoClient.coordinate(juso);
        if (utmk.isEmpty()) {
            return markNotFound(building);
        }

        double[] latLon = UtmK.toWgs84(utmk.get()[0], utmk.get()[1]);
        LegalDong legalDong = legalDongRepository.findById(juso.admCd()).orElse(building.getLegalDong());
        building.updateLocation(legalDong, juso.roadAddr(), latLon[0], latLon[1], CoordSource.JUSO);
        return CoordSource.JUSO;
    }

    static String keyword(LegalDong region, Building building) {
        String dongAndLot = building.getUmdNm() + " " + building.getJibun().trim();
        if (region == null) {
            return dongAndLot;
        }
        return region.getSidoNm() + " " + region.getSggNm() + " " + dongAndLot;
    }

    /** 같은 구·같은 지번만. 여러 개면 단지명이 겹치는 것 먼저 (한 지번에 1·2단지가 따로 있는 경우) */
    static Optional<Juso> pick(List<Juso> results, Building building, JibunLot lot) {
        String aptName = normalize(building.getAptNm());
        return results.stream()
                .filter(juso -> juso.admCd() != null && juso.admCd().startsWith(building.getSggCd()))
                .filter(juso -> lot.matches(juso.mtYn(), juso.lnbrMnnm(), juso.lnbrSlno()))
                .min(Comparator.comparingInt(juso -> nameMatches(normalize(juso.bdNm()), aptName) ? 0 : 1));
    }

    private static boolean nameMatches(String buildingName, String aptName) {
        return !buildingName.isEmpty() && !aptName.isEmpty()
                && (buildingName.contains(aptName) || aptName.contains(buildingName));
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replaceAll("\\s", "");
    }

    private CoordSource markNotFound(Building building) {
        building.updateLocation(building.getLegalDong(), building.getRoadAddr(), null, null, CoordSource.NONE);
        return CoordSource.NONE;
    }
}
