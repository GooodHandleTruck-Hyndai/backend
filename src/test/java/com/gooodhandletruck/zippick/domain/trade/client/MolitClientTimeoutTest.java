package com.gooodhandletruck.zippick.domain.trade.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 국토부가 느리거나 연결이 안 될 때 제한 시간에 맞춰 멈추는지. (#7 에서 추가)
 *
 * 가짜 서버(MockRestServiceServer)는 실제 네트워크를 안 거쳐서 시간 초과를 못 흉내 내요.
 * 그래서 JDK 기본 HTTP 서버를 테스트 안에서 띄우고 일부러 늦게 응답하게 해요 (의존성 추가 없음).
 * 제한 시간은 짧게(300ms) 잡아서 테스트가 오래 걸리지 않게 해요.
 */
class MolitClientTimeoutTest {

    private static final Duration SHORT = Duration.ofMillis(300);
    /** 제한 시간을 넘겨도 이 안에는 끝나야 해요 (CI 가 느려도 넉넉하게) */
    private static final long MUST_FINISH_WITHIN_MS = 5_000;

    private HttpServer server;
    private ExecutorService executor;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile long page1DelayMs;
    private volatile long page2DelayMs;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow(); // 일부러 잠자는 응답 스레드를 깨워서 정리해요
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        String query = exchange.getRequestURI().getQuery();
        int pageNo = query.contains("pageNo=3") ? 3 : query.contains("pageNo=2") ? 2 : 1;
        sleep(pageNo == 1 ? page1DelayMs : page2DelayMs);
        // 50건씩 나눠 저장한 실제 응답 3쪽을 그대로 돌려줘요
        byte[] body = MolitXmlParserTest.fixture("apt-trade-11440-202608-page" + pageNo + "-of50.xml");
        exchange.getResponseHeaders().add("Content-Type", "application/xml");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        } catch (IOException ignored) {
            // 클라이언트가 시간 초과로 먼저 끊으면 여기서 실패해요 — 정상이에요
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private MolitClient client(String baseUrl, int pageSize, Duration connectTimeout, Duration readTimeout) {
        return new MolitClient(new MolitProperties("secret-key-value", baseUrl, pageSize, 50,
                connectTimeout, readTimeout));
    }

    private String localBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort(); // #27: baseUrl 은 공통 주소, 경로는 MolitDataset 이 붙여요
    }

    @Test
    void 응답이_제한_시간보다_늦으면_기다리지_않고_실패한다() {
        // 서버는 10초 뒤에 응답해요. 처음 실행하는 테스트는 클래스 로딩이 섞여 느릴 수 있어서, 10초와 확실히 구분되는 5초를 기준으로 봐요
        page1DelayMs = 10_000;
        MolitClient client = client(localBaseUrl(), 1000, SHORT, SHORT);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.fetchMonth("11440", "202608"))
                .isInstanceOf(MolitApiException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 10초를 다 기다리지 않고 끝나야 하고, 제한 시간(300ms) 보다 빨리 포기해서도 안 돼요
        assertThat(elapsedMs).isGreaterThanOrEqualTo(SHORT.toMillis() - 50).isLessThan(MUST_FINISH_WITHIN_MS);
    }

    @Test
    void 제한_시간_안에_오면_느려도_성공한다() {
        page1DelayMs = 100;
        page2DelayMs = 100;
        MolitClient client = client(localBaseUrl(), 50, SHORT, Duration.ofMillis(1_500));

        MolitMonthResult result = client.fetchMonth("11440", "202608");

        // 각 쪽이 100ms 씩 늦어도 제한 시간(1.5초) 안이라 3쪽을 다 받아야 해요
        assertThat(result.items()).hasSize(121);
        assertThat(result.isComplete()).isTrue();
        assertThat(requestCount.get()).isEqualTo(3);
    }

    @Test
    void 두_번째_페이지만_늦어도_모은_것을_버리고_실패한다() {
        page1DelayMs = 0;
        page2DelayMs = 3_000;
        MolitClient client = client(localBaseUrl(), 50, SHORT, SHORT);

        assertThatThrownBy(() -> client.fetchMonth("11440", "202608"))
                .isInstanceOf(MolitApiException.class)
                .hasMessageContaining("page=2")
                .hasMessageNotContaining("secret-key-value");
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void 연결이_안_되는_주소면_연결_제한_시간_안에_실패한다() {
        // 10.255.255.1 은 응답이 없는 주소라 연결을 기다리게 돼요. 네트워크에 따라 바로 거절될 수도 있는데,
        // 그 경우에도 "제한 시간 안에 MolitApiException 으로 끝남" 은 같아야 해요
        MolitClient client = client("http://10.255.255.1:81", 1000, SHORT, SHORT);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.fetchMonth("11440", "202608"))
                .isInstanceOf(MolitApiException.class)
                .hasMessageNotContaining("secret-key-value");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(MUST_FINISH_WITHIN_MS);
    }

    @Test
    void 시간_초과_후에도_다시_시도하지_않는다() {
        page1DelayMs = 3_000;
        MolitClient client = client(localBaseUrl(), 1000, SHORT, SHORT);

        assertThatThrownBy(() -> client.fetchMonth("11440", "202608")).isInstanceOf(MolitApiException.class);

        assertThat(requestCount.get()).isEqualTo(1);
    }
}
