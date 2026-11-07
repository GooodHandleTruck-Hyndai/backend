package com.gooodhandletruck.zippick.domain.trade.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 국토부 「아파트 매매 실거래가 자료」(RTMSDataSvcAptTrade) 호출. API 목록 3번의 호출 부분. (#7 에서 추가)
 *
 * (구 코드, 계약월) 한 달 치를 모든 페이지에서 받아 모아요. 실패하면 MolitApiException 을 던지고,
 * 다시 시도는 하지 않아요 — 배치(5·12번)가 다음 회차에 다시 받는 게 규칙이라 여기서 반복 호출하면 중복 부담만 생겨요.
 */
@Component
@EnableConfigurationProperties(MolitProperties.class)
public class MolitClient {

    private static final String PATH = "/getRTMSDataSvcAptTrade";
    private static final Pattern SGG_CD = Pattern.compile("\\d{5}");
    private static final Pattern DEAL_YM = Pattern.compile("\\d{4}(0[1-9]|1[0-2])");

    private final RestClient restClient;
    private final MolitProperties properties;

    @Autowired // 생성자가 두 개라 스프링이 쓸 생성자를 알려 줘요
    public MolitClient(MolitProperties properties) {
        this(RestClient.builder().requestFactory(requestFactory(properties)), properties);
    }

    /** 테스트에서 가짜 서버(MockRestServiceServer)를 붙인 builder 를 넣으려고 열어 둬요 */
    MolitClient(RestClient.Builder builder, MolitProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    /**
     * (구, 계약월) 한 달 치 거래.
     *
     * @param sggCd  구 코드 5자리 (국토부 LAWD_CD, 예: 11440 마포구)
     * @param dealYm 계약월 YYYYMM (예: 202608)
     */
    public MolitMonthResult fetchMonth(String sggCd, String dealYm) {
        validate(sggCd, dealYm);

        List<MolitTradeItem> items = new ArrayList<>();
        int totalCount = 0;
        for (int pageNo = 1; pageNo <= properties.maxPages(); pageNo++) {
            MolitXmlParser.Page page = MolitXmlParser.parse(fetchPage(sggCd, dealYm, pageNo));
            totalCount = page.totalCount();
            items.addAll(page.items());
            // 다 받았거나, 더 줄 게 없다고 빈 페이지가 오면 멈춰요 (빈 페이지 확인이 없으면 건수가 안 맞을 때 끝까지 돌아요)
            if (items.size() >= totalCount || page.items().isEmpty()) {
                return new MolitMonthResult(sggCd, dealYm, totalCount, List.copyOf(items));
            }
        }
        throw new MolitApiException("국토부 페이지가 " + properties.maxPages() + "쪽을 넘어요 (sggCd=" + sggCd
                + ", dealYm=" + dealYm + ", totalCount=" + totalCount + ")", (String) null);
    }

    private byte[] fetchPage(String sggCd, String dealYm, int pageNo) {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            throw new MolitApiException("국토부 서비스 키가 없어요 (환경 변수 MOLIT_SERVICE_KEY)", (String) null);
        }
        try {
            // 키를 템플릿 변수로 넘겨야 + / = 같은 문자가 %2B %2F %3D 로 정확히 인코딩돼요 (안 하면 인증 실패)
            return restClient.get()
                    .uri(PATH + "?serviceKey={key}&LAWD_CD={sggCd}&DEAL_YMD={dealYm}&numOfRows={rows}&pageNo={page}",
                            Map.of("key", properties.serviceKey(), "sggCd", sggCd, "dealYm", dealYm,
                                    "rows", properties.pageSize(), "page", pageNo))
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException e) {
            // 키가 로그에 남지 않게 원인 메시지 대신 요청 범위만 적어요
            throw new MolitApiException("국토부 API 호출 실패 (sggCd=" + sggCd + ", dealYm=" + dealYm + ", page="
                    + pageNo + "): " + e.getClass().getSimpleName(), e);
        }
    }

    private static void validate(String sggCd, String dealYm) {
        if (sggCd == null || !SGG_CD.matcher(sggCd).matches()) {
            throw new IllegalArgumentException("구 코드는 숫자 5자리예요: " + sggCd);
        }
        if (dealYm == null || !DEAL_YM.matcher(dealYm).matches()) {
            throw new IllegalArgumentException("계약월은 YYYYMM 이에요: " + dealYm);
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(MolitProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        // 국토부가 응답을 안 주고 붙잡고 있어도 배치가 멈추지 않게 응답 제한 시간을 걸어요
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
