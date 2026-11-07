package com.gooodhandletruck.zippick.domain.trade.service;

import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.trade.client.MolitApiException;
import com.gooodhandletruck.zippick.domain.trade.client.MolitClient;
import com.gooodhandletruck.zippick.domain.trade.client.MolitDataset;
import com.gooodhandletruck.zippick.domain.trade.client.MolitMonthResult;
import com.gooodhandletruck.zippick.domain.trade.entity.CollectRun;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import com.gooodhandletruck.zippick.domain.trade.repository.CollectRunRepository;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 국토부 한 달 치 거래를 DB 에 반영 (API 목록 3번의 저장 부분). 5번·12번 배치가 collectMonth 를 불러요. (#8 에서 추가)
 *
 * 순서: 받기(#7) → 안전 확인(건수 일치, 기존 달에 0건이면 건너뜀) → 값 정리·지문(MolitTradeNormalizer) → 한 트랜잭션 안에서 월 단위 비교
 * (있는 거래는 바뀌는 4칸만 수정 / 새 거래 추가 / 안 온 거래 삭제) → 수집 이력(CollectRun) 저장.
 * #27 변경: 유형(아파트·오피스텔)별로 받고, 비교·삭제 범위도 (구, 계약월, 유형) 으로 좁혀요.
 * 어느 단계든 실패하면 그 달 DB 는 그대로 두고 이력에 FAILED·SKIPPED 로 남겨요. 다시 시도는 다음 배치 회차가 해요.
 */
@Slf4j
@Service
public class TradeIngestService {

    private final MolitClient molitClient;
    private final TradeRepository tradeRepository;
    private final BuildingRepository buildingRepository;
    private final CollectRunRepository collectRunRepository;
    private final TransactionTemplate transactionTemplate;

    public TradeIngestService(MolitClient molitClient, TradeRepository tradeRepository,
            BuildingRepository buildingRepository, CollectRunRepository collectRunRepository,
            PlatformTransactionManager transactionManager) {
        this.molitClient = molitClient;
        this.tradeRepository = tradeRepository;
        this.buildingRepository = buildingRepository;
        this.collectRunRepository = collectRunRepository;
        // @Transactional 대신 직접 트랜잭션을 열어요: 반영이 롤백돼도 수집 이력은 따로 저장돼야 해서,
        // "반영(롤백 가능)"과 "이력 저장"을 한 메서드 안에서 나눠 다루려고요
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * (구, 계약월) 한 달 치를 받아 반영하고 수집 이력을 돌려줘요. 예외를 던지지 않아요 — 결과는 이력의 status 로 봐요.
     */
    public CollectRun collectMonth(String sggCd, String dealYm) {
        return collectMonth(BuildingType.APT, sggCd, dealYm);
    }

    /** 유형별 한 달 치 (#27). 아파트·오피스텔은 국토부 API 가 달라서 따로 받아요 */
    public CollectRun collectMonth(BuildingType type, String sggCd, String dealYm) {
        CollectRun run = CollectRun.start(type, sggCd, dealYm);
        try {
            MolitMonthResult result = molitClient.fetchMonth(MolitDataset.of(type), sggCd, dealYm);
            run.fetched(result.totalCount(), result.items().size());

            // 안전 확인: 일부만 받은 채 반영하면 실제로 있는 거래가 "안 온 거래"로 지워져요
            if (!result.isComplete()) {
                run.skip("받은 건수(" + result.items().size() + ")가 totalCount(" + result.totalCount() + ")와 달라요");
                return collectRunRepository.save(run);
            }
            // 오류 없이 0건이 와도, 이미 거래가 있는 달이면 반영하지 않아요. 그대로 반영하면 그 달이 통째로 지워지는데,
            // 한 구의 한 달 거래가 전부 취소되는 일보다 국토부 쪽 일시 이상일 가능성이 훨씬 커요
            if (result.items().isEmpty()
                    && tradeRepository.existsBySggCdAndDealYmAndBuilding_BuildingType(sggCd, dealYm, type)) {
                run.skip("국토부가 0건을 보냈는데 DB 에는 이 달 거래가 있어요 — 통째 삭제를 막으려고 건너뛰어요");
                return collectRunRepository.save(run);
            }

            List<NormalizedTrade> rows = MolitTradeNormalizer.normalize(type, sggCd, dealYm, result.items());
            Counts counts = transactionTemplate.execute(status -> apply(type, sggCd, dealYm, rows));
            run.succeed(counts.inserted(), counts.updated(), counts.deleted());
        } catch (MolitApiException e) {
            run.fail(e.getResultCode(), e.getMessage());
        } catch (RuntimeException e) {
            // 값 이상(IllegalArgumentException)·DB 오류(DataAccessException)·커밋 실패(TransactionException) 등.
            // 트랜잭션이 롤백돼서 그 달은 손대기 전 상태 그대로고, 배치가 다음 달로 넘어갈 수 있게 던지지 않고 이력에 남겨요
            run.fail(null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        if (run.getMessage() != null) {
            log.warn("실거래 수집 {} type={} sggCd={} dealYm={}: {}", run.getStatus(), type, sggCd, dealYm, run.getMessage());
        }
        return collectRunRepository.save(run);
    }

    /** 한 달 치 비교·반영. 트랜잭션 안에서만 불러요 */
    private Counts apply(BuildingType type, String sggCd, String dealYm, List<NormalizedTrade> rows) {
        Map<String, Trade> existingByKey = new HashMap<>();
        // 같은 유형만 비교해요 — 다른 유형 거래는 이번 응답에 없는 게 당연해서 지우면 안 돼요
        for (Trade trade : tradeRepository.findBySggCdAndDealYmAndBuilding_BuildingType(sggCd, dealYm, type)) {
            existingByKey.put(trade.getFingerprint() + "#" + trade.getSeq(), trade);
        }

        Map<BuildingKey, Building> buildings = new LinkedHashMap<>(); // 같은 건물을 매번 DB 에서 찾지 않게
        List<Trade> toInsert = new ArrayList<>();
        int updated = 0;
        for (NormalizedTrade row : rows) {
            Trade existing = existingByKey.remove(row.tradeKey());
            if (existing == null) {
                toInsert.add(newTrade(row, buildings.computeIfAbsent(BuildingKey.of(row), key -> findOrCreate(type, key))));
            } else if (changed(existing, row)) {
                existing.updateChangeableFields(row.aptDong(), row.cdealType(), row.cdealDay(), row.rgstDate());
                updated++;
            }
        }

        // 남은 것 = 이번에 국토부가 안 보낸 거래 (신고 취소·정정 등)
        List<Trade> toDelete = new ArrayList<>(existingByKey.values());
        tradeRepository.deleteAll(toDelete);
        tradeRepository.saveAll(toInsert);
        // DB 오류(길이 초과 등)는 커밋 때 나도 트랜잭션 전체가 롤백돼요 (변형 검사로 flush 없이도 롤백 확인)
        return new Counts(toInsert.size(), updated, toDelete.size());
    }

    private Building findOrCreate(BuildingType type, BuildingKey key) {
        return buildingRepository
                .findByBuildingTypeAndSggCdAndUmdNmAndJibunAndAptNm(type, key.sggCd(), key.umdNm(),
                        key.jibun(), key.aptNm())
                .orElseGet(() -> buildingRepository.save(Building.builder()
                        .buildingType(type)
                        .sggCd(key.sggCd())
                        .umdNm(key.umdNm())
                        .jibun(key.jibun())
                        .aptNm(key.aptNm())
                        .buildYear(key.buildYear())
                        .build()));
    }

    private static Trade newTrade(NormalizedTrade row, Building building) {
        return Trade.builder()
                .building(building)
                .fingerprint(row.fingerprint())
                .seq(row.seq())
                .sggCd(row.sggCd())
                .dealYm(row.dealYm())
                .dealDate(row.dealDate())
                .dealAmount(row.dealAmount())
                .excluUseAr(row.excluUseAr())
                .floor(row.floor())
                .aptDong(row.aptDong())
                .cdealType(row.cdealType())
                .cdealDay(row.cdealDay())
                .rgstDate(row.rgstDate())
                .dealingGbn(row.dealingGbn())
                .estateAgentSggNm(row.estateAgentSggNm())
                .slerGbn(row.slerGbn())
                .buyerGbn(row.buyerGbn())
                .landLeaseholdGbn(row.landLeaseholdGbn())
                .build();
    }

    /** 바뀌는 4칸 중 하나라도 달라졌는지 — 안 바뀐 거래까지 수정으로 세지 않으려고 */
    private static boolean changed(Trade trade, NormalizedTrade row) {
        return !Objects.equals(trade.getAptDong(), row.aptDong())
                || !Objects.equals(trade.getCdealType(), row.cdealType())
                || !Objects.equals(trade.getCdealDay(), row.cdealDay())
                || !Objects.equals(trade.getRgstDate(), row.rgstDate());
    }

    private record Counts(int inserted, int updated, int deleted) {
    }

    /** 건물 구분 키 (유형은 APT 고정). 준공년도는 키가 아니지만 새 건물을 만들 때 쓰려고 같이 들고 다녀요 */
    private record BuildingKey(String sggCd, String umdNm, String jibun, String aptNm, Integer buildYear) {

        static BuildingKey of(NormalizedTrade row) {
            return new BuildingKey(row.sggCd(), row.umdNm(), row.jibun(), row.aptNm(), row.buildYear());
        }

        // 준공년도가 거래마다 다르게 와도 같은 건물로 묶이게 비교에서 빼요
        @Override
        public boolean equals(Object o) {
            return o instanceof BuildingKey k && sggCd.equals(k.sggCd) && umdNm.equals(k.umdNm)
                    && jibun.equals(k.jibun) && aptNm.equals(k.aptNm);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sggCd, umdNm, jibun, aptNm);
        }
    }
}
