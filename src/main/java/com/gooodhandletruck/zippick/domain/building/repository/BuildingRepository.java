package com.gooodhandletruck.zippick.domain.building.repository;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 건물 조회 (#4 에서 추가) */
public interface BuildingRepository extends JpaRepository<Building, Long> {

    /** 실거래를 적재할 때 이미 있는 건물인지 건물 구분 키로 찾아요 */
    Optional<Building> findByBuildingTypeAndSggCdAndUmdNmAndJibunAndAptNm(
            BuildingType buildingType, String sggCd, String umdNm, String jibun, String aptNm);
}
