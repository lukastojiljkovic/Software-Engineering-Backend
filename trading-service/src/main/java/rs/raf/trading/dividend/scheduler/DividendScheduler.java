package rs.raf.trading.dividend.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.trading.dividend.service.DividendService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Kvartalani scheduler za isplatu dividendi na akcije (B9).
 *
 * <p>Okida se cron-om na poslednji dan svakog kvartalnog meseca
 * (mart / jun / septembar / decembar) u 06:00 UTC. Ako taj dan pada
 * na vikend, pomera se unazad na prethodni petak.
 *
 * <p>{@code @EnableScheduling} je vec aktivan u
 * {@code rs.raf.trading.config.SchedulingConfig} — ne dodavati duplikat.
 * Test profil gasi scheduling property-jem {@code trading.scheduling.enabled=false}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DividendScheduler {

    private final DividendService dividendService;

    /**
     * Poslednji dan svakog kvartalnog meseca u 06:00 UTC.
     *
     * <p>Koristi Spring-ov prosireni cron format gde {@code L} oznacava
     * poslednji dan meseca (nije Quartz — ovo je Spring {@code @Scheduled}).
     * Ako poslednji dan meseca pada na subotu/nedelju, pomera se na petak
     * pre poziva {@link DividendService#processQuarterlyDividends}.
     */
    @Scheduled(cron = "0 0 6 L MAR,JUN,SEP,DEC ?")
    public void runQuarterlyDividendPayout() {
        LocalDate paymentDate = LocalDate.now(ZoneOffset.UTC);

        // Ako poslednji dan meseca pada na vikend, pomeri se unazad na petak.
        while (paymentDate.getDayOfWeek() == DayOfWeek.SATURDAY
                || paymentDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            paymentDate = paymentDate.minusDays(1);
        }

        log.info("DividendScheduler: pokrecemo kvartalnu isplatu dividendi za datum {}",
                paymentDate);
        try {
            dividendService.processQuarterlyDividends(paymentDate);
            log.info("DividendScheduler: kvartalna isplata dividendi za {} zavrsena uspesno",
                    paymentDate);
        } catch (Exception ex) {
            log.error("DividendScheduler: neocekivana greska pri kvartalnoj isplati dividendi: {}",
                    ex.getMessage(), ex);
        }
    }
}
