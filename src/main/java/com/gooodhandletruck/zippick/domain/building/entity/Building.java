package com.gooodhandletruck.zippick.domain.building.entity;

import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import com.gooodhandletruck.zippick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 건물(단지) (buildings). id 가 API 의 buildingId 예요.
 *
 * 국토부 실거래에는 건물 고유번호가 없어서, (유형, sggCd, umdNm, jibun, aptNm) 조합으로 같은 건물을 알아봐요.
 * 좌표는 국토부 응답에 없어서 주소 API 로 따로 채워요. (#4 에서 추가)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "buildings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_buildings_key",
                // 단지명까지 넣는 이유: 아현동 777 한 지번에 마포래미안푸르지오 1·2·4단지가 따로 있어요
                columnNames = {"building_type", "sgg_cd", "umd_nm", "jibun", "apt_nm"}),
        indexes = @Index(name = "idx_buildings_lat_lng", columnList = "latitude, longitude"))
public class Building extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "building_type", nullable = false, length = 10)
    private BuildingType buildingType;

    /** 국토부 sggCd (시군구 코드 5자리) */
    @Column(name = "sgg_cd", nullable = false, length = 5)
    private String sggCd;

    /** 국토부 umdNm (법정동명) */
    @Column(name = "umd_nm", nullable = false, length = 20)
    private String umdNm;

    /** 국토부 jibun. 비어 오면 "" 로 저장해요 (NULL 이면 UNIQUE 가 중복을 못 막아요) */
    @Column(name = "jibun", nullable = false, length = 20)
    private String jibun;

    /** 국토부 aptNm */
    @Column(name = "apt_nm", nullable = false, length = 100)
    private String aptNm;

    @Column(name = "build_year")
    private Integer buildYear;

    /** 주소 매칭에 성공했을 때만 채워져요 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adm_cd")
    private LegalDong legalDong;

    @Column(name = "road_addr", length = 200)
    private String roadAddr;

    /** WGS84 위도. 주변 건물 반경 검색에 써요 */
    @Column(name = "latitude")
    private Double latitude;

    /** WGS84 경도 */
    @Column(name = "longitude")
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "coord_source", length = 10)
    private CoordSource coordSource;

    @Builder
    private Building(BuildingType buildingType, String sggCd, String umdNm, String jibun, String aptNm,
            Integer buildYear) {
        this.buildingType = buildingType;
        this.sggCd = sggCd;
        this.umdNm = umdNm;
        // UNIQUE 키에 NULL 이 섞이면 MySQL 이 중복으로 보지 않아서, 빈 지번은 "" 로 통일해요
        this.jibun = jibun == null ? "" : jibun.trim();
        this.aptNm = aptNm;
        this.buildYear = buildYear;
    }

    /** 주소 API 로 찾은 주소·좌표를 채워요 */
    public void updateLocation(LegalDong legalDong, String roadAddr, Double latitude, Double longitude,
            CoordSource coordSource) {
        this.legalDong = legalDong;
        this.roadAddr = roadAddr;
        this.latitude = latitude;
        this.longitude = longitude;
        this.coordSource = coordSource;
    }
}
