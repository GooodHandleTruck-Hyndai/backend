package com.gooodhandletruck.zippick.domain.alert.repository;

import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 알림 만들기 전용 거래 조회. TradeRepository(실거래 적재용)와 나눠 둬요.
 * 해제 거래(cdeal_type 있음)는 빼요
 */
public interface AlertTradeRepository extends Repository<Trade, Long> {

    @Query("select t from Trade t join fetch t.building "
            + "where t.sggCd in :sggCds and t.createdAt >= :after and t.dealDate >= :minDealDate "
            + "and t.cdealType is null")
    List<Trade> findNewInRegions(@Param("sggCds") Collection<String> sggCds,
            @Param("after") LocalDateTime after, @Param("minDealDate") LocalDate minDealDate);

    @Query("select t from Trade t join fetch t.building "
            + "where t.building.id in :buildingIds and t.createdAt >= :after and t.dealDate >= :minDealDate "
            + "and t.cdealType is null")
    List<Trade> findNewInBuildings(@Param("buildingIds") Collection<Long> buildingIds,
            @Param("after") LocalDateTime after, @Param("minDealDate") LocalDate minDealDate);
}
