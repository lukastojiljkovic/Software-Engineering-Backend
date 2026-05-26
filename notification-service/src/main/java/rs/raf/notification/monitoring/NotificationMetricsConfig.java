package rs.raf.notification.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom poslovne metrike za notification-service.
 *
 * <p>{@code banka2_emails_sent_total} je @Bean (singleton — jedan counter per
 * registry). {@code banka2_emails_failed_total} ima {@code reason} tag pa je
 * builder helper a NE @Bean — Micrometer registruje jedinstven Counter per
 * (name, tag-set) kombinacija. Konzument ga obicno koristi inline preko
 * {@code registry.counter("banka2_emails_failed_total", "reason", reason).increment()}.
 */
@Configuration
public class NotificationMetricsConfig {

    @Bean
    public Counter emailsSentCounter(MeterRegistry registry) {
        return Counter.builder("banka2_emails_sent_total")
                .description("Ukupan broj uspesno poslatih email-ova")
                .register(registry);
    }

    /**
     * Failed counter sa reason tag-om — helper za on-demand registraciju.
     * NIJE @Bean (Spring ne moze da resolve String reason iz konteksta).
     * U produkciji NotificationConsumer direktno radi
     * {@code registry.counter("banka2_emails_failed_total", "reason", reason).increment()}.
     */
    public Counter emailsFailedCounter(MeterRegistry registry, String reason) {
        return Counter.builder("banka2_emails_failed_total")
                .tag("reason", reason)
                .description("Broj failed email-ova po razlogu (smtp_error, dlq, auth_failure)")
                .register(registry);
    }
}
