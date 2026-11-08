package com.gooodhandletruck.zippick.domain.favorite.repository;

import com.gooodhandletruck.zippick.domain.favorite.entity.Favorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /** 최신순 (id 가 클수록 나중에 저장). 건물을 같이 가져와서 N+1 방지 */
    @Query("select f from Favorite f join fetch f.building where f.device.id = :deviceId order by f.id desc")
    List<Favorite> findAllWithBuildingByDeviceId(@Param("deviceId") Long deviceId);

    Optional<Favorite> findByDeviceIdAndBuildingId(Long deviceId, Long buildingId);

    boolean existsByDeviceIdAndBuildingId(Long deviceId, Long buildingId);

    /** 건물 상세의 saved 계산용 */
    boolean existsByDeviceAnonymousIdAndBuildingId(String anonymousId, Long buildingId);

    /** 알림 만들기용: 기기가 저장한 건물 ID (#18) */
    @Query("select f.building.id from Favorite f where f.device.id = :deviceId")
    List<Long> findBuildingIdsByDeviceId(@Param("deviceId") Long deviceId);
}
