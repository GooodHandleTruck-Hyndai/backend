package com.gooodhandletruck.zippick.domain.device.entity;

import com.gooodhandletruck.zippick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 앱이 설치된 기기. 로그인 대신 익명 ID 로 사용자를 구분해요.
 * 저장 목록, 관심 조건, 알림 같은 사용자별 데이터는 이 기기의 id 를 외래키로 가져요.
 */
@Getter
@Entity
@Table(name = "device")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String anonymousId;

    @Column(length = 20)
    private String appVersion;

    private Device(String anonymousId, String appVersion) {
        this.anonymousId = anonymousId;
        this.appVersion = appVersion;
    }

    /** 새 기기를 만들어요. 익명 ID 는 서버가 UUID 로 발급해요. */
    public static Device register(String appVersion) {
        return new Device(UUID.randomUUID().toString(), appVersion);
    }
}
