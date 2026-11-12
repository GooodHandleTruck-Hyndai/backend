package com.gooodhandletruck.zippick.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.gooodhandletruck.zippick.domain.trade.client.MolitFixtures;
import com.gooodhandletruck.zippick.domain.trade.client.MolitTradeItem;
import com.gooodhandletruck.zippick.domain.trade.entity.CollectStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 동시 요청 상황 (#45 에서 추가). 실제 앱은 요청을 병렬로 보내고(11/08 프론트 로그: 저장과 상세가 같은 순간),
 * 여러 사람이 같은 건물을 동시에 봐요. 스레드 여러 개가 같은 순간에 출발하도록 CountDownLatch 로 맞춰요.
 * 데이터가 실제로 커밋돼야 다른 스레드가 보므로 테스트 트랜잭션은 쓰지 않아요 (ScenarioSupport 가 표를 비움).
 */
@SpringBootTest(properties = {"zippick.nearby.kakao-chunk-pause-ms=0", "zippick.nearby.kakao-retry-pause-ms=0"})
@AutoConfigureMockMvc
class ConcurrencyScenarioTest extends ScenarioSupport {

    private static final int THREADS = 16;
    private final ExecutorService pool = Executors.newFixedThreadPool(THREADS);

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    @Test
    void 같은_건물을_여러_명이_동시에_열어도_전부_200이고_응답이_같다() throws Exception {
        long id = busiestBuildingId();
        String expected = body("/api/v1/buildings/" + id);

        List<MockHttpServletResponse> results = runTogether(20, i -> response("/api/v1/buildings/" + id));

        results.forEach(r -> assertThat(r.getStatus()).isEqualTo(200));
        for (MockHttpServletResponse r : results) {
            assertThat(r.getContentAsString()).isEqualTo(expected);
        }
    }

    @Test
    void 상세_더보기_추이_주변을_섞어서_동시에_불러도_5xx_가_없다() throws Exception {
        long id = busiestBuildingId();
        String[] urls = {
                "/api/v1/buildings/" + id,
                "/api/v1/buildings/" + id + "/transactions?page=0&size=20",
                "/api/v1/buildings/" + id + "/price-trend",
                nearbyUrl(3000),
        };

        List<MockHttpServletResponse> results = runTogether(32, i -> response(urls[i % urls.length]));

        results.forEach(r -> assertThat(r.getStatus()).isEqualTo(200));
    }

    @Test
    void 저장과_상세가_동시에_나가도_에러_없이_saved_는_true_나_false_이고_끝나면_true() throws Exception {
        long id = busiestBuildingId();
        for (int round = 0; round < 10; round++) {
            String me = registerDevice();
            List<Object> results = runTogether(2, i -> i == 0
                    ? (Object) save(me, id)
                    : response("/api/v1/buildings/" + id, me));

            assertThat(results.get(0)).isEqualTo(201);
            MockHttpServletResponse detail = (MockHttpServletResponse) results.get(1);
            assertThat(detail.getStatus()).isEqualTo(200);
            assertThat(json(detail).get("saved").isBoolean()).isTrue();
            // 둘 다 끝난 뒤에는 저장된 상태로 보여야 해요
            assertThat(getData("/api/v1/buildings/" + id, me).get("saved").asBoolean()).isTrue();
        }
    }

    @Test
    void 재수집이_도는_중에_상세와_추이를_계속_불러도_5xx_가_없다() throws Exception {
        long id = busiestBuildingId();
        // 같은 달을 다시 받되 일부 거래를 해제로 바꿔서 실제로 지우고·고치는 재수집이 일어나게
        List<MolitTradeItem> resent = new ArrayList<>(mapo);
        for (int i = 0; i < resent.size(); i += 10) {
            resent.set(i, MolitFixtures.copy(resent.get(i), Map.of("cdealType", "O", "cdealDay", "26.10.01")));
        }

        CountDownLatch start = new CountDownLatch(1);
        Future<CollectStatus> ingest = pool.submit(() -> {
            start.await();
            return collect(YM, resent).getStatus();
        });
        List<Future<MockHttpServletResponse>> reads = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String url = i % 2 == 0 ? "/api/v1/buildings/" + id : "/api/v1/buildings/" + id + "/price-trend";
            reads.add(pool.submit(() -> {
                start.await();
                return response(url);
            }));
        }
        start.countDown();

        assertThat(ingest.get(30, TimeUnit.SECONDS)).isEqualTo(CollectStatus.SUCCESS);
        for (Future<MockHttpServletResponse> r : reads) {
            assertThat(r.get(30, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void 여러_기기가_동시에_같은_건물을_저장해도_각자_saved_true() throws Exception {
        long id = busiestBuildingId();
        List<String> devices = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            devices.add(registerDevice());
        }

        List<Integer> statuses = runTogether(devices.size(), i -> save(devices.get(i), id));

        assertThat(statuses).containsOnly(201);
        for (String d : devices) {
            assertThat(getData("/api/v1/buildings/" + id, d).get("saved").asBoolean()).isTrue();
        }
    }

    // ---- 도우미 ----

    @FunctionalInterface
    private interface Task<T> {
        T run(int index) throws Exception;
    }

    /** n 개 작업을 같은 순간에 출발시켜 결과를 순서대로 모아요 */
    private <T> List<T> runTogether(int n, Task<T> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int index = i;
            Callable<T> c = () -> {
                start.await();
                return task.run(index);
            };
            futures.add(pool.submit(c));
        }
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) {
            results.add(f.get(30, TimeUnit.SECONDS));
        }
        return results;
    }

    private MockHttpServletResponse response(String url) throws Exception {
        return mockMvc.perform(get(url)).andReturn().getResponse();
    }

    private MockHttpServletResponse response(String url, String anonymousId) throws Exception {
        return mockMvc.perform(get(url).header("X-Anonymous-Id", anonymousId)).andReturn().getResponse();
    }

    private String body(String url) throws Exception {
        return response(url).getContentAsString();
    }

    private JsonNode json(MockHttpServletResponse r) throws Exception {
        return objectMapper.readTree(r.getContentAsString()).get("data");
    }
}
