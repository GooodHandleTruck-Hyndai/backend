package com.gooodhandletruck.zippick.domain.trade.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 다른 패키지 테스트(적재 서비스 #8)가 저장해 둔 실제 국토부 응답을 쓰게 해 주는 도우미. (#8 에서 추가)
 *
 * 파서(MolitXmlParser)는 클라이언트 안에서만 쓰려고 package-private 이라, 같은 패키지인 여기서 대신 불러요.
 */
public final class MolitFixtures {

    private MolitFixtures() {
    }

    /** 실제 마포구(11440) 2026년 8월 응답 121건 */
    public static MolitMonthResult mapo202608() {
        MolitXmlParser.Page page = MolitXmlParser.parse(MolitXmlParserTest.fixture("apt-trade-11440-202608.xml"));
        return new MolitMonthResult("11440", "202608", page.totalCount(), page.items());
    }

    /**
     * 거래 한 건의 일부 칸만 바꾼 복사본. 예: copy(item, Map.of("cdealType", "O"))
     * 레코드라 setter 가 없어서, 칸 이름으로 바꿔 끼워요 (오타면 바로 실패).
     */
    public static MolitTradeItem copy(MolitTradeItem item, Map<String, String> overrides) {
        RecordComponent[] components = MolitTradeItem.class.getRecordComponents();
        Set<String> unknown = new HashSet<>(overrides.keySet());
        Object[] values = new Object[components.length];
        try {
            for (int i = 0; i < components.length; i++) {
                String name = components[i].getName();
                unknown.remove(name);
                values[i] = overrides.containsKey(name) ? overrides.get(name) : components[i].getAccessor().invoke(item);
            }
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("MolitTradeItem 에 없는 칸: " + unknown);
            }
            Constructor<MolitTradeItem> constructor = MolitTradeItem.class.getDeclaredConstructor(
                    Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static MolitMonthResult result(String sggCd, String dealYm, int totalCount, List<MolitTradeItem> items) {
        return new MolitMonthResult(sggCd, dealYm, totalCount, List.copyOf(items));
    }
}
