package rs.raf.trading.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rs.raf.trading.client.BankaCoreClient;

import java.time.Duration;
import java.util.List;

/**
 * Razresava per-permisija autoritete zaposlenog ({@code SUPERVISOR}, {@code AGENT},
 * {@code ADMIN}, {@code TRADE_STOCKS} ...) preko banka-core internog API-ja
 * ({@link BankaCoreClient#getUserPermissions}).
 *
 * <p><b>Zasto:</b> JWT izdat od banka-core nosi samo {@code role} claim
 * ({@code ADMIN}/{@code EMPLOYEE}/{@code CLIENT}) — NE nosi permisije. Trgovinske
 * rute i {@code @PreAuthorize} anotacije ({@code InvestmentFundController},
 * {@code ProfitBankController}, {@code ActuaryController}) traze
 * {@code hasAuthority('SUPERVISOR')}-stil autoritete. Bez razresavanja, supervizor
 * (JWT {@code role=EMPLOYEE}) bi dobijao 403 na supervizorske endpoint-e.
 * {@link TradingJwtAuthenticationFilter} ovaj resolver koristi da popuni
 * security context permisijama uz {@code ROLE_<role>}.
 *
 * <p>email -> permisije se kesira (Caffeine, 5 min) — bez kesa bio bi jedan
 * banka-core HTTP poziv po svakom autentifikovanom request-u. TTL ogranicava
 * staleness ako se zaposlenom promene permisije.
 *
 * <p>Rezilijentno: ako banka-core lookup padne (mreza, 5xx, 404), vraca praznu
 * listu — {@link TradingJwtAuthenticationFilter} tada pada na samo {@code ROLE_<role>}
 * i ne rusi request. <b>Neuspesan lookup se NE kesira</b>: kad bi se prazna lista
 * iz neuspeha kesirala 5 min, tranzijentni banka-core ispad bi supervizoru
 * uskratio {@code SUPERVISOR} autoritet (403 na supervizorske rute) i posle
 * oporavka banka-core. Zato se kesira samo uspesan rezultat — sledeci request
 * posle neuspeha ponovo pokusava banka-core.
 */
@Component
public class TradingPermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(TradingPermissionResolver.class);

    private final BankaCoreClient bankaCoreClient;

    /** email -> permisije zaposlenog. */
    private final Cache<String, List<String>> permissionCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public TradingPermissionResolver(BankaCoreClient bankaCoreClient) {
        this.bankaCoreClient = bankaCoreClient;
    }

    /**
     * Vraca listu permisija zaposlenog sa datim email-om. Uspesan rezultat se
     * kesira (5 min TTL); na banka-core gresku vraca praznu listu (rezilijentno)
     * ali je NE kesira — sledeci poziv ponovo gadja banka-core.
     */
    public List<String> resolvePermissions(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        List<String> cached = permissionCache.getIfPresent(email);
        if (cached != null) {
            return cached;
        }
        try {
            List<String> permissions = bankaCoreClient.getUserPermissions(email);
            // kesira se SAMO uspeh — neuspeh ostaje nekesiran da bi se sledeci
            // request ponovo obratio banka-core (vidi I3 obrazlozenje u javadoc-u)
            permissionCache.put(email, permissions);
            return permissions;
        } catch (RuntimeException ex) {
            log.warn("Razresavanje permisija za {} nije uspelo (pad na ROLE-only, ne kesira se): {}",
                    email, ex.getMessage());
            return List.of();
        }
    }
}
