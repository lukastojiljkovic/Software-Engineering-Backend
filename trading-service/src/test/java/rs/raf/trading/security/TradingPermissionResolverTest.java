package rs.raf.trading.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.client.BankaCoreClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testovi za {@link TradingPermissionResolver} — mockovan {@link BankaCoreClient}.
 * Pokriva uspesno razresavanje + kes (drugi poziv ne pogadja klijent),
 * rezilijentan pad na praznu listu pri banka-core gresci, i kljucnu I3
 * invarijantu: <b>neuspesan lookup se NE kesira</b> — sledeci poziv ponovo
 * gadja banka-core.
 */
class TradingPermissionResolverTest {

    private BankaCoreClient bankaCoreClient;
    private TradingPermissionResolver resolver;

    @BeforeEach
    void setUp() {
        bankaCoreClient = mock(BankaCoreClient.class);
        resolver = new TradingPermissionResolver(bankaCoreClient);
    }

    @Test
    void resolvePermissions_happyPath_returnsPermissions() {
        when(bankaCoreClient.getUserPermissions("nikola.milenkovic@banka.rs"))
                .thenReturn(List.of("SUPERVISOR", "TRADE_STOCKS"));

        List<String> result = resolver.resolvePermissions("nikola.milenkovic@banka.rs");

        assertThat(result).containsExactlyInAnyOrder("SUPERVISOR", "TRADE_STOCKS");
    }

    @Test
    void resolvePermissions_nullOrBlankEmail_returnsEmptyWithoutHittingClient() {
        assertThat(resolver.resolvePermissions(null)).isEmpty();
        assertThat(resolver.resolvePermissions("")).isEmpty();
        assertThat(resolver.resolvePermissions("   ")).isEmpty();

        verify(bankaCoreClient, times(0)).getUserPermissions(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void resolvePermissions_cachesSuccess_secondCallDoesNotReHitClient() {
        when(bankaCoreClient.getUserPermissions("tamara.pavlovic@banka.rs"))
                .thenReturn(List.of("AGENT", "TRADE_STOCKS"));

        List<String> first = resolver.resolvePermissions("tamara.pavlovic@banka.rs");
        List<String> second = resolver.resolvePermissions("tamara.pavlovic@banka.rs");

        assertThat(first).isEqualTo(second);
        // Caffeine kes: uspesan rezultat se kesira — banka-core pogodjen tacno jednom.
        verify(bankaCoreClient, times(1)).getUserPermissions("tamara.pavlovic@banka.rs");
    }

    @Test
    void resolvePermissions_bankaCoreFails_returnsEmptyList() {
        when(bankaCoreClient.getUserPermissions("blip@banka.rs"))
                .thenThrow(new BankaCoreClientException(503, "service unavailable"));

        List<String> result = resolver.resolvePermissions("blip@banka.rs");

        assertThat(result).isEmpty();
    }

    @Test
    void resolvePermissions_failedLookupIsNotCached_subsequentCallReHitsClient() {
        // 1. poziv: banka-core padne -> prazna lista, NE sme se kesirati.
        // 2. poziv: banka-core se oporavio -> mora ponovo biti pogodjen i vratiti permisije.
        when(bankaCoreClient.getUserPermissions("supervisor@banka.rs"))
                .thenThrow(new BankaCoreClientException(503, "transient banka-core blip"))
                .thenReturn(List.of("SUPERVISOR"));

        List<String> first = resolver.resolvePermissions("supervisor@banka.rs");
        List<String> second = resolver.resolvePermissions("supervisor@banka.rs");

        assertThat(first).isEmpty();
        // Kljucna I3 invarijanta: neuspeh se NE kesira -> 2. poziv ponovo gadja
        // banka-core i supervizor dobija SUPERVISOR autoritet odmah po oporavku.
        assertThat(second).containsExactly("SUPERVISOR");
        verify(bankaCoreClient, times(2)).getUserPermissions("supervisor@banka.rs");
    }
}
