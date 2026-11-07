package com.gooodhandletruck.zippick.domain.trade.client;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 국토부 XML 응답 읽기. Java 기본 파서를 써서 의존성을 늘리지 않아요. (#7 에서 추가)
 *
 * 응답 모양이 두 가지예요 (2026-11-06 실제 응답으로 확인).
 * - 정상: {@code <response><header><resultCode>000</resultCode>...<body><items><item>...}
 * - 게이트웨이 오류(인증키 등): {@code <OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode>...}
 */
final class MolitXmlParser {

    /** 정상 결과 코드 */
    static final String SUCCESS_CODE = "000";

    private MolitXmlParser() {
    }

    /** 한 페이지 결과 */
    record Page(int totalCount, List<MolitTradeItem> items) {
    }

    static Page parse(byte[] xml) {
        return parse(xml, MolitDataset.APT);
    }

    /** 종류별로 건물명 태그만 달라요 (아파트 aptNm, 오피스텔 offiNm) (#27) */
    static Page parse(byte[] xml, MolitDataset dataset) {
        Element root = read(xml).getDocumentElement();

        if ("OpenAPI_ServiceResponse".equals(root.getTagName())) {
            String reason = text(root, "returnReasonCode");
            String message = text(root, "returnAuthMsg");
            throw new MolitApiException("국토부 API 오류: " + reason + " " + message + " (" + text(root, "errMsg") + ")",
                    reason);
        }

        String resultCode = text(root, "resultCode");
        if (!SUCCESS_CODE.equals(trimOrNull(resultCode))) {
            throw new MolitApiException("국토부 API 결과 코드 " + resultCode + ": " + text(root, "resultMsg"),
                    trimOrNull(resultCode));
        }

        String totalCount = trimOrNull(text(root, "totalCount"));
        if (totalCount == null) {
            // 건수를 모르면 다 받았는지 확인할 수 없어서, 반영하지 않게 실패로 봐요
            throw new MolitApiException("국토부 응답에 totalCount 가 없어요", resultCode);
        }

        NodeList itemNodes = root.getElementsByTagName("item");
        List<MolitTradeItem> items = new ArrayList<>(itemNodes.getLength());
        for (int i = 0; i < itemNodes.getLength(); i++) {
            items.add(toItem((Element) itemNodes.item(i), dataset));
        }
        try {
            return new Page(Integer.parseInt(totalCount), items);
        } catch (NumberFormatException e) {
            throw new MolitApiException("국토부 totalCount 가 숫자가 아니에요: " + totalCount, e);
        }
    }

    private static MolitTradeItem toItem(Element item, MolitDataset dataset) {
        return new MolitTradeItem(
                text(item, "sggCd"),
                text(item, "umdNm"),
                text(item, "jibun"),
                text(item, dataset.nameTag()), // 오피스텔은 offiNm 을 같은 자리(건물명)에 담아요
                text(item, "aptDong"),
                text(item, "buildYear"),
                text(item, "dealYear"),
                text(item, "dealMonth"),
                text(item, "dealDay"),
                text(item, "dealAmount"),
                text(item, "excluUseAr"),
                text(item, "floor"),
                text(item, "cdealType"),
                text(item, "cdealDay"),
                text(item, "rgstDate"),
                text(item, "dealingGbn"),
                text(item, "estateAgentSggNm"),
                text(item, "slerGbn"),
                text(item, "buyerGbn"),
                text(item, "landLeaseholdGbn"));
    }

    private static Document read(byte[] xml) {
        if (xml == null || xml.length == 0) {
            throw new MolitApiException("국토부 응답이 비어 있어요", (String) null);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 외부 엔티티(XXE)를 막아요. 외부 응답을 읽는 곳이라 기본으로 켜 둬요
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new MolitApiException("국토부 응답 XML 을 읽지 못했어요", e);
        }
    }

    /** 태그가 없으면 null, 있으면 받은 문자열 그대로 (공백 포함) */
    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        return node.getTextContent();
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
