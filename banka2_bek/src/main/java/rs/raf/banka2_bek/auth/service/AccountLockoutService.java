package rs.raf.banka2_bek.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Opciono.2 — Account lockout posle X neuspesnih login pokusaja.
 *
 * Spec Celina 1, Scenario 5: "Posle 4 neuspeha, peti unos pogresne lozinke
 * → sistem zaključava nalog na određeni vremenski period". Spec eksplicitno
 * navodi ovo kao "Za nadogradnju" — opciono.
 *
 * Razlikuje se od {@link rs.raf.banka2_bek.auth.config.AuthRateLimitFilter}:
 *   * RateLimitFilter limitira po IP-u (10 req/min) — sprecava brute force.
 *   * AccountLockout limitira po EMAIL-u (5 failed → 15min lock) —
 *     sprecava credential stuffing kad napadac probava razne IP-e.
 *
 * In-memory implementacija (Caffeine), ne preziva restart. Za production
 * deploy zahteva Redis ili sl. shared store da bi radio kroz multiple
 * instance. Za KT3 demo dovoljno.
 */
@Slf4j
@Service
public class AccountLockoutService {

    /*
     * // TODO [B2 - Validacija + brute-force | Nosilac: Andjela Vilcek]
     *
     * Preraditi servis sa in-memory (Caffeine) na DB-bazirano stanje
     * koristeci nova polja entiteta User.failedLoginAttempts i
     * User.accountLockedUntil (analogno za Employee):
     *
     * 1. BRISANJE Caffeine cache-a:
     *    - Ukloniti polja failedAttempts: Cache<String, AtomicInteger> i
     *      lockedUntil: Cache<String, Instant>.
     *    - Ukloniti @PostConstruct init() metodu.
     *    - Ukloniti @Value lockout property-je (ili ostaviti za timeout
     *      konfiguraciju, po izboru).
     *
     * 2. assertNotLocked(email):
     *    - Ucitati User ili Employee po email-u iz repozitorijuma.
     *    - Proveriti accountLockedUntil != null &&
     *      accountLockedUntil.isAfter(LocalDateTime.now()).
     *    - Ako je lock aktivan, baciti AccountLockedException sa preostalim
     *      vremenom kao i sada.
     *
     * 3. recordFailure(email) — posle 5 uzastopnih neuspesnih pokusaja:
     *    - Uvecati failedLoginAttempts u bazi (UserRepository.save /
     *      EmployeeRepository.save).
     *    - Kad failedLoginAttempts >= maxFailedAttempts, postaviti
     *      accountLockedUntil = LocalDateTime.now().plusMinutes(10).
     *    - Poslati email korisniku o zakljucavanju (koristiti postojeci
     *      MailNotificationService ili ApplicationEventPublisher).
     *    - Baciti AccountLockedException odmah (kao i sada).
     *
     * 4. recordSuccess(email):
     *    - Nulirati failedLoginAttempts = 0.
     *    - Postaviti accountLockedUntil = null (otkljucati nalog).
     *    - Sacuvati u bazi.
     *
     * 5. resetPassword tok u AuthService:
     *    - Posle uspesne promene lozinke, takodje pozvati recordSuccess(email)
     *      da se otkljuca nalog i nulira brojac.
     *
     * VAZNO: metoda mora biti @Transactional jer radi write nad entitetima.
     * Paziti na lazyno ucitavanje entiteta — koristiti explicit fetch ili
     * @Transactional(readOnly = false) u pozivajucem servisu.
     */

    /** Posle ovoliko neuspeha — racun je lock-ovan. */
    @Value("${auth.lockout.max-failed-attempts:5}")
    private int maxFailedAttempts;

    /** Trajanje lock-a u minutama. */
    @Value("${auth.lockout.lock-duration-minutes:15}")
    private int lockDurationMinutes;

    /** Window u kome se broje neuspesi (resetuje se posle uspesnog login-a). */
    @Value("${auth.lockout.attempt-window-minutes:30}")
    private int attemptWindowMinutes;

    private Cache<String, AtomicInteger> failedAttempts;
    private Cache<String, Instant> lockedUntil;

    @PostConstruct
    public void init() {
        this.failedAttempts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(attemptWindowMinutes))
                .maximumSize(50_000)
                .build();
        this.lockedUntil = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(lockDurationMinutes))
                .maximumSize(50_000)
                .build();
    }

    /**
     * Baca {@link AccountLockedException} ako je email trenutno lock-ovan.
     * Treba pozvati pre nego sto se proveri lozinka.
     *
     * Poruka je na srpskom (Bug T1-017 — pre fix-a poruka je bila pomesana
     * SR/EN sto je FE prosledjivao 1:1 korisniku).
     */
    public void assertNotLocked(String email) {
        if (email == null) return;
        String key = normalize(email);
        Instant locked = lockedUntil.getIfPresent(key);
        if (locked != null && locked.isAfter(Instant.now())) {
            long secondsRemaining = locked.getEpochSecond() - Instant.now().getEpochSecond();
            throw new AccountLockedException(formatLockoutMessage(secondsRemaining), secondsRemaining);
        }
    }

    /**
     * Belezi neuspesan pokusaj i lockuje racun ako je dosegnut prag.
     *
     * Spec Sc 5 (Bug T1-015): "posle 4 neuspeha, 5. pokusaj zakljucava nalog".
     * Stari kod je samo postavljao lock na 5. pokusaju ali vracao genericku
     * gresku "Invalid email or password" — korisnik je dobijao lockout alert
     * tek na 6. pokusaju kad je {@code assertNotLocked} pucao. Sad bacamo
     * {@link AccountLockedException} odmah kad pretreknemo prag, tako da
     * korisnik vidi lockout poruku na samom 5. pokusaju.
     */
    public void recordFailure(String email) {
        if (email == null) return;
        String key = normalize(email);
        AtomicInteger count = failedAttempts.get(key, k -> new AtomicInteger(0));
        int attempts = count.incrementAndGet();
        if (attempts >= maxFailedAttempts) {
            Instant lockUntil = Instant.now().plus(Duration.ofMinutes(lockDurationMinutes));
            lockedUntil.put(key, lockUntil);
            failedAttempts.invalidate(key);
            log.warn("Account locked: {} ({} failed attempts)", key, attempts);
            long secondsRemaining = lockUntil.getEpochSecond() - Instant.now().getEpochSecond();
            throw new AccountLockedException(formatLockoutMessage(secondsRemaining), secondsRemaining);
        }
    }

    private String formatLockoutMessage(long secondsRemaining) {
        long minutes = Math.max(1, secondsRemaining / 60 + 1);
        return "Nalog je privremeno zakljucan zbog previse neuspesnih pokusaja. "
                + "Pokusajte ponovo za " + minutes + " min.";
    }

    /**
     * Resetuje brojac (po uspesnom login-u).
     */
    public void recordSuccess(String email) {
        if (email == null) return;
        String key = normalize(email);
        failedAttempts.invalidate(key);
        // Lock se zadrzava ako je vec aktivan — ne unlock-ujemo eksplicitno;
        // korisnik ce moci tek posle isteka.
    }

    /** Vraca trenutni broj neuspesa za email; 0 ako nema upisa. */
    public int getFailureCount(String email) {
        if (email == null) return 0;
        AtomicInteger count = failedAttempts.getIfPresent(normalize(email));
        return count != null ? count.get() : 0;
    }

    /** Vraca true ako je email trenutno lock-ovan. */
    public boolean isLocked(String email) {
        if (email == null) return false;
        Instant locked = lockedUntil.getIfPresent(normalize(email));
        return locked != null && locked.isAfter(Instant.now());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Throw-ovan kad korisnik pokusa login na lock-ovan racun.
     */
    public static class AccountLockedException extends RuntimeException {
        private final long secondsRemaining;

        public AccountLockedException(String message, long secondsRemaining) {
            super(message);
            this.secondsRemaining = secondsRemaining;
        }

        public long getSecondsRemaining() {
            return secondsRemaining;
        }
    }
}
