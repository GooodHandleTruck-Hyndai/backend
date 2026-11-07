package com.gooodhandletruck.zippick.domain.trade.entity;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아파트 매매 실거래 (trades). 국토부 RTMSDataSvcAptTrade 응답 한 줄 = 한 행.
 *
 * 국토부 응답에는 거래 고유번호가 없고 모든 값이 같은 정상 거래도 있어서,
 * 지문(fingerprint) + 순번(seq) 으로 고유 키를 만들어요. 규칙은 노션 「실거래 적재 규칙」 참고.
 * createdAt(BaseTimeEntity) = 처음 수집된 시각이라, 새 신고 감지(17번)에 그대로 써요. (#4 에서 추가)
 *
 * #8 변경: 전용면적을 DECIMAL(7,2) → DECIMAL(9,4). 국토부가 113.803, 59.9236 처럼 소수 3~4자리를 보내서
 * 반올림 없이 원본 그대로 저장하고, 화면 응답에서만 소수 2자리로 보여 줘요.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "trades",
        // 필드 조합에 UNIQUE 를 걸면 같은 값의 진짜 거래가 빠져서, 지문 + 순번에 걸어요
        uniqueConstraints = @UniqueConstraint(name = "uk_trades_fp_seq", columnNames = {"fingerprint", "seq"}),
        indexes = {
                @Index(name = "idx_trades_building_date", columnList = "building_id, deal_date"),
                // 재수집은 (구, 계약월) 단위로 통째 비교해요
                @Index(name = "idx_trades_sgg_ym", columnList = "sgg_cd, deal_ym")
        })
public class Trade extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    /** 바뀌지 않는 값으로 만든 SHA-256 (64자 hex) */
    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    /** 같은 지문 안의 순번 1, 2, ... */
    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "sgg_cd", nullable = false, length = 5)
    private String sggCd;

    /** 계약월 YYYYMM */
    @Column(name = "deal_ym", nullable = false, length = 6)
    private String dealYm;

    @Column(name = "deal_date", nullable = false)
    private LocalDate dealDate;

    /** 거래금액, 만원 단위 */
    @Column(name = "deal_amount", nullable = false)
    private Integer dealAmount;

    /** 전용면적 ㎡ (응답 areaM2). 국토부 원본 소수 4자리까지 그대로 (#8) */
    @Column(name = "exclu_use_ar", nullable = false, precision = 9, scale = 4)
    private BigDecimal excluUseAr;

    /** 층. 지하는 음수 */
    @Column(name = "floor", nullable = false)
    private Integer floor;

    // ---- 아래는 나중에 바뀌는 값이라 지문에 넣지 않아요 ----

    /** 동 (응답 dong). 등기 후에 채워져서 처음엔 비어 있을 수 있어요 */
    @Column(name = "apt_dong", length = 20)
    private String aptDong;

    /** 해제 여부. 해제면 "O", 정상이면 NULL. 화면 조회는 NULL 만 내보내요 */
    @Column(name = "cdeal_type", length = 1)
    private String cdealType;

    @Column(name = "cdeal_day")
    private LocalDate cdealDay;

    @Column(name = "rgst_date")
    private LocalDate rgstDate;

    // ---- 참고용 (화면에 아직 안 씀) ----

    /** 거래유형: 중개거래 / 직거래 */
    @Column(name = "dealing_gbn", length = 10)
    private String dealingGbn;

    @Column(name = "estate_agent_sgg_nm", length = 200)
    private String estateAgentSggNm;

    @Column(name = "sler_gbn", length = 10)
    private String slerGbn;

    @Column(name = "buyer_gbn", length = 10)
    private String buyerGbn;

    @Column(name = "land_leasehold_gbn", length = 1)
    private String landLeaseholdGbn;

    @Builder
    private Trade(Building building, String fingerprint, Integer seq, String sggCd, String dealYm,
            LocalDate dealDate, Integer dealAmount, BigDecimal excluUseAr, Integer floor,
            String aptDong, String cdealType, LocalDate cdealDay, LocalDate rgstDate,
            String dealingGbn, String estateAgentSggNm, String slerGbn, String buyerGbn,
            String landLeaseholdGbn) {
        this.building = building;
        this.fingerprint = fingerprint;
        this.seq = seq;
        this.sggCd = sggCd;
        this.dealYm = dealYm;
        this.dealDate = dealDate;
        this.dealAmount = dealAmount;
        this.excluUseAr = excluUseAr;
        this.floor = floor;
        this.aptDong = aptDong;
        this.cdealType = cdealType;
        this.cdealDay = cdealDay;
        this.rgstDate = rgstDate;
        this.dealingGbn = dealingGbn;
        this.estateAgentSggNm = estateAgentSggNm;
        this.slerGbn = slerGbn;
        this.buyerGbn = buyerGbn;
        this.landLeaseholdGbn = landLeaseholdGbn;
    }

    /** 재수집 때 이미 있는 거래면 바뀌는 값만 고쳐요 (지문에 든 값은 그대로) */
    public void updateChangeableFields(String aptDong, String cdealType, LocalDate cdealDay, LocalDate rgstDate) {
        this.aptDong = aptDong;
        this.cdealType = cdealType;
        this.cdealDay = cdealDay;
        this.rgstDate = rgstDate;
    }
}
