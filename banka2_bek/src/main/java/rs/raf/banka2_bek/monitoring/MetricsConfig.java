package rs.raf.banka2_bek.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralna registracija custom poslovnih (domenskih) metrika.
 * <p>
 * Spring Boot Actuator + Micrometer Prometheus registry vec automatski
 * exposiraju standardne metrike (JVM, HTTP request-ove, DB connection pool,
 * cache hit rate, Logback log nivoa, Tomcat thread pool, ...). Ovde dodajemo
 * domenske metrike koje opisuju POSLOVNO ponasanje aplikacije:
 * <ul>
 *   <li>Broj uspesnih i neuspesnih login pokusaja (input za alert-e)</li>
 *   <li>Broj transakcija + njihova vrednost po valuti</li>
 *   <li>Broj OTC ponuda i sklopljenih ugovora (banka-core deo)</li>
 *   <li>Inter-bank protocol message-i po smeru (inbound/outbound)</li>
 * </ul>
 * Sve metrike imaju automatski tag {@code application=banka2_backend} koji
 * dolazi iz {@code management.metrics.tags.application} property-ja.
 *
 * <p><b>W2-T1 (26.05.2026):</b> {@code banka2_orders_executed_total} i
 * {@code banka2_order_execution_seconds} su PREMESTENI u
 * {@code trading-service/TradingMetricsConfig} — order/OTC/option/margin
 * domain je posle Faza 2f cutover-a kompletno u trading-service-u, pa su
 * ovde bili dead beans (registrovani ali nikad inkrementirani).
 */
@Configuration
public class MetricsConfig {

    /** Counter — broj uspesnih login zahteva (vraca 200 OK). */
    @Bean
    public Counter loginSuccessCounter(MeterRegistry registry) {
        return Counter.builder("banka2_login_success_total")
                .description("Broj uspesnih login zahteva")
                .register(registry);
    }

    /** Counter — broj neuspesnih login zahteva (HTTP 401, ne ukljucuje 429 rate-limit). */
    @Bean
    public Counter loginFailureCounter(MeterRegistry registry) {
        return Counter.builder("banka2_login_failure_total")
                .description("Broj neuspesnih login zahteva — input za BruteForceLogin alert")
                .register(registry);
    }

    /** Counter — broj 429 Too Many Requests odgovora (rate-limit hit). */
    @Bean
    public Counter rateLimitHitCounter(MeterRegistry registry) {
        return Counter.builder("banka2_rate_limit_hit_total")
                .description("Broj zahteva odbacenih zbog rate-limit-a (HTTP 429)")
                .register(registry);
    }

    /** Counter — broj realizovanih unutarbankarskih placanja. */
    @Bean
    public Counter paymentExecutedCounter(MeterRegistry registry) {
        return Counter.builder("banka2_payments_executed_total")
                .description("Broj realizovanih placanja izmedju klijenata")
                .register(registry);
    }

    /** Counter — broj sklopljenih OTC ugovora (lokalni + inter-bank). */
    @Bean
    public Counter otcContractCreatedCounter(MeterRegistry registry) {
        return Counter.builder("banka2_otc_contracts_created_total")
                .description("Broj sklopljenih OTC opcionih ugovora (lokalni + inter-bank)")
                .register(registry);
    }

    /** Counter — broj inter-bank inbound zahteva primljenih sa /interbank endpoint-a. */
    @Bean
    public Counter interbankInboundCounter(MeterRegistry registry) {
        return Counter.builder("banka2_interbank_inbound_total")
                .description("Broj primljenih inter-bank zahteva")
                .register(registry);
    }

    /** Counter — broj inter-bank outbound poruka poslatih partner banci. */
    @Bean
    public Counter interbankOutboundCounter(MeterRegistry registry) {
        return Counter.builder("banka2_interbank_outbound_total")
                .description("Broj poslatih inter-bank zahteva ka partner banci")
                .register(registry);
    }
}
