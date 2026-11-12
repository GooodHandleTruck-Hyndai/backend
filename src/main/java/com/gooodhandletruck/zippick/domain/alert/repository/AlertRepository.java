package com.gooodhandletruck.zippick.domain.alert.repository;

import com.gooodhandletruck.zippick.domain.alert.entity.Alert;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /** 최신순. 건물을 같이 가져와서 N+1 방지 */
    @Query("select a from Alert a join fetch a.building "
            + "where a.device.id = :deviceId and a.createdAt > :since order by a.id desc")
    List<Alert> findRecent(@Param("deviceId") Long deviceId, @Param("since") LocalDateTime since, Pageable pageable);

    Optional<Alert> findByIdAndDeviceId(Long id, Long deviceId);

    /** 이미 알림을 만든 거래 */
    @Query("select a.tradeId from Alert a where a.device.id = :deviceId and a.tradeId in :tradeIds")
    List<Long> findTradeIds(@Param("deviceId") Long deviceId, @Param("tradeIds") Collection<Long> tradeIds);
}
