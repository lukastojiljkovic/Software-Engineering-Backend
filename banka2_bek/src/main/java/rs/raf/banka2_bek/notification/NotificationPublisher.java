package rs.raf.banka2_bek.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import rs.raf.banka2.contracts.NotificationKind;
import rs.raf.banka2.contracts.NotificationMessage;
import rs.raf.banka2.contracts.NotificationRabbit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Publish-uje email notifikacione poruke na RabbitMQ. Zamenjuje direktne pozive
 * starog MailNotificationService-a. Best-effort: neuspeh publish-a se loguje,
 * ne rusi poslovnu operaciju (isto ponasanje kao stari try-catch oko email-a).
 */
@Service
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public NotificationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    private void publish(NotificationKind kind, Map<String, String> data) {
        try {
            rabbitTemplate.convertAndSend(NotificationRabbit.EXCHANGE,
                    NotificationRabbit.EMAIL_ROUTING_KEY, new NotificationMessage(kind, data));
        } catch (RuntimeException ex) {
            log.warn("Neuspeh publish-a notifikacije kind={}: {}", kind, ex.getMessage());
        }
    }

    public void sendPasswordResetMail(String toEmail, String token) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("token", token);
        publish(NotificationKind.PASSWORD_RESET, d);
    }

    public void sendActivationMail(String toEmail, String firstName, String token) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("firstName", firstName);
        d.put("activationToken", token);
        publish(NotificationKind.EMPLOYEE_ACCOUNT_CREATED, d);
    }

    public void sendActivationConfirmationMail(String toEmail, String firstName) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("firstName", firstName);
        publish(NotificationKind.EMPLOYEE_ACTIVATION_CONFIRMED, d);
    }

    public void sendAccountCreatedConfirmationMail(String toEmail, String firstName,
                                                   String accountNumber, String accountType) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("firstName", firstName);
        d.put("accountNumber", accountNumber);
        d.put("accountType", accountType);
        publish(NotificationKind.ACCOUNT_CREATED, d);
    }

    public void sendOtpMail(String toEmail, String code, int expiryMinutes) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("code", code);
        d.put("expiryMinutes", Integer.toString(expiryMinutes));
        publish(NotificationKind.OTP, d);
    }

    public void sendPaymentConfirmationMail(String toEmail, BigDecimal amount, String currency,
                                            String fromAccount, String toAccount,
                                            LocalDate date, String status) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("amount", amount.toString());
        d.put("currency", currency);
        d.put("fromAccount", fromAccount);
        d.put("toAccount", toAccount);
        d.put("date", date.toString());
        d.put("status", status);
        publish(NotificationKind.PAYMENT_CONFIRMED, d);
    }

    public void sendCardBlockedMail(String toEmail, String last4Digits, LocalDate blockDate) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("last4Digits", last4Digits);
        d.put("blockDate", blockDate.toString());
        publish(NotificationKind.CARD_BLOCKED, d);
    }

    public void sendCardUnblockedMail(String toEmail, String last4Digits) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("last4Digits", last4Digits);
        publish(NotificationKind.CARD_UNBLOCKED, d);
    }

    public void sendLoanRequestSubmittedMail(String toEmail, String loanType,
                                             BigDecimal amount, String currency) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("loanType", loanType);
        d.put("amount", amount.toString());
        d.put("currency", currency);
        publish(NotificationKind.LOAN_REQUEST_SUBMITTED, d);
    }

    public void sendLoanApprovedMail(String toEmail, String loanNumber, BigDecimal amount,
                                     String currency, BigDecimal monthlyPayment, LocalDate startDate) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("loanNumber", loanNumber);
        d.put("amount", amount.toString());
        d.put("currency", currency);
        d.put("monthlyPayment", monthlyPayment.toString());
        d.put("startDate", startDate.toString());
        publish(NotificationKind.LOAN_APPROVED, d);
    }

    public void sendLoanRejectedMail(String toEmail, String loanType,
                                     BigDecimal amount, String currency) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("loanType", loanType);
        d.put("amount", amount.toString());
        d.put("currency", currency);
        publish(NotificationKind.LOAN_REJECTED, d);
    }

    public void sendInstallmentPaidMail(String toEmail, String loanNumber,
                                        BigDecimal installmentAmount, String currency,
                                        BigDecimal remainingDebt) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("loanNumber", loanNumber);
        d.put("installmentAmount", installmentAmount.toString());
        d.put("currency", currency);
        d.put("remainingDebt", remainingDebt.toString());
        publish(NotificationKind.INSTALLMENT_PAID, d);
    }

    public void sendInstallmentFailedMail(String toEmail, String loanNumber,
                                          BigDecimal amountDue, String currency,
                                          LocalDate nextRetryDate) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("loanNumber", loanNumber);
        d.put("amountDue", amountDue.toString());
        d.put("currency", currency);
        d.put("nextRetryDate", nextRetryDate.toString());
        publish(NotificationKind.INSTALLMENT_FAILED, d);
    }

    /**
     * [B1 — In-app notifications] Publishes a generic in-app email message
     * which {@code notification-service} renders via the branded
     * {@code InAppGenericEmailTemplate}. Subject is the notification title.
     *
     * <p>Caller is {@code NotificationServiceImpl.notify(...)} when the
     * {@code NotificationType} has {@code sendsEmail=true}. The in-app row is
     * already persisted before this method is invoked; failure here is
     * swallowed by the underlying {@link #publish} (best-effort).
     *
     * <p>{@code firstName} may be {@code null} (anonymous greeting will be used
     * on the consumer side); we publish an empty string in that case so the
     * data map has a stable shape.
     */
    public void sendInAppGenericMail(String toEmail, String firstName, String title, String body) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("firstName", firstName == null ? "" : firstName);
        d.put("title", title == null ? "" : title);
        d.put("body", body == null ? "" : body);
        publish(NotificationKind.IN_APP_GENERIC, d);
    }

    /**
     * [B2 — Account lockout] Publishes an email notification telling the user
     * their account has been temporarily locked due to too many failed login
     * attempts. Consumer side renders via {@code AccountLockedEmailTemplate}.
     */
    public void sendAccountLockedMail(String toEmail, int lockMinutes) {
        Map<String, String> d = new HashMap<>();
        d.put("email", toEmail);
        d.put("lockMinutes", Integer.toString(lockMinutes));
        publish(NotificationKind.ACCOUNT_LOCKED, d);
    }
}
