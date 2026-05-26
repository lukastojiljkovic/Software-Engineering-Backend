package rs.raf.notification.messaging;

import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.mail.MailException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import rs.raf.banka2.contracts.NotificationMessage;
import rs.raf.notification.mail.MailNotificationService;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Konzumira NotificationMessage sa RabbitMQ i delegira na MailNotificationService.
 *
 * <p><b>BE-NTF-01 (manual ack + DLQ routing):</b> consumer eksplicitno odlucuje
 * sta uraditi sa porukom na osnovu vrste greske:
 * <ul>
 *   <li><b>OK</b> — basicAck.</li>
 *   <li><b>Transient (MailException)</b> — basicNack(requeue=true). Spring AMQP
 *       vraca poruku u queue; SMTP outage tako ne gubi notifikaciju. (Konfigurisani
 *       retry/backoff parametri su na container factory-ju; ovde samo signal.)</li>
 *   <li><b>Poison (bad payload, NPE, NumberFormatException itd.)</b> —
 *       basicNack(requeue=false). Poruka ide u DLX → DLQ (configured u
 *       {@link RabbitConfig}); ne ulazi u beskonacnu retry petlju.</li>
 * </ul>
 * Kanal i delivery tag su {@code @Nullable} samo iz pragmatickog razloga: postojeci
 * unit testovi pozivaju {@code consumer.handle(message)} sa jednim argumentom.
 * U produkciji ih Spring AMQP uvek injectuje (queue je vezana na manual ack
 * container factory). Kad su null, fallback je legacy swallow-and-log ponasanje.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final MailNotificationService mail;
    /**
     * W2-T1: Micrometer registry za on-demand registraciju email counter-a.
     * Koristi se za {@code banka2_emails_sent_total} (no tags) i
     * {@code banka2_emails_failed_total{reason="smtp_error|dlq"}}.
     * Nullable za backward-compat unit test konstrukciju
     * ({@code new NotificationConsumer(mail)}) — pre wire-up testovi su
     * pravili consumer bez registry-ja.
     */
    @Nullable
    private final MeterRegistry registry;

    /** Backward-compat constructor — koriste ga unit testovi bez Micrometer-a. */
    public NotificationConsumer(MailNotificationService mail) {
        this(mail, null);
    }

    /**
     * Primarni Spring constructor — Spring injectuje {@link MeterRegistry}
     * koji je auto-configured kroz {@code micrometer-registry-prometheus}
     * starter. {@code @Autowired} eksplicitno pokazuje koji constructor
     * koristi container kad ima vise public konstruktora.
     */
    @Autowired
    public NotificationConsumer(MailNotificationService mail, @Nullable MeterRegistry registry) {
        this.mail = mail;
        this.registry = registry;
    }

    // Parametar je tipiziran (NotificationMessage) -> Jackson2JsonMessageConverter
    // deserijalizuje prema tipu metode, ignorise __TypeId__ header; setTrustedPackages
    // NE treba. (Ne menjati param na Object/Message - to menja mod deserijalizacije.)
    /**
     * Backward-compat overload bez channel/tag — koriste ga unit testovi.
     * Kad Spring AMQP invoke-uje listener, MessageListenerAdapter resolve-uje
     * argumente reflection-om i koristi {@link #handle(NotificationMessage, Channel, Long)}
     * varijantu (sa @RabbitListener anotacijom) — jer ima vise argumenata
     * (Spring AMQP bira metodu sa najvise resolvovanih parametara).
     */
    public void handle(NotificationMessage message) {
        handle(message, null, null);
    }

    @RabbitListener(queues = rs.raf.banka2.contracts.NotificationRabbit.EMAIL_QUEUE)
    public void handle(NotificationMessage message,
                       @Nullable Channel channel,
                       @Nullable @Header(value = AmqpHeaders.DELIVERY_TAG, required = false) Long deliveryTag) {
        Map<String, String> d = message.data();
        try {
            dispatch(message, d);
            // W2-T1: brojaj uspesno poslate email-ove.
            incrementCounter("banka2_emails_sent_total");
            ackOrSkip(channel, deliveryTag);
        } catch (MailException ex) {
            // Transient SMTP outage — requeue, ne baca u DLQ. Spring AMQP retry
            // policy (na container factory-ju) odlucuje koliko puta pre nego
            // sto se preda DLX-u.
            log.warn("Transient mail failure za kind={} — requeue u glavni queue", message.kind(), ex);
            // W2-T1: SMTP failure counter (tag reason=smtp_error).
            incrementFailureCounter("smtp_error");
            nackOrSkip(channel, deliveryTag, true);
        } catch (RuntimeException ex) {
            // Poison payload (NPE, NumberFormatException, IllegalArgumentException…) —
            // re-deliveries ce uvek pucati. Salji u DLQ (requeue=false).
            log.error("Poison message za kind={} — saljem u DLQ (requeue=false)", message.kind(), ex);
            // W2-T1: DLQ failure counter (tag reason=dlq).
            incrementFailureCounter("dlq");
            nackOrSkip(channel, deliveryTag, false);
        }
    }

    /** W2-T1: bezbedan inkrement — preskace se ako registry nije configured (unit test path). */
    private void incrementCounter(String name) {
        if (registry == null) {
            return;
        }
        try {
            registry.counter(name).increment();
        } catch (RuntimeException ex) {
            log.warn("Failed to increment counter {}: {}", name, ex.getMessage());
        }
    }

    private void incrementFailureCounter(String reason) {
        if (registry == null) {
            return;
        }
        try {
            registry.counter("banka2_emails_failed_total", "reason", reason).increment();
        } catch (RuntimeException ex) {
            log.warn("Failed to increment failure counter (reason={}): {}", reason, ex.getMessage());
        }
    }

    private void dispatch(NotificationMessage message, Map<String, String> d) {
        switch (message.kind()) {
                case PASSWORD_RESET ->
                        mail.sendPasswordResetMail(d.get("email"), d.get("token"));
                case EMPLOYEE_ACCOUNT_CREATED ->
                        mail.sendActivationMail(d.get("email"), d.get("firstName"), d.get("activationToken"));
                case EMPLOYEE_ACTIVATION_CONFIRMED ->
                        mail.sendActivationConfirmationMail(d.get("email"), d.get("firstName"));
                case ACCOUNT_CREATED ->
                        mail.sendAccountCreatedConfirmationMail(d.get("email"), d.get("firstName"),
                                d.get("accountNumber"), d.get("accountType"));
                case OTP ->
                        mail.sendOtpMail(d.get("email"), d.get("code"), Integer.parseInt(d.get("expiryMinutes")));
                case PAYMENT_CONFIRMED ->
                        mail.sendPaymentConfirmationMail(d.get("email"), new BigDecimal(d.get("amount")),
                                d.get("currency"), d.get("fromAccount"), d.get("toAccount"),
                                LocalDate.parse(d.get("date")), d.get("status"));
                case CARD_BLOCKED ->
                        mail.sendCardBlockedMail(d.get("email"), d.get("last4Digits"),
                                LocalDate.parse(d.get("blockDate")));
                case CARD_UNBLOCKED ->
                        mail.sendCardUnblockedMail(d.get("email"), d.get("last4Digits"));
                case LOAN_REQUEST_SUBMITTED ->
                        mail.sendLoanRequestSubmittedMail(d.get("email"), d.get("loanType"),
                                new BigDecimal(d.get("amount")), d.get("currency"));
                case LOAN_APPROVED ->
                        mail.sendLoanApprovedMail(d.get("email"), d.get("loanNumber"),
                                new BigDecimal(d.get("amount")), d.get("currency"),
                                new BigDecimal(d.get("monthlyPayment")), LocalDate.parse(d.get("startDate")));
                case LOAN_REJECTED ->
                        mail.sendLoanRejectedMail(d.get("email"), d.get("loanType"),
                                new BigDecimal(d.get("amount")), d.get("currency"));
                case INSTALLMENT_PAID ->
                        mail.sendInstallmentPaidMail(d.get("email"), d.get("loanNumber"),
                                new BigDecimal(d.get("installmentAmount")), d.get("currency"),
                                new BigDecimal(d.get("remainingDebt")));
                case INSTALLMENT_FAILED ->
                        mail.sendInstallmentFailedMail(d.get("email"), d.get("loanNumber"),
                                new BigDecimal(d.get("amountDue")), d.get("currency"),
                                LocalDate.parse(d.get("nextRetryDate")));
                case MARGIN_ACCOUNT_BLOCKED ->
                        mail.sendMarginAccountBlockedMail(d.get("email"), d.get("maintenanceMargin"),
                                d.get("initialMargin"), d.get("deficit"));
                case ACCOUNT_LOCKED ->
                        mail.sendAccountLockedMail(d.get("email"), Integer.parseInt(d.get("lockMinutes")));
            case IN_APP_GENERIC -> {
                String email = d.get("email");
                if (email == null || email.isBlank()) {
                    log.warn("IN_APP_GENERIC poruka nema 'email' kljuc — preskacemo slanje");
                    return;
                }
                mail.sendInAppNotificationMail(email, d.get("firstName"), d.get("title"), d.get("body"));
            }
        }
    }

    /** Ack ako su channel/tag dostupni (production). U unit testovima oba su null
     *  pa se preskace (legacy ponasanje, test pisao samo {@code consumer.handle(msg)}). */
    private void ackOrSkip(@Nullable Channel channel, @Nullable Long deliveryTag) {
        if (channel == null || deliveryTag == null) {
            return;
        }
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException io) {
            log.warn("basicAck failed za tag={}", deliveryTag, io);
        }
    }

    /** Nack sa eksplicitnim requeue flag-om. {@code requeue=true} → vraca u glavni queue;
     *  {@code requeue=false} → ide u DLX (kako je konfigurisan u {@link RabbitConfig}). */
    private void nackOrSkip(@Nullable Channel channel, @Nullable Long deliveryTag, boolean requeue) {
        if (channel == null || deliveryTag == null) {
            return;
        }
        try {
            channel.basicNack(deliveryTag, false, requeue);
        } catch (IOException io) {
            log.warn("basicNack failed za tag={} requeue={}", deliveryTag, requeue, io);
        }
    }
}
