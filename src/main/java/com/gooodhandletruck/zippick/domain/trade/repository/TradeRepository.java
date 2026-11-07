package com.gooodhandletruck.zippick.domain.trade.repository;

import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 실거래 조회 (#4 에서 추가) */
public interface TradeRepository extends JpaRepository<Trade, Long> {

    /** 재수집 때 (구, 계약월) 한 달 치 기존 거래를 불러와서 새로 받은 데이터와 비교해요 */
    List<Trade> findBySggCdAndDealYm(String sggCd, String dealYm);
}
