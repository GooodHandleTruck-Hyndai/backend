package com.gooodhandletruck.zippick.domain.trade.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import com.gooodhandletruck.zippick.global.util.KstClock;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 실거래 수집 이력 (collect_runs). (구, 계약월) 한 번 수집할 때마다 한 줄. (#8 에서 추가)
 *
 * 5번(시연 지역 사전 저장)·12번(재수집) 배치가 언제 무엇을 얼마나 바꿨는지, 왜 건너뛰었는지 남겨요.
 * ERD 의 alert_held(알림 보류)는 쓰는 곳(17번 알림)이 생길 때 추가해요.
 *
 * #19 변경: 시작·종료 시각을 한국 시간(KstClock)으로 기록. 이 날짜가 화면의 자료 기준일이 되는데,
 * 서버가 UTC 면 04:00(KST) 재수집이 전날 19:00 으로 찍혀 기준일이 하루 늦게 보여서예요.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "collect_runs", indexes = @Index(name = "idx_collect_runs_sgg_ym", columnList = "sgg_cd, deal_ym"))
public class CollectRun {

    /** 실패 이유가 길어도 저장이 실패하지 않게 잘라요 */
    static final int MESSAGE_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sgg_cd", nullable = false, length = 5)
    private String sggCd;

    /** 계약월 YYYYMM */
    @Column(name = "deal_ym", nullable = false, length = 6)
    private String dealYm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private CollectStatus status;

    /** 국토부 결과 코드. 받기 성공이면 000, 호출·파싱 실패면 NULL 일 수 있어요 */
    @Column(name = "result_code", length = 10)
    private String resultCode;

    /** 국토부가 알려 준 전체 건수 */
    @Column(name = "total_count")
    private Integer totalCount;

    /** 실제로 받은 건수 (total_count 와 같아야 반영) */
    @Column(name = "fetched_count")
    private Integer fetchedCount;

    @Column(name = "inserted_count", nullable = false)
    private int insertedCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "deleted_count", nullable = false)
    private int deletedCount;

    /** 건너뜀·실패 이유 (키는 들어가지 않아요 — 클라이언트가 메시지에서 뺌) */
    @Column(name = "message", length = MESSAGE_MAX)
    private String message;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    private CollectRun(String sggCd, String dealYm) {
        this.sggCd = sggCd;
        this.dealYm = dealYm;
        this.status = CollectStatus.RUNNING;
        this.startedAt = LocalDateTime.now(KstClock.KST);
    }

    public static CollectRun start(String sggCd, String dealYm) {
        return new CollectRun(sggCd, dealYm);
    }

    /** 국토부에서 받은 결과를 적어 둬요 (반영 여부와 상관없이) */
    public void fetched(int totalCount, int fetchedCount) {
        this.resultCode = "000"; // 클라이언트는 결과 코드가 000 일 때만 결과를 돌려줘요
        this.totalCount = totalCount;
        this.fetchedCount = fetchedCount;
    }

    public void succeed(int inserted, int updated, int deleted) {
        this.insertedCount = inserted;
        this.updatedCount = updated;
        this.deletedCount = deleted;
        finish(CollectStatus.SUCCESS, null);
    }

    public void skip(String reason) {
        finish(CollectStatus.SKIPPED, reason);
    }

    public void fail(String resultCode, String reason) {
        if (resultCode != null) {
            this.resultCode = resultCode;
        }
        finish(CollectStatus.FAILED, reason);
    }

    private void finish(CollectStatus status, String reason) {
        this.status = status;
        this.message = reason == null || reason.length() <= MESSAGE_MAX ? reason : reason.substring(0, MESSAGE_MAX);
        this.finishedAt = LocalDateTime.now(KstClock.KST);
    }
}
