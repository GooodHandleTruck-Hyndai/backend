package com.gooodhandletruck.zippick.domain.trade.repository;

import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 실거래 조회 (#4 에서 추가, #6 에서 건물 상세용 조회 추가) */
public interface TradeRepository extends JpaRepository<Trade, Long> {

    /** 재수집 때 (구, 계약월) 한 달 치 기존 거래를 불러와서 새로 받은 데이터와 비교해요 */
    List<Trade> findBySggCdAndDealYm(String sggCd, String dealYm);

    /**
     * 건물 상세의 최근 거래. 해제 거래는 빼고 계약일 최신순(같은 날이면 나중에 들어온 순).
     * 화면엔 3건만 보여 주지만 4건을 읽어서, 4번째가 있으면 「더보기」를 띄워요 (개수 쿼리를 따로 안 하려고)
     */
    List<Trade> findTop4ByBuildingIdAndCdealTypeIsNullOrderByDealDateDescIdDesc(Long buildingId);

    /** 그 구에서 가장 최근에 수집된 시각. 수집 이력(5·12번 배치)이 생기기 전까지 자료 기준일로 써요 */
    @Query("select max(t.createdAt) from Trade t where t.sggCd = :sggCd")
    LocalDateTime findLatestCollectedAt(@Param("sggCd") String sggCd);
}
