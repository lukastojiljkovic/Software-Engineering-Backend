package rs.raf.trading.dividend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.trading.dividend.scheduler.DividendScheduler;
import rs.raf.trading.dividend.service.DividendService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * Unit testovi za {@link DividendScheduler} — B9 (dividende na akcije).
 *
 * <p>Provjera da scheduler ispravno pomjera datum isplate unazad na petak
 * kad poslednji dan kvartalnog meseca pada na vikend (subota ili nedjelja).
 */
@ExtendWith(MockitoExtension.class)
class DividendSchedulerTest {

    @InjectMocks
    private DividendScheduler dividendScheduler;

    @Mock
    private DividendService dividendService;

    // ── Test 1: Subota -> prethodni petak ─────────────────────────────────────

    @Test
    void runQuarterlyDividendPayout_rollsBackSaturdayToFriday() {
        // 2025-12-27 je subota; ocekujemo da scheduler prosledi petak 2025-12-26
        LocalDate saturday = LocalDate.of(2025, 12, 27);
        assertThat(saturday.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        LocalDate expectedFriday = LocalDate.of(2025, 12, 26);

        try (MockedStatic<LocalDate> mockedLocalDate = mockStatic(LocalDate.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mockedLocalDate.when(() -> LocalDate.now(ZoneOffset.UTC)).thenReturn(saturday);

            dividendScheduler.runQuarterlyDividendPayout();
        }

        var captor = forClass(LocalDate.class);
        verify(dividendService).processQuarterlyDividends(captor.capture());
        assertThat(captor.getValue()).isEqualTo(expectedFriday);
        assertThat(captor.getValue().getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
    }

    // ── Test 2: Nedjelja -> prethodni petak (preskace 2 dana) ─────────────────

    @Test
    void runQuarterlyDividendPayout_rollsBackSundayToFriday() {
        // 2025-12-28 je nedjelja; ocekujemo petak 2025-12-26 (2 minusDays)
        LocalDate sunday = LocalDate.of(2025, 12, 28);
        assertThat(sunday.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        LocalDate expectedFriday = LocalDate.of(2025, 12, 26);

        try (MockedStatic<LocalDate> mockedLocalDate = mockStatic(LocalDate.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mockedLocalDate.when(() -> LocalDate.now(ZoneOffset.UTC)).thenReturn(sunday);

            dividendScheduler.runQuarterlyDividendPayout();
        }

        var captor = forClass(LocalDate.class);
        verify(dividendService).processQuarterlyDividends(captor.capture());
        assertThat(captor.getValue()).isEqualTo(expectedFriday);
        assertThat(captor.getValue().getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
    }

    // ── Test 3: Radni dan ostaje nepromijenjen ────────────────────────────────

    @Test
    void runQuarterlyDividendPayout_doesNotShiftWeekday() {
        // 2025-12-31 je srijeda — bez pomaka
        LocalDate wednesday = LocalDate.of(2025, 12, 31);
        assertThat(wednesday.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);

        try (MockedStatic<LocalDate> mockedLocalDate = mockStatic(LocalDate.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mockedLocalDate.when(() -> LocalDate.now(ZoneOffset.UTC)).thenReturn(wednesday);

            dividendScheduler.runQuarterlyDividendPayout();
        }

        var captor = forClass(LocalDate.class);
        verify(dividendService).processQuarterlyDividends(captor.capture());
        assertThat(captor.getValue()).isEqualTo(wednesday);
    }
}
