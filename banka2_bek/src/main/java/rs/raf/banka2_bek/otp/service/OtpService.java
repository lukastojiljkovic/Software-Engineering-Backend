package rs.raf.banka2_bek.otp.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.notification.NotificationPublisher;
import rs.raf.banka2_bek.otp.repository.TotpSecretRepository;

import java.time.Instant;
import java.util.Map;

@Service
public class OtpService {

    private static final long TOTP_WINDOW_SECONDS = 30L;

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final TotpSecretRepository totpSecretRepository;
    private final NotificationPublisher notificationPublisher;
    private final int emailExpiryMinutes;

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

    @Transactional
    public Map<String, Object> verify(String email, String code) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return Map.of(
                    "verified", false,
                    "blocked", false,
                    "message", "Verifikacioni kod nije pronadjen. Zatrazite novi kod.");
        }

        boolean ok;
        try {
            ok = totpService.verify(user.getId(), code);
        } catch (IllegalStateException ex) {
            return Map.of(
                    "verified", false,
                    "blocked", false,
                    "message", "Verifikacioni kod nije pronadjen. Zatrazite novi kod.");
        }

        if (ok) {
            return Map.of(
                    "verified", true,
                    "message", "Transakcija uspesno verifikovana");
        }

        return Map.of(
                "verified", false,
                "blocked", false,
                "message", "Pogresan verifikacioni kod.");
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
