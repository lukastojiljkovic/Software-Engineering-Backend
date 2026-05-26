package rs.raf.trading.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradingMetricsConfigTest {

    private MeterRegistry registry;
    private TradingMetricsConfig config;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        config = new TradingMetricsConfig();
    }

    @Test
    void ordersExecutedCounter_isRegistered() {
        Counter c = config.ordersExecutedCounter(registry);
        assertThat(c.getId().getName()).isEqualTo("banka2_orders_executed_total");
    }

    @Test
    void orderExecutionTimer_hasPercentileHistogram() {
        Timer t = config.orderExecutionTimer(registry);
        assertThat(t.getId().getName()).isEqualTo("banka2_order_execution_seconds");
    }

    @Test
    void otcIntraCounter_isRegistered() {
        Counter c = config.otcIntraTotal(registry);
        assertThat(c.getId().getName()).isEqualTo("banka2_otc_intra_total");
    }

    @Test
    void otcInterCounter_isRegistered() {
        Counter c = config.otcInterTotal(registry);
        assertThat(c.getId().getName()).isEqualTo("banka2_otc_inter_total");
    }

    @Test
    void optionsCounter_isRegistered() {
        Counter c = config.optionsTotal(registry);
        assertThat(c.getId().getName()).isEqualTo("banka2_options_total");
    }

    @Test
    void marginCallsCounter_isRegistered() {
        Counter c = config.marginCallsTotal(registry);
        assertThat(c.getId().getName()).isEqualTo("banka2_margin_calls_total");
    }
}
