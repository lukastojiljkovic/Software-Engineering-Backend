package rs.raf.trading.option.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.option.service.OptionGeneratorService;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

/**
 * Integracioni test {@link OptionScheduler} u punom Spring kontekstu (H2 test
 * profil).
 *
 * <p>NAPOMENA (copy-first ekstrakcija, faza 2d-C): {@link BankaCoreClient} je
 * {@code @MockitoBean} jer trading-service kontekst nema banka-core servis;
 * scheduler ga ne koristi direktno, ali ga {@code OptionService} drzi kao
 * zavisnost. {@code dailyOptionMaintenance} se ovde poziva rucno — automatsko
 * okidanje je uspavano ({@code TradingServiceApplication} nema
 * {@code @EnableScheduling}).
 */
@SpringBootTest
@ActiveProfiles("test")
class OptionSchedulerIntegrationTest {

    @Autowired
    private OptionScheduler optionScheduler;

    @MockitoBean
    private OptionGeneratorService optionGeneratorService;

    @MockitoBean
    private BankaCoreClient bankaCoreClient;

    @Test
    @DisplayName("dailyOptionMaintenance runs successfully in Spring context with empty DB")
    void dailyMaintenanceRunsInSpringContext() {
        assertThatNoException().isThrownBy(() -> optionScheduler.dailyOptionMaintenance());

        verify(optionGeneratorService, times(1)).generateAllOptions();
    }

    @Test
    @DisplayName("dailyOptionMaintenance does not fail when generator throws exception")
    void doesNotFailWhenGeneratorThrows() {
        doThrow(new RuntimeException("generation error"))
                .when(optionGeneratorService).generateAllOptions();

        assertThatNoException().isThrownBy(() -> optionScheduler.dailyOptionMaintenance());
    }
}
