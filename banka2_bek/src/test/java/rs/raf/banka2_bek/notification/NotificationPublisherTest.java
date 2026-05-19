package rs.raf.banka2_bek.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import rs.raf.banka2.contracts.NotificationKind;
import rs.raf.banka2.contracts.NotificationMessage;
import rs.raf.banka2.contracts.NotificationRabbit;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class NotificationPublisherTest {

    private final RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
    private final NotificationPublisher publisher = new NotificationPublisher(rabbitTemplate);

    @Test
    void otp_publishesOtpMessageWithStringFields() {
        publisher.sendOtpMail("a@b.rs", "123456", 5);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(rabbitTemplate).convertAndSend(eqEx(), eqRk(), captor.capture());
        NotificationMessage msg = captor.getValue();
        assertThat(msg.kind()).isEqualTo(NotificationKind.OTP);
        assertThat(msg.data()).containsEntry("email", "a@b.rs")
                .containsEntry("code", "123456")
                .containsEntry("expiryMinutes", "5");
    }

    @Test
    void payment_serializesBigDecimalAndLocalDateAsStrings() {
        publisher.sendPaymentConfirmationMail("a@b.rs", new BigDecimal("99.90"), "EUR",
                "111", "222", LocalDate.of(2026, 5, 18), "COMPLETED");

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(rabbitTemplate).convertAndSend(eqEx(), eqRk(), captor.capture());
        assertThat(captor.getValue().data())
                .containsEntry("amount", "99.90")
                .containsEntry("date", "2026-05-18");
    }

    @Test
    void publishFailure_isSwallowed() {
        Mockito.doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(Mockito.anyString(), Mockito.anyString(),
                        Mockito.any(Object.class));
        // Ne sme da baci.
        publisher.sendCardUnblockedMail("a@b.rs", "1234");
    }

    @Test
    void inAppGeneric_publishesIN_APP_GENERICWithEmailAndCopy() {
        publisher.sendInAppGenericMail("user@banka2.rs", "Marko",
                "Naslov", "Vase placanje je izvrseno.");

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(rabbitTemplate).convertAndSend(eqEx(), eqRk(), captor.capture());
        NotificationMessage msg = captor.getValue();
        assertThat(msg.kind()).isEqualTo(NotificationKind.IN_APP_GENERIC);
        assertThat(msg.data()).containsEntry("email", "user@banka2.rs")
                .containsEntry("firstName", "Marko")
                .containsEntry("title", "Naslov")
                .containsEntry("body", "Vase placanje je izvrseno.");
    }

    @Test
    void inAppGeneric_nullFirstNameBecomesEmptyString() {
        publisher.sendInAppGenericMail("user@banka2.rs", null, "Naslov", "Telo");

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(rabbitTemplate).convertAndSend(eqEx(), eqRk(), captor.capture());
        assertThat(captor.getValue().data())
                .containsEntry("firstName", "")
                .containsEntry("title", "Naslov")
                .containsEntry("body", "Telo");
    }

    // Mockito: kad jedan argument koristi matcher (captor.capture()), SVI argumenti
    // moraju biti matcher-i — zato eq(...) a ne sirov string.
    private static String eqEx() {
        return eq(NotificationRabbit.EXCHANGE);
    }

    private static String eqRk() {
        return eq(NotificationRabbit.EMAIL_ROUTING_KEY);
    }
}
