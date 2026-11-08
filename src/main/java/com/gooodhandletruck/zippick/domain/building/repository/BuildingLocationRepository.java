package com.gooodhandletruck.zippick.domain.building.repository;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * 좌표 채우기 전용 조회. BuildingRepository(실거래 적재용)와 나눠 둬요
 */
public interface BuildingLocationRepository extends Repository<Building, Long> {

    Optional<Building> findById(Long id);

    /** 좌표를 한 번도 찾아보지 않은 건물 (coord_source 가 비어 있음) */
    List<Building> findByCoordSourceIsNullOrderByIdAsc(Pageable pageable);
}
