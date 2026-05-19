package rs.raf.trading.tax.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.trading.tax.service.TaxService;

/**
 * Scheduler za automatski obracun poreza.
 * <p>
 * Pokrece se prvog dana svakog meseca u ponoc (00:00:00).
 * Poziva TaxService.calculateTaxForAllUsers() koji obracunava porez
 * na osnovu svih DONE ordera za svakog korisnika.
 * <p>
 * Specifikacija: Celina 3 - Porez na kapitalnu dobit (15%)
 * <p>
 * Nakon obracuna, loguje notifikaciju o poreskim obavezama.
 * Kada se implementira TaxEmailTemplate, ovde dodati slanje emailova
 * korisnicima ciji se taxOwed promenio (koristeci MailNotificationService).
 * <p>
 * NAPOMENA (copy-first ekstrakcija, faza 2c): {@code @Scheduled} je USPAVAN —
 * {@code TradingServiceApplication} namerno NEMA {@code @EnableScheduling} do
 * cutover-a (2f). Monolit i dalje vrti svoj {@code TaxScheduler}; trading-service
 * ga ne okida da se obracun poreza ne bi izvrsavao dvaput.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaxScheduler {

    private final TaxService taxService;

    /**
     * Mesecni obracun poreza — pokrece se 1. u mesecu u 00:00:00.
     * <p>
     * Cron format: sekunda minut sat dan-u-mesecu mesec dan-u-nedelji
     * "0 0 0 1 * *" = 00:00:00 prvog dana svakog meseca
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void calculateMonthlyTax() {
        log.info("Starting monthly tax calculation...");
        try {
            taxService.calculateTaxForAllUsers();
            log.info("Monthly tax calculation completed successfully.");
            log.info("Tax calculation complete. Email notifications for users with outstanding tax would be sent here.");
        } catch (Exception e) {
            log.error("Error during tax calculation: {}", e.getMessage(), e);
        }
    }
}
