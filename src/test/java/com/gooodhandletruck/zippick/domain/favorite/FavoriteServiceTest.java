package com.gooodhandletruck.zippick.domain.favorite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.device.dto.DeviceRegisterRequest;
import com.gooodhandletruck.zippick.domain.device.dto.DeviceResponse;
import com.gooodhandletruck.zippick.domain.device.repository.DeviceRepository;
import com.gooodhandletruck.zippick.domain.device.service.DeviceService;
import com.gooodhandletruck.zippick.domain.favorite.dto.FavoriteResponse;
import com.gooodhandletruck.zippick.domain.favorite.service.FavoriteService;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FavoriteServiceTest {

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    private CurrentDevice me;
    private CurrentDevice other;
    private Building first;
    private Building second;

    @BeforeEach
    void setUp() {
        me = registerDevice();
        other = registerDevice();
        first = buildingRepository.save(TestFixtures.building().build());
        second = buildingRepository.save(TestFixtures.building().aptNm("마포래미안푸르지오2단지").build());
    }

    @Test
    void 저장하면_목록에_최신순으로_나온다() {
        favoriteService.save(me, first.getId());
        favoriteService.save(me, second.getId());

        List<FavoriteResponse> favorites = favoriteService.getFavorites(me);

        assertThat(favorites).extracting(FavoriteResponse::buildingId)
                .containsExactly(second.getId(), first.getId());
        assertThat(favorites.get(0).name()).isEqualTo("마포래미안푸르지오2단지");
        assertThat(favorites.get(0).jibunAddress()).endsWith("아현동 777");
    }

    @Test
    void 없는_건물을_저장하면_B001() {
        assertThatThrownBy(() -> favoriteService.save(me, 999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUILDING_NOT_FOUND);
    }

    @Test
    void 같은_건물을_두_번_저장하면_F001() {
        favoriteService.save(me, first.getId());

        assertThatThrownBy(() -> favoriteService.save(me, first.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FAVORITE_ALREADY_EXISTS);
    }

    @Test
    void 다른_기기의_저장_목록은_안_보인다() {
        favoriteService.save(other, first.getId());

        assertThat(favoriteService.getFavorites(me)).isEmpty();
    }

    @Test
    void 저장_취소하면_목록에서_빠진다() {
        favoriteService.save(me, first.getId());

        favoriteService.delete(me, first.getId());

        assertThat(favoriteService.getFavorites(me)).isEmpty();
    }

    @Test
    void 저장하지_않은_건물을_취소하면_F002() {
        assertThatThrownBy(() -> favoriteService.delete(me, first.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FAVORITE_NOT_FOUND);
    }

    @Test
    void 저장_여부를_익명ID로_확인한다() {
        favoriteService.save(me, first.getId());

        assertThat(favoriteService.isSaved(me.anonymousId(), first.getId())).isTrue();
        assertThat(favoriteService.isSaved(other.anonymousId(), first.getId())).isFalse();
        assertThat(favoriteService.isSaved(null, first.getId())).isFalse();
    }

    private CurrentDevice registerDevice() {
        DeviceResponse response = deviceService.register(new DeviceRegisterRequest("0.1.0"));
        Long deviceId = deviceRepository.findByAnonymousId(response.anonymousId()).orElseThrow().getId();
        return new CurrentDevice(deviceId, response.anonymousId());
    }
}
