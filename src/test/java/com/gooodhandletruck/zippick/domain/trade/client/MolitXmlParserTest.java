package com.gooodhandletruck.zippick.domain.trade.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 국토부 XML 읽기 테스트. (#7 에서 추가)
 * src/test/resources/molit/*.xml 은 2026-11-06 마포구(11440) 실제 응답을 그대로 저장한 거예요 (서비스 키는 응답에 없음).
 */
class MolitXmlParserTest {

    static byte[] fixture(String name) {
        try (InputStream in = MolitXmlParserTest.class.getResourceAsStream("/molit/" + name)) {
            return in.readAllBytes();
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("테스트 파일이 없어요: " + name, e);
        }
    }

    private static byte[] xml(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static String okResponse(String totalCount, String items) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?><response><header><resultCode>000</resultCode>"
                + "<resultMsg>OK</resultMsg></header><body><items>" + items + "</items><numOfRows>1000</numOfRows>"
                + "<pageNo>1</pageNo>" + totalCount + "</body></response>";
    }

    // ---- 실제 응답 ----

    @Test
    void 실제_응답_한_달_치를_모두_읽는다() {
        MolitXmlParser.Page page = MolitXmlParser.parse(fixture("apt-trade-11440-202608.xml"));

        assertThat(page.totalCount()).isEqualTo(121);
        assertThat(page.items()).hasSize(121);
    }

    @Test
    void 실제_응답의_첫_거래를_받은_문자열_그대로_담는다() {
        MolitTradeItem first = MolitXmlParser.parse(fixture("apt-trade-11440-202608.xml")).items().get(0);

        assertThat(first.sggCd()).isEqualTo("11440");
        assertThat(first.umdNm()).isEqualTo("상암동");
        assertThat(first.jibun()).isEqualTo("1660");
        assertThat(first.aptNm()).isEqualTo("상암월드컵파크7단지");
        assertThat(first.aptDong()).isEqualTo(" "); // 빈 값은 공백 한 칸으로 와요 — 정리는 #8 에서
        assertThat(first.buildYear()).isEqualTo("2005");
        assertThat(first.dealYear()).isEqualTo("2026");
        assertThat(first.dealMonth()).isEqualTo("8"); // 0 채움 없음
        assertThat(first.dealDay()).isEqualTo("29");
        assertThat(first.dealAmount()).isEqualTo("139,000"); // 쉼표 포함 문자열
        assertThat(first.excluUseAr()).isEqualTo("84.45");
        assertThat(first.floor()).isEqualTo("2");
        assertThat(first.cdealType()).isEqualTo("O");
        assertThat(first.cdealDay()).isEqualTo("26.10.07");
        assertThat(first.rgstDate()).isEqualTo(" ");
        assertThat(first.dealingGbn()).isEqualTo("중개거래");
        assertThat(first.estateAgentSggNm()).isEqualTo("서울 마포구");
        assertThat(first.slerGbn()).isEqualTo("개인");
        assertThat(first.buyerGbn()).isEqualTo("개인");
        assertThat(first.landLeaseholdGbn()).isEqualTo("N");
    }

    @Test
    void 실제_응답의_모든_거래에_20개_필드가_다_있다() {
        List<MolitTradeItem> items = MolitXmlParser.parse(fixture("apt-trade-11440-202608.xml")).items();

        assertThat(items).allSatisfy(item -> assertThat(item).hasNoNullFieldsOrProperties());
    }

    @Test
    void 실제_응답의_해제_거래_7건을_그대로_담는다() {
        List<MolitTradeItem> items = MolitXmlParser.parse(fixture("apt-trade-11440-202608.xml")).items();

        assertThat(items).filteredOn(item -> "O".equals(item.cdealType().trim())).hasSize(7)
                .allSatisfy(item -> assertThat(item.cdealDay()).matches("\\d{2}\\.\\d{2}\\.\\d{2}"));
    }

    @Test
    void 면적의_소수_4자리를_자르지_않고_그대로_담는다() {
        // 지문은 받은 값 그대로 만들어야 해서, 읽을 때 반올림하면 안 돼요
        List<MolitTradeItem> items = MolitXmlParser.parse(fixture("apt-trade-11440-202608.xml")).items();

        assertThat(items).extracting(MolitTradeItem::excluUseAr).contains("59.9236", "59.989");
    }

    @Test
    void 값이_모두_같은_진짜_거래도_각각_담는다() {
        List<MolitTradeItem> items = MolitXmlParser.parse(fixture("apt-trade-11440-202608.xml")).items();

        assertThat(items).filteredOn(item -> "미원2".equals(item.aptNm()) && "18".equals(item.dealDay())).hasSize(2);
    }

    @ParameterizedTest(name = "{0}쪽: {1}건, 전체 121")
    @CsvSource({"1, 50", "2, 50", "3, 21"})
    void 나눠_받은_페이지도_각각_읽는다(int page, int count) {
        MolitXmlParser.Page parsed = MolitXmlParser.parse(fixture("apt-trade-11440-202608-page" + page + "-of50.xml"));

        assertThat(parsed.totalCount()).isEqualTo(121);
        assertThat(parsed.items()).hasSize(count);
    }

    @Test
    void 거래가_없는_달은_빈_목록이다() {
        MolitXmlParser.Page page = MolitXmlParser.parse(fixture("apt-trade-empty.xml"));

        assertThat(page.totalCount()).isZero();
        assertThat(page.items()).isEmpty();
    }

    // ---- 오류 응답 ----

    @Test
    void 인증키_오류_응답은_사유_코드와_함께_실패한다() {
        assertThatThrownBy(() -> MolitXmlParser.parse(fixture("apt-trade-error-invalid-key.xml")))
                .isInstanceOf(MolitApiException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")
                .extracting(e -> ((MolitApiException) e).getResultCode()).isEqualTo("30");
    }

    @ParameterizedTest(name = "resultCode {0} 이면 실패")
    @CsvSource({"03, NODATA_ERROR", "22, LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR", "99, UNKNOWN_ERROR",
            "' ', ''"})
    void 결과_코드가_000이_아니면_실패한다(String code, String message) {
        String body = "<response><header><resultCode>" + code + "</resultCode><resultMsg>" + message
                + "</resultMsg></header><body><items/><totalCount>0</totalCount></body></response>";

        assertThatThrownBy(() -> MolitXmlParser.parse(xml(body))).isInstanceOf(MolitApiException.class);
    }

    @Test
    void totalCount_가_없으면_다_받았는지_모르니_실패한다() {
        assertThatThrownBy(() -> MolitXmlParser.parse(xml(okResponse("", ""))))
                .isInstanceOf(MolitApiException.class).hasMessageContaining("totalCount");
    }

    @Test
    void totalCount_가_숫자가_아니면_실패한다() {
        assertThatThrownBy(() -> MolitXmlParser.parse(xml(okResponse("<totalCount>abc</totalCount>", ""))))
                .isInstanceOf(MolitApiException.class);
    }

    @ParameterizedTest(name = "XML 아님: [{0}]")
    @ValueSource(strings = {"", "not xml", "<html><body>502 Bad Gateway</body></html>", "<response><header>"})
    void XML_이_아니거나_깨졌으면_실패한다(String body) {
        assertThatThrownBy(() -> MolitXmlParser.parse(xml(body))).isInstanceOf(MolitApiException.class);
    }

    @Test
    void 응답이_null_이면_실패한다() {
        assertThatThrownBy(() -> MolitXmlParser.parse(null)).isInstanceOf(MolitApiException.class);
    }

    @Test
    void DOCTYPE_외부_엔티티는_읽지_않고_거부한다() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<response><header><resultCode>000</resultCode></header><body><items><item><aptNm>&x;</aptNm>"
                + "</item></items><totalCount>1</totalCount></body></response>";

        assertThatThrownBy(() -> MolitXmlParser.parse(xml(xxe))).isInstanceOf(MolitApiException.class);
    }

    // ---- 필드 모양 ----

    @Test
    void 거래에_없는_태그는_null_로_둔다() {
        MolitTradeItem item = MolitXmlParser.parse(xml(okResponse("<totalCount>1</totalCount>",
                "<item><aptNm>테스트</aptNm></item>"))).items().get(0);

        assertThat(item.aptNm()).isEqualTo("테스트");
        assertThat(item.jibun()).isNull();
        assertThat(item.cdealType()).isNull();
    }

    @Test
    void 결과_코드_앞뒤_공백은_괜찮다() {
        String body = "<response><header><resultCode> 000 </resultCode></header><body><items/>"
                + "<totalCount> 0 </totalCount></body></response>";

        assertThat(MolitXmlParser.parse(xml(body)).totalCount()).isZero();
    }
}
