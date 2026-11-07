package com.gooodhandletruck.zippick.domain.trade.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 국토부 클라이언트 테스트. 실제 국토부 대신 가짜 서버(MockRestServiceServer)가 저장해 둔 실제 응답을 돌려줘요. (#7 에서 추가)
 * 실제 국토부 연결은 이 테스트로 확인하지 않아요 (로컬에서 키로 따로 확인).
 */
class MolitClientTest {

    private static final String BASE = "https://molit.test"; // #27: 공통 주소, 경로는 MolitDataset
    private static final String KEY = "test-key";

    private MockRestServiceServer server;

    private MolitClient client(String serviceKey, int pageSize, int maxPages) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MolitProperties properties = new MolitProperties(serviceKey, BASE, pageSize, maxPages,
                Duration.ofSeconds(5), Duration.ofSeconds(30));
        return new MolitClient(builder, properties);
    }

    private void expectPage(int pageNo, byte[] body) {
        server.expect(once(), requestTo(containsString("pageNo=" + pageNo)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_XML));
    }

    // ---- 정상 ----

    @Test
    void 오피스텔은_오피스텔_API_경로로_부르고_건물명은_offiNm_에서_읽는다() {
        MolitClient client = client(KEY, 1000, 50);
        server.expect(once(), requestTo(containsString(BASE + "/RTMSDataSvcOffiTrade/getRTMSDataSvcOffiTrade?")))
                .andRespond(withSuccess(MolitXmlParserTest.fixture("offi-trade-11440-202608.xml"),
                        MediaType.APPLICATION_XML));

        MolitMonthResult result = client.fetchMonth(MolitDataset.OFFICETEL, "11440", "202608");

        server.verify();
        assertThat(result.items()).hasSize(54);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.items().get(0).aptNm()).isEqualTo("마포 한화 오벨리스크");
        assertThat(result.items().get(0).aptDong()).isNull(); // 오피스텔엔 동 태그가 없어요
    }

    @Test
    void 한_달_치를_한_번에_받는다() {
        MolitClient client = client(KEY, 1000, 50);
        server.expect(once(), requestTo(containsString(BASE + "/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade?")))
                .andExpect(queryParam("serviceKey", KEY))
                .andExpect(queryParam("LAWD_CD", "11440"))
                .andExpect(queryParam("DEAL_YMD", "202608"))
                .andExpect(queryParam("numOfRows", "1000"))
                .andExpect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(MolitXmlParserTest.fixture("apt-trade-11440-202608.xml"),
                        MediaType.APPLICATION_XML));

        MolitMonthResult result = client.fetchMonth("11440", "202608");

        server.verify();
        assertThat(result.sggCd()).isEqualTo("11440");
        assertThat(result.dealYm()).isEqualTo("202608");
        assertThat(result.totalCount()).isEqualTo(121);
        assertThat(result.items()).hasSize(121);
        assertThat(result.isComplete()).isTrue();
    }

    @Test
    void 여러_페이지를_차례로_받아_순서대로_모은다() {
        MolitClient client = client(KEY, 50, 50);
        expectPage(1, MolitXmlParserTest.fixture("apt-trade-11440-202608-page1-of50.xml"));
        expectPage(2, MolitXmlParserTest.fixture("apt-trade-11440-202608-page2-of50.xml"));
        expectPage(3, MolitXmlParserTest.fixture("apt-trade-11440-202608-page3-of50.xml"));

        MolitMonthResult result = client.fetchMonth("11440", "202608");

        server.verify();
        assertThat(result.items()).hasSize(121);
        assertThat(result.isComplete()).isTrue();
        // 나눠 받은 결과가 한 번에 받은 것과 같은 순서·같은 내용이어야 순번(seq)이 흔들리지 않아요
        List<MolitTradeItem> whole = MolitXmlParser.parse(
                MolitXmlParserTest.fixture("apt-trade-11440-202608.xml")).items();
        assertThat(result.items()).containsExactlyElementsOf(whole);
    }

    @Test
    void 다_받으면_더_요청하지_않는다() {
        MolitClient client = client(KEY, 1000, 50);
        expectPage(1, MolitXmlParserTest.fixture("apt-trade-11440-202608.xml"));

        client.fetchMonth("11440", "202608");

        server.verify(); // 2쪽 요청이 있으면 여기서 실패해요
    }

    @Test
    void 거래가_없는_달은_한_번만_요청하고_빈_결과다() {
        MolitClient client = client(KEY, 1000, 50);
        expectPage(1, MolitXmlParserTest.fixture("apt-trade-empty.xml"));

        MolitMonthResult result = client.fetchMonth("11440", "202801");

        server.verify();
        assertThat(result.items()).isEmpty();
        assertThat(result.totalCount()).isZero();
        assertThat(result.isComplete()).isTrue();
    }

    @Test
    void 건수가_모자란데_빈_페이지가_오면_멈추고_미완료로_알려준다() {
        // 이 결과를 반영할지는 적재 서비스(#8)의 안전 확인이 정해요. 클라이언트는 끝없이 돌지만 않으면 돼요
        MolitClient client = client(KEY, 50, 50);
        expectPage(1, MolitXmlParserTest.fixture("apt-trade-11440-202608-page1-of50.xml"));
        expectPage(2, okXml("121", ""));

        MolitMonthResult result = client.fetchMonth("11440", "202608");

        server.verify();
        assertThat(result.items()).hasSize(50);
        assertThat(result.isComplete()).isFalse();
    }

    @Test
    void 페이지_상한을_넘으면_실패한다() {
        MolitClient client = client(KEY, 50, 2);
        expectPage(1, MolitXmlParserTest.fixture("apt-trade-11440-202608-page1-of50.xml"));
        expectPage(2, MolitXmlParserTest.fixture("apt-trade-11440-202608-page2-of50.xml"));

        assertThatThrownBy(() -> client.fetchMonth("11440", "202608"))
                .isInstanceOf(MolitApiException.class).hasMessageContaining("2쪽");
        server.verify(); // 3쪽은 요청하지 않아야 해요
    }

    @ParameterizedTest(name = "계약월 {0} 허용")
    @ValueSource(strings = {"202601", "202612", "200601"})
    void 계약월_경계값을_그대로_보낸다(String dealYm) {
        MolitClient client = client(KEY, 1000, 50);
        server.expect(once(), queryParam("DEAL_YMD", dealYm))
                .andRespond(withSuccess(MolitXmlParserTest.fixture("apt-trade-empty.xml"), MediaType.APPLICATION_XML));

        client.fetchMonth("11440", dealYm);

        server.verify();
    }

    // ---- 서비스 키 ----

    @Test
    void 키의_특수문자를_정확히_인코딩해서_보낸다() {
        // 공공데이터포털 키에 + / = 가 섞이면, 그대로 보내면 + 가 공백으로 읽혀 인증이 실패해요
        MolitClient client = client("ab+cd/ef==", 1000, 50);
        server.expect(once(), requestTo(containsString("serviceKey=ab%2Bcd%2Fef%3D%3D&")))
                .andRespond(withSuccess(MolitXmlParserTest.fixture("apt-trade-empty.xml"), MediaType.APPLICATION_XML));

        client.fetchMonth("11440", "202608");

        server.verify();
    }

    @ParameterizedTest(name = "키 [{0}] 이면 요청하지 않고 실패")
    @NullSource
    @ValueSource(strings = {"", "  "})
    void 키가_없으면_요청하지_않고_실패한다(String key) {
        MolitClient client = client(key, 1000, 50);

        assertThatThrownBy(() -> client.fetchMonth("11440", "202608"))
                .isInstanceOf(MolitApiException.class).hasMessageContaining("MOLIT_SERVICE_KEY");
        server.verify(); // 요청이 하나도 없어야 해요
    }

    // ---- 실패 ----

    @ParameterizedTest(name = "HTTP {0} 이면 실패")
    @ValueSource(ints = {400, 401, 403, 404, 429, 500, 502, 503})
    void HTTP_오류면_실패하고_키를_메시지에_남기지_않는다(int status) {
        MolitClient client = client("secret-key-value", 1000, 50);
        server.expect(once(), requestTo(containsString("pageNo=1")))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("error"));

        assertThatThrownBy(() -> client.fetchMonth("11440", "202608"))
                .isInstanceOf(MolitApiException.class)
                .hasMessageContaining("11440")
                .hasMessageNotContaining("secret-key-value");
    }

    @Test
    void 정상_HTTP_라도_인증키_오류_XML_이면_실패한다() {
        // 공공데이터포털은 인증키 오류를 HTTP 200 + OpenAPI_ServiceResponse XML 로 보내요
        MolitClient client = client(KEY, 1000, 50);
        expectPage(1, MolitXmlParserTest.fixture("apt-trade-error-invalid-key.xml"));

        assertThatThrownBy(() -> client.fetchMonth("11440", "202608"))
                .isInstanceOf(MolitApiException.class)
                .extracting(e -> ((MolitApiException) e).getResultCode()).isEqualTo("30");
    }

    @Test
    void 두_번째_페이지에서_실패하면_모은_것도_버리고_실패한다() {
        // 반쪽짜리 결과로 DB 를 고치면 안 돼서, 중간 실패는 결과를 돌려주지 않아요
        MolitClient client = client(KEY, 50, 50);
        expectPage(1, MolitXmlParserTest.fixture("apt-trade-11440-202608-page1-of50.xml"));
        server.expect(once(), requestTo(containsString("pageNo=2"))).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchMonth("11440", "202608")).isInstanceOf(MolitApiException.class);
    }

    @Test
    void 실패해도_다시_시도하지_않는다() {
        // 재시도는 배치 다음 회차 몫이라, 한 번 실패하면 바로 멈춰야 해요
        MolitClient client = client(KEY, 1000, 50);
        server.expect(once(), requestTo(containsString("pageNo=1"))).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchMonth("11440", "202608")).isInstanceOf(MolitApiException.class);
        server.verify();
    }

    // ---- 입력 검사 ----

    @ParameterizedTest(name = "구 코드 [{0}] 거부")
    @NullSource
    @ValueSource(strings = {"", "1144", "114400", "1144a", " 11440"})
    void 구_코드가_숫자_5자리가_아니면_요청하지_않는다(String sggCd) {
        MolitClient client = client(KEY, 1000, 50);

        assertThatThrownBy(() -> client.fetchMonth(sggCd, "202608")).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @ParameterizedTest(name = "계약월 [{0}] 거부")
    @NullSource
    @ValueSource(strings = {"", "2026", "2026-08", "202600", "202613", "20260801", "abcdef"})
    void 계약월이_YYYYMM_이_아니면_요청하지_않는다(String dealYm) {
        MolitClient client = client(KEY, 1000, 50);

        assertThatThrownBy(() -> client.fetchMonth("11440", dealYm)).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    // ---- 도우미 ----

    private static byte[] okXml(String totalCount, String items) {
        return ("<response><header><resultCode>000</resultCode></header><body><items>" + items
                + "</items><totalCount>" + totalCount + "</totalCount></body></response>")
                .getBytes(StandardCharsets.UTF_8);
    }
}
