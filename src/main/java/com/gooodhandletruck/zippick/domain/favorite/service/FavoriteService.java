package com.gooodhandletruck.zippick.domain.favorite.service;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.building.service.JibunAddressFormatter;
import com.gooodhandletruck.zippick.domain.device.entity.Device;
import com.gooodhandletruck.zippick.domain.device.repository.DeviceRepository;
import com.gooodhandletruck.zippick.domain.favorite.dto.FavoriteResponse;
import com.gooodhandletruck.zippick.domain.favorite.entity.Favorite;
import com.gooodhandletruck.zippick.domain.favorite.repository.FavoriteRepository;
import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.domain.region.repository.LegalDongRepository;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 저장하기 / 저장 목록 / 저장 취소 (API 9~11번, #12 에서 추가) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final DeviceRepository deviceRepository;
    private final BuildingRepository buildingRepository;
    private final LegalDongRepository legalDongRepository;

    /** 9번 저장하기. 없는 건물이면 B001, 이미 저장했으면 F001 */
    @Transactional
    public FavoriteResponse save(CurrentDevice currentDevice, Long buildingId) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUILDING_NOT_FOUND));
        if (favoriteRepository.existsByDeviceIdAndBuildingId(currentDevice.deviceId(), buildingId)) {
            throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }

        Device device = deviceRepository.getReferenceById(currentDevice.deviceId());
        try {
            Favorite favorite = favoriteRepository.saveAndFlush(Favorite.of(device, building));
            return FavoriteResponse.of(favorite, jibunAddress(building, new HashMap<>()));
        } catch (DataIntegrityViolationException e) {
            // 거의 동시에 두 번 눌러서 유니크 제약에 걸린 경우
            throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }
    }

    /** 10번 저장 목록. 최근에 저장한 순서, 없으면 빈 목록 */
    public List<FavoriteResponse> getFavorites(CurrentDevice currentDevice) {
        Map<String, Optional<LegalDong>> regionCache = new HashMap<>();
        return favoriteRepository.findAllWithBuildingByDeviceId(currentDevice.deviceId()).stream()
                .map(favorite -> FavoriteResponse.of(favorite, jibunAddress(favorite.getBuilding(), regionCache)))
                .toList();
    }

    /** 11번 저장 취소. 저장하지 않은 건물이면 F002 */
    @Transactional
    public void delete(CurrentDevice currentDevice, Long buildingId) {
        Favorite favorite = favoriteRepository.findByDeviceIdAndBuildingId(currentDevice.deviceId(), buildingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_NOT_FOUND));
        favoriteRepository.delete(favorite);
    }

    /**
     * 건물 상세(7번)의 saved 값. 헤더가 없으면 false.
     * BuildingService.getDetail 에서 이걸 부르면 돼요
     */
    public boolean isSaved(String anonymousId, Long buildingId) {
        if (!StringUtils.hasText(anonymousId)) {
            return false;
        }
        return favoriteRepository.existsByDeviceAnonymousIdAndBuildingId(anonymousId.trim(), buildingId);
    }

    /** 건물 상세와 같은 방식 (BuildingService.findRegion). 같은 구는 한 번만 조회 */
    private String jibunAddress(Building building, Map<String, Optional<LegalDong>> regionCache) {
        LegalDong region = building.getLegalDong() != null
                ? building.getLegalDong()
                : regionCache.computeIfAbsent(building.getSggCd(), legalDongRepository::findFirstBySggCd)
                        .orElse(null);
        return JibunAddressFormatter.format(region, building.getUmdNm(), building.getJibun());
    }
}
