package rs.raf.trading.otc.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.trading.otc.service.OtcService;

/**
 * Dnevno markiranje OTC ugovora kojima je prosao settlementDate kao EXPIRED.
 * Time se prodavcima oslobadja publicQuantity za nove ponude.
 *
 * NAPOMENA (copy-first ekstrakcija, faza 2d): {@code @Scheduled} je u
 * trading-service-u DORMANTAN — {@code TradingServiceApplication} namerno
 * nema {@code @EnableScheduling} do cutover-a 2f. Bean se i dalje registruje
 * (drzi kontract metode), ali se {@code expireContracts} ne okida automatski.
 * Posle 2f cutover-a monolitni OTC scheduler se gasi i ovaj preuzima.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtcContractExpiryScheduler {

    private final OtcService otcService;

    @Scheduled(cron = "0 5 1 * * *")
    public void expireContracts() {
        int count = otcService.expireSettledContracts();
        if (count > 0) {
            log.info("OTC: {} ugovora istekao i markiran EXPIRED", count);
        }
    }
}
