package com.gooodhandletruck.zippick.domain.favorite.entity;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.device.entity.Device;
import com.gooodhandletruck.zippick.global.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기기별로 저장한 건물 (favorites). (#12 에서 추가)
 * 건물 이름·주소는 buildings 에 있어서 따로 저장하지 않음
 */
@Getter
@Entity
@Table(name = "favorites", uniqueConstraints = @UniqueConstraint(
        name = "uk_favorites_device_building", columnNames = {"device_id", "building_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    private Favorite(Device device, Building building) {
        this.device = device;
        this.building = building;
    }

    public static Favorite of(Device device, Building building) {
        return new Favorite(device, building);
    }
}
