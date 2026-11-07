package com.gooodhandletruck.zippick.domain.trade.repository;

import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 실거래 조회 (#4 에서 추가, #6·#16 에서 건물 상세·실거래 내역 조회, #8 에서 existsBySggCdAndDealYm, #26 에서 가격 추이 집계, #27 에서 유형별 범위 조회 추가, #35 에서 주변 건물 latestDeal 조회 추가) */
public interface TradeRepository extends JpaRepository<Trade, Long> {

    /** 재수집 때 (구, 계약월) 한 달 치 기존 거래를 불러와서 새로 받은 데이터와 비교해요 */
    List<Trade> findBySggCdAndDealYm(String sggCd, String dealYm);

    /**
     * 건물 상세의 최근 거래. 해제 거래는 빼고 계약일 최신순(같은 날이면 나중에 들어온 순).
     * 화면엔 3건만 보여 주지만 4건을 읽어서, 4번째가 있으면 「더보기」를 띄워요 (개수 쿼리를 따로 안 하려고)
     */
    List<Trade> findTop4ByBuildingIdAndCdealTypeIsNullOrderByDealDateDescIdDesc(Long buildingId);

    /**
     * 실거래 내역(API 8번) 한 쪽. 정렬은 건물 상세와 같게 계약일 최신순 → 같은 날이면 id 역순.
     * id 까지 정렬에 넣어야 같은 날 거래가 쪽 경계에서 겹치거나 빠지지 않아요.
     * Slice 는 size+1 건을 읽어 다음 쪽 여부만 알아내서, 전체 개수 쿼리가 따로 안 나가요 (#16 에서 추가)
     */
    Slice<Trade> findByBuildingIdAndCdealTypeIsNullOrderByDealDateDescIdDesc(Long buildingId, Pageable pageable);

    /**
     * 주변 건물(API 2번)의 latestDeal 후보. 여러 건물의 since 이후 거래를 한 번에 읽어요 (건물마다 쿼리하지 않으려고).
     * 해제 거래 제외, 정렬은 건물 상세와 같게 계약일 최신순 → 같은 날이면 id 역순 — 건물별 첫 건이 latestDeal 이에요 (#35 에서 추가)
     */
    List<Trade> findByBuildingIdInAndCdealTypeIsNullAndDealDateGreaterThanEqualOrderByDealDateDescIdDesc(
            Collection<Long> buildingIds, LocalDate since);

    /** 그 구에서 가장 최근에 거래가 들어온 시각. 수집 이력이 없는 구의 자료 기준일 대체값이에요 (#19 부터) */
    @Query("select max(t.createdAt) from Trade t where t.sggCd = :sggCd")
    LocalDateTime findLatestCollectedAt(@Param("sggCd") String sggCd);

    /** 그 달에 저장된 거래가 있는지 — 국토부가 0건을 보냈을 때 통째 삭제를 막는 데 써요 (#8) */
    boolean existsBySggCdAndDealYm(String sggCd, String dealYm);

    /**
     * 가격 추이(API 13번): 한 건물의 계약월별 금액 합계·건수, 해제 거래 제외 (#26).
     * 평균을 DB 에서 내면 소수 처리가 DB 마다 달라서, 합계·건수만 받아 서비스에서 반올림해요.
     * 거래 없는 달은 결과에 안 나와요 — 서비스가 빈 달을 채워요.
     *
     * @param fromYm 시작 계약월 YYYYMM (포함)
     * @param toYm   끝 계약월 YYYYMM (포함). 문자열 비교지만 YYYYMM 형식이라 달 순서와 같아요
     */
    @Query("""
            select t.dealYm as dealYm, sum(t.dealAmount) as total, count(t) as cnt
            from Trade t
            where t.building.id = :buildingId and t.cdealType is null and t.dealYm between :fromYm and :toYm
            group by t.dealYm
            """)
    List<MonthlyTotal> sumByDealYm(@Param("buildingId") Long buildingId, @Param("fromYm") String fromYm,
            @Param("toYm") String toYm);

    /** sumByDealYm 한 줄 */
    interface MonthlyTotal {
        String getDealYm();

        Long getTotal();

        Long getCnt();
    }

    /**
     * 재수집 비교 범위를 유형별로 (#27). 같은 구·달이라도 아파트 적재 때 오피스텔 거래를,
     * 오피스텔 적재 때 아파트 거래를 "안 온 거래"로 지우면 안 돼서요.
     */
    List<Trade> findBySggCdAndDealYmAndBuilding_BuildingType(String sggCd, String dealYm, BuildingType buildingType);

    boolean existsBySggCdAndDealYmAndBuilding_BuildingType(String sggCd, String dealYm, BuildingType buildingType);
}
