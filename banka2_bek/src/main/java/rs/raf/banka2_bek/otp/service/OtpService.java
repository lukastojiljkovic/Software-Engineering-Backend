package rs.raf.banka2_bek.otp.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.notification.NotificationPublisher;
import rs.raf.banka2_bek.otp.repository.TotpSecretRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class OtpService {

    private static final long TOTP_WINDOW_SECONDS = 30L;
    /**
     * BE-AUTH-01 fix (Celina 2 §18, TODO_testovi.pdf Sc 14): nakon 3 neuspesna
     * pokusaja TOTP provere se transakcija otkazuje. Caffeine cache prati fail
     * counter po email-u sa TTL 5 minuta (slidi sa TOTP code prozorom +
     * realisticnim grace periodom).
     */
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final Duration FAILED_ATTEMPTS_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final TotpSecretRepository totpSecretRepository;
    private final NotificationPublisher notificationPublisher;
    private final int emailExpiryMinutes;
    private final Cache<String, Integer> failedAttempts;

    public OtpService(UserRepository userRepository,
                      TotpService totpService,
                      TotpSecretRepository totpSecretRepository,
                      NotificationPublisher notificationPublisher,
                      @Value("${otp.expiry-minutes:5}") int emailExpiryMinutes) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.totpSecretRepository = totpSecretRepository;
        this.notificationPublisher = notificationPublisher;
        this.emailExpiryMinutes = emailExpiryMinutes;
        this.failedAttempts = Caffeine.newBuilder()
                .expireAfterWrite(FAILED_ATTEMPTS_TTL)
                .maximumSize(10_000)
                .build();
    }

    @Transactional
    public void generateAndSend(String email) {
        ensureSecret(email);
    }

    @Transactional
    public void generateAndSendViaEmail(String email) {
        String secret = ensureSecret(email);
        String code = currentCode(secret);
        notificationPublisher.sendOtpMail(email, code, emailExpiryMinutes);
    }

    @Transactional
    public Map<String, Object> getActiveOtp(String email) {
        String secret = ensureSecret(email);
        String code = currentCode(secret);
        long secondsLeft = TOTP_WINDOW_SECONDS - (Instant.now().getEpochSecond() % TOTP_WINDOW_SECONDS);

        return Map.of(
                "active", true,
                "code", code,
                "expiresInSeconds", secondsLeft,
                "attempts", 0,
                "maxAttempts", 0);
    }

    /**
     * Verifikuje TOTP kod za korisnika.
     *
     * <p>BE-AUTH-01: posle {@link #MAX_FAILED_ATTEMPTS} uzastopnih neuspesnih
     * pokusaja u prozoru od {@link #FAILED_ATTEMPTS_TTL}, naredne provere
     * vracaju {@code blocked=true} (caller mora otkazati transakciju).
     * Counter se resetuje pri svakoj uspesnoj verifikaciji.</p>
     */
    @Transactional
    public Map<String, Object> verify(String email, String code) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return Map.of(
                    "verified", false,
                    "blocked", false,
                    "attempts", 0,
                    "maxAttempts", MAX_FAILED_ATTEMPTS,
                    "message", "Verifikacioni kod nije pronadjen. Zatrazite novi kod.");
        }

        // BE-AUTH-01: ako je transakcija vec blokirana, ne diraj counter (TTL
        // window jos uvek vazi). Klijent treba da otkaze i pokrene nov OTP flow.
        Integer currentCount = failedAttempts.getIfPresent(email);
        if (currentCount != null && currentCount >= MAX_FAILED_ATTEMPTS) {
            return Map.of(
                    "verified", false,
                    "blocked", true,
                    "attempts", currentCount,
                    "maxAttempts", MAX_FAILED_ATTEMPTS,
                    "message", "Prekoracen je broj pokusaja. Transakcija je otkazana.");
        }

        boolean ok;
        try {
            ok = totpService.verify(user.getId(), code);
        } catch (IllegalStateException ex) {
            int attempts = recordFailure(email);
            boolean blocked = attempts >= MAX_FAILED_ATTEMPTS;
            return Map.of(
                    "verified", false,
                    "blocked", blocked,
                    "attempts", attempts,
                    "maxAttempts", MAX_FAILED_ATTEMPTS,
                    "message", blocked
                            ? "Prekoracen je broj pokusaja. Transakcija je otkazana."
                            : "Verifikacioni kod nije pronadjen. Zatrazite novi kod.");
        }

        if (ok) {
            failedAttempts.invalidate(email);
            return Map.of(
                    "verified", true,
                    "blocked", false,
                    "attempts", 0,
                    "maxAttempts", MAX_FAILED_ATTEMPTS,
                    "message", "Transakcija uspesno verifikovana");
        }

        int attempts = recordFailure(email);
        boolean blocked = attempts >= MAX_FAILED_ATTEMPTS;
        return Map.of(
                "verified", false,
                "blocked", blocked,
                "attempts", attempts,
                "maxAttempts", MAX_FAILED_ATTEMPTS,
                "message", blocked
                        ? "Prekoracen je broj pokusaja. Transakcija je otkazana."
                        : "Pogresan verifikacioni kod.");
    }

    /**
     * BE-AUTH-01: increment-and-return atomic helper. Caffeine
     * {@code asMap().merge} garantuje at-most-once povecanje po pozivu.
     */
    private int recordFailure(String email) {
        return failedAttempts.asMap().merge(email, 1, Integer::sum);
    }

    private String ensureSecret(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Korisnik nije pronadjen: " + email));

        return totpSecretRepository.findByUserId(user.getId())
                .map(rs.raf.banka2_bek.otp.model.TotpSecret::getSecret)
                .orElseGet(() -> totpService.generateSecret(user.getId()));
    }

    private String currentCode(String secret) {
        int raw = new GoogleAuthenticator().getTotpPassword(secret);
        return String.format("%06d", raw);
    }
}
