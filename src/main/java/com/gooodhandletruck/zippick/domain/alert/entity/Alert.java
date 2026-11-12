package com.gooodhandletruck.zippick.domain.alert.entity;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.device.entity.Device;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관심 건물 알림 (alerts). 기기 하나에 새 거래 하나. (#18 에서 추가)
 *
 * 거래 값(금액·계약일·면적)은 알림을 만들 때 복사해 둬요.
 * trades 는 재수집 때 지워질 수 있어서 외래키를 걸지 않고 trade_id 만 숫자로 남겨요 (같은 거래 중복 알림 방지용)
 */
@Getter
@Entity
@Table(name = "alerts",
        uniqueConstraints = @UniqueConstraint(name = "uk_alerts_device_trade", columnNames = {"device_id", "trade_id"}),
        indexes = @Index(name = "idx_alerts_device_created", columnList = "device_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alert extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "trade_id", nullable = false)
    private Long tradeId;

    /** 거래금액, 만원 */
    @Column(name = "deal_amount", nullable = false)
    private Integer dealAmount;

    @Column(name = "deal_date", nullable = false)
    private LocalDate dealDate;

    /** 전용면적 ㎡ (trades 와 같은 정밀도) */
    @Column(name = "exclu_use_ar", nullable = false, precision = 9, scale = 4)
    private BigDecimal excluUseAr;

    /** 처음 읽은 시각. 안 읽었으면 null */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    private Alert(Device device, Trade trade) {
        this.device = device;
        this.building = trade.getBuilding();
        this.tradeId = trade.getId();
        this.dealAmount = trade.getDealAmount();
        this.dealDate = trade.getDealDate();
        this.excluUseAr = trade.getExcluUseAr();
    }

    public static Alert of(Device device, Trade trade) {
        return new Alert(device, trade);
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** 이미 읽었으면 처음 읽은 시각을 그대로 둬요 */
    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
