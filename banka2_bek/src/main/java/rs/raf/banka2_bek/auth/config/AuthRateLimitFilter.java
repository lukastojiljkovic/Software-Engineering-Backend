package rs.raf.banka2_bek.auth.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-IP rate limiting na auth endpoint-ima koji uzimaju kredencijale ili token-e:
 * <ul>
 *   <li>{@code POST /auth/login} — sprecava brute-force lozinki</li>
 *   <li>{@code POST /auth/refresh} — sprecava refresh-token spam-ovanje</li>
 *   <li>{@code POST /auth/password_reset/request} — sprecava email bombing</li>
 *   <li>{@code POST /auth-employee/activate} — sprecava token guess</li>
 * </ul>
 *
 * Limit: <b>10 zahteva u 60s po IP-u</b>. Token-bucket algoritam (Bucket4j) — ako
 * korisnik kucka pravilan password, prosao je u <50ms i ima jos 9 token-a; ako napadac
 * gada 1000 lozinki/min iz iste IP, dobija 429 Too Many Requests posle 10. zahteva.
 *
 * <p>In-memory mapa po IP-u. Za multi-instance produkciju treba prebaciti na
 * Bucket4j JCache backend (Caffeine/Redis), ali za jednu BE instancu je dovoljno.
 *
 * <p>Filter se izvrsava PRE {@code JwtAuthenticationFilter}-a — neuspeli login
 * pokusaji ne moraju ni da idu kroz JWT auth chain.
 */
@Component
// `auth.rate-limit.enabled=false` u test profile-u potpuno gasi filter (bean nije
// registrovan, GlobalSecurityConfig ga injektuje sa @Autowired(required=false) ekvivalentom
// preko Spring constructor injection-a). matchIfMissing=true znaci default = enabled.
@ConditionalOnProperty(name = "auth.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    /**
     * Kapacitet je config-driven preko {@code auth.rate-limit.capacity} property-ja.
     * Production vrednost = 10/min (sigurnost). Integration testovi koji ispaljuju
     * 50-100 zahteva po sekundi prema istom endpoint-u override-uju u
     * {@code application-test.properties} sa visokim capacity-jem (npr. 100000).
     */
    private final int capacity;
    private static final Duration WINDOW = Duration.ofSeconds(60);

    public AuthRateLimitFilter(@Value("${auth.rate-limit.capacity:10}") int capacity) {
        this.capacity = capacity;
    }

    /**
     * IP → Bucket cache.
     *
     * BE-AUTH-08 fix: prethodno je bila {@code ConcurrentHashMap} koja nikad nije
     * evict-ovala unose, sto pod botnet napadom (1000 razlicitih source IP-ova
     * iz iste mreze, ili spoofed XFF header-ima) raste neograniceno → memory leak.
     *
     * Caffeine cache sa {@code expireAfterAccess(15min)} (~3x window) i
     * {@code maximumSize(50_000)} resava oba problema:
     * <ul>
     *   <li>idle IP-ovi koji nisu udarili u zadnjih 15min se brisu (3x bigger od
     *       60s window-a — sigurno duže od bilo kog rate-limit zahteva)</li>
     *   <li>maximumSize bound osigurava da napad sa milionima jedinstvenih IP-ova
     *       ne moze da rasporedi sve dostupne memorije (Caffeine LRU evicts najstarije)</li>
     * </ul>
     *
     * Side-effect (acceptable): napadac koji sa istim IP-em udari samo jednom u
     * 15min i onda 60s+ kasnije ponovo, dobija fresh bucket — ali to je manje strogo
     * od originalnog ponasanja samo na granici (15min idle). U praksi: brute-force
     * pokusaji su sustained, evict nije problem.
     */
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(15))
            .maximumSize(50_000)
            .build();

    private static final java.util.Set<String> RATE_LIMITED_PATHS = java.util.Set.of(
            "/auth/login",
            "/auth/refresh",
            "/auth/password_reset/request",
            "/auth-employee/activate",
            // SEC-09: rate-limit OTP verifikacije po IP-u — kombinovano sa per-email
            // counter-om u OtpService (3 fail -> blocked=true). Filter koristi isti
            // capacity (default 10/min), gadja oba glavna OTP entry-point-a:
            // - /payments/verify: klijentski payment OTP flow (PaymentController)
            // - /otp/verify: alias / forward-compat (ako BE doda public route)
            "/payments/verify",
            "/otp/verify"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !RATE_LIMITED_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        // BE-AUTH-08: Caffeine.get(key, fn) je atomic + thread-safe; null vrednost
        // se nikad ne kesira jer newBucket() uvek vraca non-null.
        Bucket bucket = buckets.get(clientIp, ip -> newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            // 429 Too Many Requests — RFC 6585. Retry-After hint = 60s window.
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(WINDOW.getSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"Too many requests\",\"message\":\"Limit od " + capacity
                            + " zahteva u " + WINDOW.getSeconds() + "s je premasen. "
                            + "Pokusajte ponovo za " + WINDOW.getSeconds() + " sekundi.\"}");
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillIntervally(capacity, WINDOW).build())
                .build();
    }

    /**
     * X-Forwarded-For prefer ako iza nginx/cloudflare proxy-ja.
     * Spec za XFF: prvi IP u listi je client.
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}
