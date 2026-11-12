package com.gooodhandletruck.zippick.domain.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gooodhandletruck.zippick.domain.TestFixtures;
import com.gooodhandletruck.zippick.domain.alert.dto.AlertReadResponse;
import com.gooodhandletruck.zippick.domain.alert.dto.AlertResponse;
import com.gooodhandletruck.zippick.domain.alert.service.AlertGenerateService;
import com.gooodhandletruck.zippick.domain.alert.service.AlertService;
import com.gooodhandletruck.zippick.domain.building.entity.Building;
import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import com.gooodhandletruck.zippick.domain.building.repository.BuildingRepository;
import com.gooodhandletruck.zippick.domain.device.dto.DeviceRegisterRequest;
import com.gooodhandletruck.zippick.domain.device.dto.DeviceResponse;
import com.gooodhandletruck.zippick.domain.device.repository.DeviceRepository;
import com.gooodhandletruck.zippick.domain.device.service.DeviceService;
import com.gooodhandletruck.zippick.domain.favorite.service.FavoriteService;
import com.gooodhandletruck.zippick.domain.interest.dto.InterestConditionRequest;
import com.gooodhandletruck.zippick.domain.interest.service.InterestConditionService;
import com.gooodhandletruck.zippick.domain.trade.entity.Trade;
import com.gooodhandletruck.zippick.domain.trade.repository.TradeRepository;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.error.BusinessException;
import com.gooodhandletruck.zippick.global.error.ErrorCode;
import com.gooodhandletruck.zippick.global.util.KstClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AlertServiceTest {

    private static final AtomicInteger FINGERPRINT = new AtomicInteger();

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertGenerateService alertGenerateService;

    @Autowired
    private InterestConditionService interestConditionService;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private TradeRepository tradeRepository;

    private final LocalDate today = KstClock.today();
    private CurrentDevice me;
    private CurrentDevice other;
    private Building mapo;      // 마포구 11440
    private Building gangnam;   // 강남구 11680

    @BeforeEach
    void setUp() {
        me = registerDevice();
        other = registerDevice();
        mapo = buildingRepository.save(TestFixtures.building().build());
        gangnam = buildingRepository.save(TestFixtures.building().sggCd("11680").umdNm("역삼동").aptNm("역삼래미안").build());
    }

    @Test
    void 관심_조건에_맞는_새_거래로_알림을_만든다() {
        setCondition(me, true);
        trade(mapo, 125000, "84.97", 3, null);

        int created = alertGenerateService.generate(today);

        assertThat(created).isEqualTo(1);
        List<AlertResponse> alerts = alertService.getAlerts(me, null).alerts();
        assertThat(alerts).hasSize(1);
        AlertResponse alert = alerts.get(0);
        assertThat(alert.buildingId()).isEqualTo(mapo.getId());
        assertThat(alert.buildingName()).isEqualTo("마포래미안푸르지오1단지");
        assertThat(alert.deal().price()).isEqualTo(125000);
        assertThat(alert.deal().areaM2()).isEqualByComparingTo("84.97");
        assertThat(alert.deal().areaPyeong()).isEqualByComparingTo("25.7");
        assertThat(alert.read()).isFalse();
    }

    @Test
    void 조건에_안_맞는_거래는_알리지_않는다() {
        setCondition(me, true); // 마포구, 8억~15억, 59~85㎡, APT
        trade(mapo, 200000, "84.97", 3, null);   // 가격 초과
        trade(mapo, 100000, "114.00", 3, null);  // 면적 초과
        trade(gangnam, 100000, "84.97", 3, null); // 다른 구
        trade(mapo, 100000, "84.97", 3, "O");     // 해제 거래
        trade(mapo, 100000, "84.97", 40, null);   // 계약일이 30일보다 오래됨

        assertThat(alertGenerateService.generate(today)).isZero();
    }

    @Test
    void 저장한_건물은_조건과_상관없이_알린다() {
        setCondition(me, true); // 마포구만
        favoriteService.save(me, gangnam.getId());
        trade(gangnam, 300000, "120.00", 3, null);

        assertThat(alertGenerateService.generate(today)).isEqualTo(1);
    }

    @Test
    void 같은_거래는_두_번_알리지_않는다() {
        setCondition(me, true);
        trade(mapo, 125000, "84.97", 3, null);

        alertGenerateService.generate(today);

        assertThat(alertGenerateService.generate(today)).isZero();
        assertThat(alertService.getAlerts(me, null).alerts()).hasSize(1);
    }

    @Test
    void 알림을_끄거나_조건이_없으면_알리지_않는다() {
        setCondition(me, false);
        trade(mapo, 125000, "84.97", 3, null);

        assertThat(alertGenerateService.generate(today)).isZero();
        assertThat(alertService.getAlerts(other, null).alerts()).isEmpty();
    }

    @Test
    void 읽음_처리는_처음_읽은_시각을_유지한다() {
        setCondition(me, true);
        trade(mapo, 125000, "84.97", 3, null);
        alertGenerateService.generate(today);
        Long alertId = alertService.getAlerts(me, null).alerts().get(0).alertId();

        AlertReadResponse first = alertService.markRead(me, alertId);
        AlertReadResponse second = alertService.markRead(me, alertId);

        assertThat(first.read()).isTrue();
        assertThat(second.readAt()).isEqualTo(first.readAt());
        assertThat(alertService.getAlerts(me, null).alerts().get(0).read()).isTrue();
    }

    @Test
    void 남의_알림이나_없는_알림을_읽으면_N001() {
        setCondition(me, true);
        trade(mapo, 125000, "84.97", 3, null);
        alertGenerateService.generate(today);
        Long alertId = alertService.getAlerts(me, null).alerts().get(0).alertId();

        assertThatThrownBy(() -> alertService.markRead(other, alertId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALERT_NOT_FOUND);
        assertThatThrownBy(() -> alertService.markRead(me, 999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALERT_NOT_FOUND);
    }

    @Test
    void since_이후_알림만_돌려준다() {
        setCondition(me, true);
        trade(mapo, 125000, "84.97", 3, null);
        alertGenerateService.generate(today);

        assertThat(alertService.getAlerts(me, "2000-01-01T00:00:00+09:00").alerts()).hasSize(1);
        assertThat(alertService.getAlerts(me, "2999-01-01T00:00:00+09:00").alerts()).isEmpty();
        // URL 에서 + 가 공백으로 바뀌어 와도 처리
        assertThat(alertService.getAlerts(me, "2000-01-01T00:00:00 09:00").alerts()).hasSize(1);
    }

    @Test
    void since_형식이_틀리면_C001() {
        assertThatThrownBy(() -> alertService.getAlerts(me, "어제"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private void setCondition(CurrentDevice device, boolean alertEnabled) {
        interestConditionService.save(device, new InterestConditionRequest(
                List.of("11440"), 80000, 150000, List.of(BuildingType.APT), 59, 85, alertEnabled));
    }

    private void trade(Building building, int price, String area, int daysAgo, String cdealType) {
        LocalDate dealDate = today.minusDays(daysAgo);
        Trade trade = TestFixtures.trade(building)
                .fingerprint(String.format("%064d", FINGERPRINT.incrementAndGet()))
                .sggCd(building.getSggCd())
                .dealYm(dealDate.toString().substring(0, 7).replace("-", ""))
                .dealDate(dealDate)
                .dealAmount(price)
                .excluUseAr(new BigDecimal(area))
                .cdealType(cdealType)
                .build();
        tradeRepository.save(trade);
    }

    private CurrentDevice registerDevice() {
        DeviceResponse response = deviceService.register(new DeviceRegisterRequest("0.1.0"));
        Long deviceId = deviceRepository.findByAnonymousId(response.anonymousId()).orElseThrow().getId();
        return new CurrentDevice(deviceId, response.anonymousId());
    }
}
