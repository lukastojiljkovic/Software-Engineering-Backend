package rs.raf.trading.tax.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.security.TradingUserResolver;
import rs.raf.trading.tax.service.TaxService;

import static org.mockito.Mockito.*;

/**
 * Integracioni test {@link TaxScheduler} — pun Spring kontekst (H2 test profil).
 * {@code TaxService} je {@code @MockitoBean} (obracun se zasebno testira), money/
 * identitet seam ({@link BankaCoreClient}, {@link TradingUserResolver}) je mockovan
 * jer scheduler ne dira ni jedno ni drugo. {@code @Scheduled} je inertan; metoda
 * se poziva eksplicitno (faza 2c, adaptacija monolitnog testa).
 */
@SpringBootTest
@ActiveProfiles("test")
class TaxSchedulerIntegrationTest {

    @Autowired
    private TaxScheduler taxScheduler;

    @MockitoBean
    private TaxService taxService;

    @MockitoBean
    private BankaCoreClient bankaCoreClient;

    @MockitoBean
    private TradingUserResolver tradingUserResolver;

    @Test
    @DisplayName("scheduler calls taxService within Spring context")
    void schedulerCallsTaxServiceInContext() {
        taxScheduler.calculateMonthlyTax();

        verify(taxService, times(1)).calculateTaxForAllUsers();
    }

    @Test
    @DisplayName("scheduler does not propagate exception from service within Spring context")
    void schedulerSwallowsExceptionInContext() {
        doThrow(new RuntimeException("simulirana greska")).when(taxService).calculateTaxForAllUsers();

        taxScheduler.calculateMonthlyTax();

        verify(taxService, times(1)).calculateTaxForAllUsers();
    }
}
