package rs.raf.trading.tax.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.trading.notification.model.NotificationType;
import rs.raf.trading.notification.service.NotificationService;
import rs.raf.trading.tax.service.TaxCalculationException;
import rs.raf.trading.tax.service.TaxService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Test {@link TaxScheduler} — porten verbatim iz monolita (faza 2c, samo
 * package rename). {@code @Scheduled} je inertan; metoda se poziva eksplicitno.
 *
 * <p>BE-ORD-08: dodati testovi za {@link TaxCalculationException} handling —
 * scheduler hvata exception po-korisniku, emituje notifikaciju supervizoru,
 * ali ne propagira (ne sme da padne ceo cron).
 */
@ExtendWith(MockitoExtension.class)
class TaxSchedulerTest {

    @Mock
    private TaxService taxService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TaxScheduler taxScheduler;

    @Nested
    @DisplayName("calculateMonthlyTax")
    class CalculateMonthlyTax {

        @Test
        @DisplayName("calls taxService.calculateTaxForAllUsers() exactly once")
        void callsTaxServiceOnce() {
            taxScheduler.calculateMonthlyTax();

            verify(taxService, times(1)).calculateTaxForAllUsers();
        }

        @Test
        @DisplayName("does not propagate exception from taxService")
        void doesNotPropagateException() {
            doThrow(new RuntimeException("DB error")).when(taxService).calculateTaxForAllUsers();

            taxScheduler.calculateMonthlyTax();

            verify(taxService, times(1)).calculateTaxForAllUsers();
        }

        @Test
        @DisplayName("catches all exception subtypes including RuntimeException")
        void catchesAllExceptionTypes() {
            doThrow(new IllegalStateException("bad state")).when(taxService).calculateTaxForAllUsers();

            taxScheduler.calculateMonthlyTax();

            verify(taxService, times(1)).calculateTaxForAllUsers();
        }

        @Test
        @DisplayName("BE-ORD-08: TaxCalculationException → notify supervisor with userId/userType")
        void taxCalculationExceptionEmitsSupervisorNotification() {
            doThrow(new TaxCalculationException(42L, "CLIENT",
                    "FX rate unavailable for USD", new RuntimeException("timeout")))
                    .when(taxService).calculateTaxForAllUsers();

            taxScheduler.calculateMonthlyTax();

            // Notifikacija mora biti emitovana sa SUPERVISOR recipientType i GENERAL type,
            // referencirajuci userId iz exception-a (TAX/42).
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(notificationService, times(1)).notify(
                    isNull(),
                    eq("SUPERVISOR"),
                    eq(NotificationType.GENERAL),
                    eq("Obracun poreza neuspesan (FX)"),
                    bodyCaptor.capture(),
                    eq("TAX"),
                    eq(42L)
            );
            assertThat(bodyCaptor.getValue())
                    .contains("42")
                    .contains("CLIENT")
                    .contains("FX rate unavailable for USD");
        }

        @Test
        @DisplayName("BE-ORD-08: notify failure ne propagira (best-effort)")
        void taxCalculationExceptionNotifyFailureIsSwallowed() {
            doThrow(new TaxCalculationException(1L, "CLIENT", "FX dead", null))
                    .when(taxService).calculateTaxForAllUsers();
            doThrow(new RuntimeException("RabbitMQ down")).when(notificationService).notify(
                    any(), any(), any(), any(), any(), any(), any());

            // Ne sme da padne — scheduler mora da bude rezilijentan na notify pad.
            taxScheduler.calculateMonthlyTax();

            verify(notificationService).notify(any(), any(), any(), any(), any(), any(), any());
        }
    }
}
