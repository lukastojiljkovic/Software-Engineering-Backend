package rs.raf.banka2_bek.otp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.notification.service.MailNotificationService;
import rs.raf.banka2_bek.otp.model.OtpVerification;
import rs.raf.banka2_bek.otp.repository.OtpVerificationRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class OtpService {

    private final OtpVerificationRepository otpRepository;
    private final MailNotificationService mailNotificationService;
    private final int expiryMinutes;
    private final int maxAttempts;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(OtpVerificationRepository otpRepository,
                      MailNotificationService mailNotificationService,
                      @Value("${otp.expiry-minutes:5}") int expiryMinutes,
                      @Value("${otp.max-attempts:3}") int maxAttempts) {
        this.otpRepository = otpRepository;
        this.mailNotificationService = mailNotificationService;
        this.expiryMinutes = expiryMinutes;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void generateAndSend(String email) {
        // Invalidate any existing unused OTP
        otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .ifPresent(existing -> {
                    existing.setUsed(true);
                    otpRepository.save(existing);
                });

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        OtpVerification otp = OtpVerification.builder()
                .email(email)
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryMinutes))
                .build();

        otpRepository.save(otp);

        mailNotificationService.sendOtpMail(email, code, expiryMinutes);
    }

    @Transactional
    public Map<String, Object> verify(String email, String code) {
        OtpVerification otp = otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElse(null);

        if (otp == null) {
            return Map.of(
                    "verified", false,
                    "blocked", false,
                    "message", "Verifikacioni kod nije pronadjen. Zatrazite novi kod.");
        }

        if (otp.isExpired()) {
            otp.setUsed(true);
            otpRepository.save(otp);
            return Map.of(
                    "verified", false,
                    "blocked", false,
                    "message", "Verifikacioni kod je istekao. Zatrazite novi kod.");
        }

        if (otp.getAttempts() >= maxAttempts) {
            otp.setUsed(true);
            otpRepository.save(otp);
            return Map.of(
                    "verified", false,
                    "blocked", true,
                    "message", "Transakcija otkazana - previse neuspesnih pokusaja");
        }

        if (otp.getCode().equals(code)) {
            otp.setUsed(true);
            otpRepository.save(otp);
            return Map.of(
                    "verified", true,
                    "message", "Transakcija uspesno verifikovana");
        }

        otp.setAttempts(otp.getAttempts() + 1);
        otpRepository.save(otp);

        int remaining = maxAttempts - otp.getAttempts();
        if (remaining <= 0) {
            otp.setUsed(true);
            otpRepository.save(otp);
            return Map.of(
                    "verified", false,
                    "blocked", true,
                    "message", "Transakcija otkazana - previse neuspesnih pokusaja");
        }

        return Map.of(
                "verified", false,
                "blocked", false,
                "message", "Pogresan verifikacioni kod. Preostalo pokusaja: " + remaining);
    }
}
