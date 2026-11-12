package com.gooodhandletruck.zippick.domain.building.repository;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import com.gooodhandletruck.zippick.domain.building.entity.CoordSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 건물 조회 (#4 에서 추가)
 * #50 변경: 앞쪽 건물 찾기(API 20번)용 "좌표 사각형 안 + 좌표 출처" 조회 추가 (idx_buildings_lat_lng 인덱스 사용).
 */
public interface BuildingRepository extends JpaRepository<Building, Long> {

    /** 실거래를 적재할 때 이미 있는 건물인지 건물 구분 키로 찾아요 */
    Optional<Building> findByBuildingTypeAndSggCdAndUmdNmAndJibunAndAptNm(
            BuildingType buildingType, String sggCd, String umdNm, String jibun, String aptNm);

    /**
     * #50: 저장된 좌표가 위·경도 사각형 안에 있는 건물. 원(반경)·방향은 서비스에서 다시 걸러요 —
     * 여기서는 DB 에서 가져올 건물 수만 줄여요 (주행 중 응답이라 외부 API 없이 DB 만 봐요).
     */
    @Query("select b from Building b where b.coordSource = :source "
            + "and b.latitude between :minLat and :maxLat and b.longitude between :minLon and :maxLon")
    List<Building> findWithCoordinateIn(@Param("source") CoordSource source,
            @Param("minLat") double minLat, @Param("maxLat") double maxLat,
            @Param("minLon") double minLon, @Param("maxLon") double maxLon);
}
