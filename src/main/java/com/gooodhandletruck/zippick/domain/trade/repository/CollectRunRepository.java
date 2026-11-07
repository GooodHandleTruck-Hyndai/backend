package com.gooodhandletruck.zippick.domain.trade.repository;

import com.gooodhandletruck.zippick.domain.trade.entity.CollectRun;
import com.gooodhandletruck.zippick.domain.trade.entity.CollectStatus;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 실거래 수집 이력 (#8 에서 추가, #19 에서 마지막 성공 시각 조회 추가) */
public interface CollectRunRepository extends JpaRepository<CollectRun, Long> {

    /**
     * 그 구에서 마지막으로 국토부 데이터를 받아 반영한 시각 (#19).
     * 자료 기준일(dataBaseDate)로 써요 — 재수집했는데 새 거래가 없어도 "다시 받은 날짜"가 바뀌어야 해서(명세 12번).
     * 건너뜀(SKIPPED)·실패(FAILED)는 데이터가 그대로라 빼요.
     */
    @Query("select max(r.finishedAt) from CollectRun r where r.sggCd = :sggCd and r.status = :status")
    LocalDateTime findLastFinishedAt(@Param("sggCd") String sggCd, @Param("status") CollectStatus status);

    default LocalDateTime findLastSuccessAt(String sggCd) {
        return findLastFinishedAt(sggCd, CollectStatus.SUCCESS);
    }
}
