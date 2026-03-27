package rs.raf.banka2_bek.otp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.otp.model.OtpVerification;

import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);
}
