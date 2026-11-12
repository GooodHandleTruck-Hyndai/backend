package com.gooodhandletruck.zippick.domain.interest.entity;

import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import com.gooodhandletruck.zippick.domain.device.entity.Device;
import com.gooodhandletruck.zippick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기기별 관심 조건 (interest_conditions). 기기당 하나. (#14 에서 추가)
 * 가격은 만원, 면적은 ㎡. 범위의 한쪽이 null 이면 그쪽은 제한 없음
 */
@Getter
@Entity
@Table(name = "interest_conditions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterestCondition extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    private Device device;

    /** 시군구 코드 5자리 목록. DB 에는 "11440,11680" 처럼 저장 */
    @Convert(converter = StringListConverter.class)
    @Column(name = "region_codes", nullable = false, length = 100)
    private List<String> regionCodes;

    /** 건물 유형 목록. DB 에는 "APT,OFFICETEL" 처럼 저장 */
    @Convert(converter = BuildingTypeListConverter.class)
    @Column(name = "types", nullable = false, length = 50)
    private List<BuildingType> types;

    @Column(name = "price_min")
    private Integer priceMin;

    @Column(name = "price_max")
    private Integer priceMax;

    @Column(name = "area_min_m2")
    private Integer areaMinM2;

    @Column(name = "area_max_m2")
    private Integer areaMaxM2;

    @Column(name = "alert_enabled", nullable = false)
    private boolean alertEnabled;

    private InterestCondition(Device device) {
        this.device = device;
    }

    public static InterestCondition of(Device device) {
        return new InterestCondition(device);
    }

    /** 전체 값을 그대로 바꿔요 (PUT) */
    public void update(List<String> regionCodes, List<BuildingType> types, Integer priceMin, Integer priceMax,
            Integer areaMinM2, Integer areaMaxM2, boolean alertEnabled) {
        this.regionCodes = List.copyOf(regionCodes);
        this.types = List.copyOf(types);
        this.priceMin = priceMin;
        this.priceMax = priceMax;
        this.areaMinM2 = areaMinM2;
        this.areaMaxM2 = areaMaxM2;
        this.alertEnabled = alertEnabled;
    }
}
