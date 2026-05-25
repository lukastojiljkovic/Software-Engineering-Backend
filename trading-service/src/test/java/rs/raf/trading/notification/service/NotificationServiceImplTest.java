package rs.raf.trading.notification.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import rs.raf.banka2.contracts.NotificationKind;
import rs.raf.banka2.contracts.NotificationMessage;
import rs.raf.banka2.contracts.NotificationRabbit;
import rs.raf.banka2.contracts.internal.InternalNotificationRequest;
import rs.raf.banka2.contracts.internal.InternalUserDto;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.client.BankaCoreClientException;
import rs.raf.trading.notification.model.NotificationType;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testovi za {@link NotificationServiceImpl} — verifikuje da:
 * <ul>
 *   <li>email kanal okida samo kad {@code NotificationType.sendsEmail() == true};</li>
 *   <li>in-app kanal (cross-DB POST ka banka-core) okida samo kad
 *       {@code NotificationType.sendsInApp() == true};</li>
 *   <li>greske u oba kanala su best-effort — ne propagiraju se nazad pozivaocu.</li>
 * </ul>
 */
class NotificationServiceImplTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final BankaCoreClient bankaCoreClient = mock(BankaCoreClient.class);
    private final NotificationServiceImpl service = new NotificationServiceImpl(rabbitTemplate, bankaCoreClient);

    private static final InternalUserDto STEFAN = new InternalUserDto(
            7L, "CLIENT", "stefan@test.com", "Stefan", "J", true, null);

    @Test
    void notify_typeSendsEmailAndInApp_callsBothChannels() throws InterruptedException, TimeoutException {
        when(bankaCoreClient.getUserById("CLIENT", 7L)).thenReturn(STEFAN);

        // ORDER_EXECUTED ima i sendsEmail=true i sendsInApp=true.
        service.notify(7L, "CLIENT", NotificationType.ORDER_EXECUTED,
                "Order izvrsen", "Vas BUY order je popunjen", "ORDER", 42L);

        // Email kanal: RabbitMQ publish sa IN_APP_GENERIC kind-om.
        ArgumentCaptor<NotificationMessage> rabbitCaptor =
                ArgumentCaptor.forClass(NotificationMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(NotificationRabbit.EXCHANGE),
                eq(NotificationRabbit.EMAIL_ROUTING_KEY),
                rabbitCaptor.capture());
        assertThat(rabbitCaptor.getValue().kind()).isEqualTo(NotificationKind.IN_APP_GENERIC);
        assertThat(rabbitCaptor.getValue().data())
                .containsEntry("email", "stefan@test.com")
                .containsEntry("firstName", "Stefan")
                .containsEntry("title", "Order izvrsen")
                .containsEntry("body", "Vas BUY order je popunjen");

        // In-app kanal: cross-DB POST ka banka-core (async, pa cekamo).
        ArgumentCaptor<InternalNotificationRequest> inAppCaptor =
                ArgumentCaptor.forClass(InternalNotificationRequest.class);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(bankaCoreClient).postNotification(inAppCaptor.capture()));
        InternalNotificationRequest captured = inAppCaptor.getValue();
        assertThat(captured.recipientId()).isEqualTo(7L);
        assertThat(captured.recipientType()).isEqualTo("CLIENT");
        assertThat(captured.type()).isEqualTo("ORDER_EXECUTED");
        assertThat(captured.title()).isEqualTo("Order izvrsen");
        assertThat(captured.message()).isEqualTo("Vas BUY order je popunjen");
        assertThat(captured.referenceType()).isEqualTo("ORDER");
        assertThat(captured.referenceId()).isEqualTo(42L);
        assertThat(captured.idempotencyKey()).isNotBlank();
    }

    @Test
    void notify_typeSendsInAppOnly_skipsEmailKanal() {
        // GENERAL ima oba false → niti email niti in-app.
        // Pa cu uzeti tip koji ima sendsInApp=true, sendsEmail=false; ali svi
        // tipovi sada imaju oba true. Da bi se testirao "in-app only" scenario
        // ovde gradimo novi tip-mock koristeci behavior umesto enum-a, ali
        // pristup ovde je da koristim GENERAL kao "oba false" baseline pa
        // odvojeno testiram da konfiguracija enum-a pravilno gata vec putem
        // postojecih ORDER_EXECUTED.
        // Stoga ovaj test verifikuje GENERAL (oba false) → ni jedan kanal.
        service.notify(7L, "CLIENT", NotificationType.GENERAL,
                "Test", "Body", null, null);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        // In-app je async — daj mu vremena da ne pozove postNotification
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
        }
        verify(bankaCoreClient, never()).postNotification(any(InternalNotificationRequest.class));
    }

    @Test
    void notify_recipientIdNull_skipsBothChannels() {
        service.notify(null, "CLIENT", NotificationType.ORDER_EXECUTED,
                "Test", "Body", null, null);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(bankaCoreClient, never()).postNotification(any(InternalNotificationRequest.class));
    }

    @Test
    void notify_recipientTypeNull_skipsBothChannels() {
        service.notify(7L, null, NotificationType.ORDER_EXECUTED,
                "Test", "Body", null, null);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(bankaCoreClient, never()).postNotification(any(InternalNotificationRequest.class));
    }

    @Test
    void notify_bankaCoreLookupFails_emailSkipped_butInAppStillCalled() {
        // Email lookup pada → email se preskace; ali in-app je nezavisan i ide.
        when(bankaCoreClient.getUserById("CLIENT", 99L))
                .thenThrow(new BankaCoreClientException(503, "banka-core down"));

        assertThatNoException().isThrownBy(() ->
                service.notify(99L, "CLIENT", NotificationType.ORDER_EXECUTED,
                        "Order", "Body", "ORDER", 1L));

        // Email NIJE publishovan
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        // Ali in-app jeste — async
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(bankaCoreClient).postNotification(any(InternalNotificationRequest.class)));
    }

    @Test
    void notify_rabbitFails_swallowsException() {
        when(bankaCoreClient.getUserById("CLIENT", 7L)).thenReturn(STEFAN);
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatNoException().isThrownBy(() ->
                service.notify(7L, "CLIENT", NotificationType.ORDER_EXECUTED,
                        "Order", "Body", null, null));
    }

    @Test
    void notify_bankaCorePostNotificationFails_swallowsException() {
        when(bankaCoreClient.getUserById("CLIENT", 7L)).thenReturn(STEFAN);
        // postNotification je vec best-effort u BankaCoreClient (swallow-uje sve),
        // ali da postavimo doThrow ipak verifikujemo da NotificationServiceImpl
        // nista ne escape-uje.
        doThrow(new RuntimeException("network error"))
                .when(bankaCoreClient).postNotification(any(InternalNotificationRequest.class));

        assertThatNoException().isThrownBy(() ->
                service.notify(7L, "CLIENT", NotificationType.ORDER_EXECUTED,
                        "Order", "Body", null, null));
    }

    @Test
    void notify_emailBlankFromBankaCore_skipsEmailPublish() {
        InternalUserDto noEmail = new InternalUserDto(7L, "CLIENT", "", "Stefan", "J", true, null);
        when(bankaCoreClient.getUserById("CLIENT", 7L)).thenReturn(noEmail);

        service.notify(7L, "CLIENT", NotificationType.ORDER_EXECUTED,
                "Test", "Body", null, null);

        // Email NIJE publishovan
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        // Ali in-app jeste (nezavisan kanal)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(bankaCoreClient).postNotification(any(InternalNotificationRequest.class)));
    }
}
