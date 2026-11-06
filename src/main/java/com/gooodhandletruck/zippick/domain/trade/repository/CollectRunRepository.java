package com.gooodhandletruck.zippick.domain.trade.repository;

import com.gooodhandletruck.zippick.domain.trade.entity.CollectRun;
import org.springframework.data.jpa.repository.JpaRepository;

/** 실거래 수집 이력 (#8 에서 추가) */
public interface CollectRunRepository extends JpaRepository<CollectRun, Long> {
}
