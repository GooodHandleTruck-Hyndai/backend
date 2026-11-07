package com.gooodhandletruck.zippick.domain.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.trade.client.MolitApiException;
import com.gooodhandletruck.zippick.domain.trade.client.MolitClient;
import com.gooodhandletruck.zippick.domain.trade.client.MolitFixtures;
import com.gooodhandletruck.zippick.domain.trade.client.MolitMonthResult;
import com.gooodhandletruck.zippick.domain.trade.client.MolitTradeItem;
import com.gooodhandletruck.zippick.domain.trade.entity.CollectRun;
import com.gooodhandletruck.zippick.domain.trade.entity.CollectStatus;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import com.gooodhandletruck.zippick.domain.trade.repository.CollectRunRepository;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import com.gooodhandletruck.zippick.global.config.JpaConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실거래 적재 서비스 (#8 에서 추가). 국토부 대신 가짜 클라이언트가 실제 마포구 2026-08 응답(121건)을 돌려줘요.
 *
 * 테스트를 트랜잭션으로 감싸면 서비스의 "한 달 = 트랜잭션 하나" 와 롤백을 확인할 수 없어서
 * 테스트 트랜잭션을 끄고(NOT_SUPPORTED), 테스트마다 표를 직접 비워요.
 * H2(MySQL 모드) 로 돌아요. 실제 MySQL 적재는 로컬 실행으로 따로 확인해요.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TradeIngestServiceTest {

    private static final String SGG = "11440";
    private static final String YM = "202608";

    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private CollectRunRepository collectRunRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private MolitClient molitClient;
    private TradeIngestService service;
    private List<MolitTradeItem> mapo;

    @BeforeEach
    void setUp() {
        molitClient = mock(MolitClient.class);
        service = new TradeIngestService(molitClient, tradeRepository, buildingRepository, collectRunRepository,
                transactionManager);
        mapo = MolitFixtures.mapo202608().items();
    }

    @AfterEach
    void cleanUp() {
        tradeRepository.deleteAllInBatch(); // 거래가 건물을 가리켜서 거래부터
        buildingRepository.deleteAllInBatch();
        collectRunRepository.deleteAllInBatch();
    }

    /** 국토부가 이 목록을 보낸 것처럼 (totalCount = 건수) */
    private CollectRun collect(List<MolitTradeItem> items) {
        return collect(MolitFixtures.result(SGG, YM, items.size(), items));
    }

    private CollectRun collect(MolitMonthResult result) {
        when(molitClient.fetchMonth(result.sggCd(), result.dealYm())).thenReturn(result);
        return service.collectMonth(result.sggCd(), result.dealYm());
    }

    private CollectRun firstLoad() {
        CollectRun run = collect(mapo);
        assertThat(run.getStatus()).isEqualTo(CollectStatus.SUCCESS);
        return run;
    }

    private List<Trade> trades() {
        return tradeRepository.findBySggCdAndDealYm(SGG, YM).stream()
                .sorted(Comparator.comparing(Trade::getId)).toList();
    }

    /** 해제 표시가 없는 실제 거래 하나 (해제 반영 테스트용) */
    private int indexOfNormalTrade() {
        for (int i = 0; i < mapo.size(); i++) {
            if (mapo.get(i).cdealType() == null || mapo.get(i).cdealType().isBlank()) {
                return i;
            }
        }
        throw new IllegalStateException("픽스처에 정상 거래가 없어요");
    }

    private static void assertCounts(CollectRun run, int inserted, int updated, int deleted) {
        assertThat(run.getInsertedCount()).as("추가").isEqualTo(inserted);
        assertThat(run.getUpdatedCount()).as("수정").isEqualTo(updated);
        assertThat(run.getDeletedCount()).as("삭제").isEqualTo(deleted);
    }

    // ---- 정상 ----

    @Test
    void 처음_적재하면_121건과_건물_77개가_생긴다() {
        CollectRun run = firstLoad();

        assertCounts(run, 121, 0, 0);
        assertThat(run.getTotalCount()).isEqualTo(121);
        assertThat(run.getFetchedCount()).isEqualTo(121);
        assertThat(run.getResultCode()).isEqualTo("000");
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(tradeRepository.count()).isEqualTo(121);
        assertThat(buildingRepository.count()).isEqualTo(77); // 실제 응답의 (동, 지번, 단지명) 조합 수
        assertThat(collectRunRepository.count()).isEqualTo(1);
    }

    @Test
    void 해제_거래도_저장한다() {
        // 화면 조회에서만 빼요 (cdeal_type IS NULL). 저장은 해야 재수집 때 같은 거래로 찾아요
        firstLoad();

        assertThat(trades()).filteredOn(t -> "O".equals(t.getCdealType())).hasSize(7);
    }

    @Test
    void 값이_같은_진짜_거래는_순번_2로_둘_다_저장한다() {
        firstLoad();

        assertThat(trades()).filteredOn(t -> t.getSeq() == 2).hasSize(6);
    }

    @Test
    void 소수_4자리_면적을_그대로_저장한다() {
        firstLoad();

        assertThat(trades()).extracting(Trade::getExcluUseAr)
                .anySatisfy(a -> assertThat(a).isEqualByComparingTo("59.9236"));
    }

    @Test
    void 같은_응답을_다시_적재하면_아무것도_바뀌지_않는다() {
        firstLoad();
        List<Trade> before = trades();

        CollectRun again = collect(mapo);

        assertThat(again.getStatus()).isEqualTo(CollectStatus.SUCCESS);
        assertCounts(again, 0, 0, 0);
        assertThat(trades()).extracting(Trade::getId).containsExactlyElementsOf(before.stream().map(Trade::getId).toList());
        assertThat(buildingRepository.count()).isEqualTo(77);
        assertThat(collectRunRepository.count()).isEqualTo(2);
    }

    // ---- 재수집 비교 ----

    @Test
    void 해제_표시가_생기면_그_거래만_수정하고_처음_수집_시각은_그대로다() {
        firstLoad();
        int i = indexOfNormalTrade();
        Trade target = trades().get(i);
        LocalDateTime firstSeen = target.getCreatedAt();

        List<MolitTradeItem> changed = new ArrayList<>(mapo);
        changed.set(i, MolitFixtures.copy(mapo.get(i), Map.of("cdealType", "O", "cdealDay", "26.11.05")));
        CollectRun run = collect(changed);

        assertCounts(run, 0, 1, 0);
        Trade after = tradeRepository.findById(target.getId()).orElseThrow();
        assertThat(after.getCdealType()).isEqualTo("O");
        assertThat(after.getCdealDay()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(after.getCreatedAt()).isEqualTo(firstSeen); // 17번 새 신고 감지가 이 값을 써요
    }

    @Test
    void 국토부에서_사라진_거래는_지우고_새_거래는_추가한다() {
        firstLoad();
        Trade removed = trades().get(3);
        // 같은 건물(첫 거래 단지)에 새 거래 하나
        MolitTradeItem added = MolitFixtures.copy(mapo.get(0), Map.of("dealDay", "30", "dealAmount", "141,357", // 실제 응답에 없는 금액
                "cdealType", " ", "cdealDay", " "));

        List<MolitTradeItem> next = new ArrayList<>(mapo);
        next.remove(3);
        next.add(added);
        CollectRun run = collect(next);

        assertCounts(run, 1, 0, 1);
        assertThat(tradeRepository.findById(removed.getId())).isEmpty();
        assertThat(tradeRepository.count()).isEqualTo(121);
        assertThat(buildingRepository.count()).isEqualTo(77); // 이미 있는 건물을 다시 쓰고 새로 만들지 않아요
        assertThat(trades()).filteredOn(t -> t.getDealAmount() == 141357).singleElement()
                // 건물은 지연 로딩이라 트랜잭션 밖에서는 id 로 다시 읽어요
                .satisfies(t -> assertThat(buildingRepository.findById(t.getBuilding().getId()).orElseThrow()
                        .getAptNm()).isEqualTo("상암월드컵파크7단지"));
    }

    @Test
    void 다른_달_거래는_건드리지_않는다() {
        firstLoad();
        MolitTradeItem july = MolitFixtures.copy(mapo.get(0), Map.of("dealMonth", "7"));

        CollectRun run = collect(MolitFixtures.result(SGG, "202607", 1, List.of(july)));

        assertCounts(run, 1, 0, 0);
        assertThat(tradeRepository.findBySggCdAndDealYm(SGG, YM)).hasSize(121);
        assertThat(tradeRepository.findBySggCdAndDealYm(SGG, "202607")).hasSize(1);
    }

    // ---- 반영하지 않는 경우: DB 그대로 + 이력 ----

    @Test
    void 받은_건수가_totalCount_와_다르면_건너뛰고_아무것도_지우지_않는다() {
        firstLoad();

        CollectRun run = collect(MolitFixtures.result(SGG, YM, 121, mapo.subList(0, 100)));

        assertThat(run.getStatus()).isEqualTo(CollectStatus.SKIPPED);
        assertThat(run.getTotalCount()).isEqualTo(121);
        assertThat(run.getFetchedCount()).isEqualTo(100);
        assertCounts(run, 0, 0, 0);
        assertThat(tradeRepository.count()).isEqualTo(121);
    }

    @Test
    void 거래가_있는_달에_0건이_오면_건너뛴다() {
        firstLoad();

        CollectRun run = collect(List.of());

        assertThat(run.getStatus()).isEqualTo(CollectStatus.SKIPPED);
        assertThat(tradeRepository.count()).isEqualTo(121);
    }

    @Test
    void 거래가_없는_달에_0건이_오면_성공이다() {
        CollectRun run = collect(List.of());

        assertThat(run.getStatus()).isEqualTo(CollectStatus.SUCCESS);
        assertCounts(run, 0, 0, 0);
    }

    @Test
    void 국토부_호출이_실패하면_결과_코드와_함께_실패로_남긴다() {
        firstLoad();
        when(molitClient.fetchMonth(SGG, YM)).thenThrow(new MolitApiException("국토부 API 결과 코드 03: NO_DATA", "03"));

        CollectRun run = service.collectMonth(SGG, YM);

        assertThat(run.getStatus()).isEqualTo(CollectStatus.FAILED);
        assertThat(run.getResultCode()).isEqualTo("03");
        assertThat(run.getMessage()).contains("NO_DATA");
        assertThat(run.getTotalCount()).isNull();
        assertThat(tradeRepository.count()).isEqualTo(121);
        assertThat(collectRunRepository.count()).isEqualTo(2);
    }

    @Test
    void 값이_이상한_거래가_하나라도_있으면_그_달_전체를_반영하지_않는다() {
        firstLoad();
        List<MolitTradeItem> next = new ArrayList<>(mapo);
        next.remove(3); // 반영됐다면 지워졌을 거래
        next.set(10, MolitFixtures.copy(mapo.get(10), Map.of("dealAmount", "가격없음")));

        CollectRun run = collect(next);

        assertThat(run.getStatus()).isEqualTo(CollectStatus.FAILED);
        assertThat(run.getMessage()).contains("번째 거래");
        assertThat(tradeRepository.count()).isEqualTo(121);
    }

    @Test
    void 반영_중_DB_오류가_나면_그_달_전체가_롤백된다() {
        firstLoad();
        int i = indexOfNormalTrade();
        Trade toBeUpdated = trades().get(i);
        Trade toBeDeleted = trades().get(3);

        List<MolitTradeItem> next = new ArrayList<>(mapo);
        next.set(i, MolitFixtures.copy(mapo.get(i), Map.of("cdealType", "O", "cdealDay", "26.11.05"))); // 수정 예정
        next.remove(3); // 삭제 예정 (i 보다 뒤를 바꾸지 않게 set 다음에 지워요)
        // 새 건물 + 새 거래인데, 중개사 소재지가 trades.estate_agent_sgg_nm 길이(200)를 넘어서 저장이 실패해요
        next.add(MolitFixtures.copy(mapo.get(0), Map.of("aptNm", "새로생긴단지", "estateAgentSggNm", "가".repeat(201))));

        CollectRun run = collect(next);

        assertThat(run.getStatus()).isEqualTo(CollectStatus.FAILED);
        assertThat(tradeRepository.count()).isEqualTo(121);
        assertThat(tradeRepository.findById(toBeDeleted.getId())).isPresent();
        assertThat(tradeRepository.findById(toBeUpdated.getId()).orElseThrow().getCdealType()).isNull();
        assertThat(buildingRepository.count()).isEqualTo(77); // 새 건물도 같이 롤백
        assertThat(collectRunRepository.count()).isEqualTo(2); // 실패해도 이력은 남아요
    }

    @Test
    void 실패_이유가_길어도_이력_저장은_성공한다() {
        when(molitClient.fetchMonth(SGG, YM)).thenThrow(new MolitApiException("x".repeat(2000), (String) null));

        CollectRun run = service.collectMonth(SGG, YM);

        assertThat(run.getId()).isNotNull();
        assertThat(run.getMessage()).hasSize(500);
    }

    @Test
    void 이미_있는_건물이면_새로_만들지_않는다() {
        // 다른 경로(예: 다른 달 적재)로 먼저 생긴 건물을 같은 건물 구분 키로 찾아 써요
        buildingRepository.save(TestFixtures.building()
                .umdNm("상암동").jibun("1660").aptNm("상암월드컵파크7단지").buildYear(2005).build());

        firstLoad();

        assertThat(buildingRepository.count()).isEqualTo(77);
    }
}
