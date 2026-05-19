package rs.raf.banka2_bek.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.notification.dto.NotificationDto;
import rs.raf.banka2_bek.notification.exception.InAppNotificationException;
import rs.raf.banka2_bek.notification.model.Notification;
import rs.raf.banka2_bek.notification.model.NotificationType;
import rs.raf.banka2_bek.notification.repository.NotificationRepository;
import rs.raf.banka2_bek.notification.service.NotificationServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final Long CLIENT_ID = 5L;
    private static final Long EMPLOYEE_ID = 8L;

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    @Test
    void notify_persistsNotificationAndDelegatesToPublisher() {
        Client client = mock(Client.class);
        when(client.getEmail()).thenReturn("marko@test.rs");
        when(client.getFirstName()).thenReturn("Marko");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));

        notificationService.notify(CLIENT_ID, "CLIENT", NotificationType.PAYMENT,
                "Placanje", "Vase placanje je izvrseno", "PAYMENT", 99L);

        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertEquals(CLIENT_ID, saved.getRecipientId());
        assertEquals("CLIENT", saved.getRecipientType());
        assertEquals(NotificationType.PAYMENT, saved.getNotificationType());
        assertEquals("Placanje", saved.getTitle());
        assertEquals("Vase placanje je izvrseno", saved.getBody());
        assertFalse(saved.isRead());
        assertEquals("PAYMENT", saved.getReferenceType());
        assertEquals(99L, saved.getReferenceId().longValue());

        verify(notificationPublisher).sendInAppGenericMail(
                "marko@test.rs", "Marko", "Placanje", "Vase placanje je izvrseno");
    }

    @Test
    void notify_resolvesEmployeeContactForEmployeeRecipient() {
        Employee employee = mock(Employee.class);
        when(employee.getEmail()).thenReturn("supervizor@banka.rs");
        when(employee.getFirstName()).thenReturn("Nikola");
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        notificationService.notify(EMPLOYEE_ID, "EMPLOYEE", NotificationType.ACCOUNT_LOCKED,
                "Nalog je zakljucan", "Vas nalog je privremeno zakljucan", null, null);

        verify(notificationRepository).save(any(Notification.class));
        verify(notificationPublisher).sendInAppGenericMail(
                "supervizor@banka.rs", "Nikola", "Nalog je zakljucan",
                "Vas nalog je privremeno zakljucan");
    }

    @Test
    void notify_publisherFailureDoesNotRollbackPersistence() {
        // Recipient lookup fails — the service must swallow the exception and
        // still keep the saved Notification (in-app row already persisted).
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

        notificationService.notify(CLIENT_ID, "CLIENT", NotificationType.PAYMENT,
                "Placanje", "telo", null, null);

        verify(notificationRepository).save(any(Notification.class));
        verify(notificationPublisher, never()).sendInAppGenericMail(
                any(), any(), any(), any());
    }

    // [B1 — Test coverage] Verifies that types with sendsEmail=false never trigger the
    // email pipeline. Important for B4 order/OTC types which are in-app only.
    @Test
    void notify_doesNotPublishEmailEventForNonEmailType() {
        notificationService.notify(CLIENT_ID, "CLIENT", NotificationType.ORDER_PENDING,
                "Order na cekanju", "Vas order je kreiran i ceka odobrenje.", null, null);

        verify(notificationRepository).save(any(Notification.class));
        verify(notificationPublisher, never()).sendInAppGenericMail(
                any(), any(), any(), any());
    }

    @Test
    void notify_unrecognisedRecipientTypeDoesNotPublishAndDoesNotPropagate() {
        // Notification still persisted, publisher never invoked because contact
        // resolution throws InAppNotificationException — which the service swallows.
        notificationService.notify(CLIENT_ID, "ROBOT", NotificationType.PAYMENT,
                "Placanje", "telo", null, null);

        verify(notificationRepository).save(any(Notification.class));
        verify(notificationPublisher, never()).sendInAppGenericMail(
                any(), any(), any(), any());
    }

    @Test
    void getMyNotifications_returnsAllWhenOnlyUnreadFalse() {
        Page<Notification> page = new PageImpl<>(List.of(
                notification(false), notification(true), notification(false)));
        when(notificationRepository.findByRecipientIdAndRecipientType(
                eq(CLIENT_ID), eq("CLIENT"), any(Pageable.class))).thenReturn(page);

        Page<NotificationDto> result =
                notificationService.getMyNotifications(CLIENT_ID, "CLIENT", false, 0, 20);

        assertEquals(3, result.getContent().size());
    }

    @Test
    void getMyNotifications_returnsOnlyUnreadWhenFlagTrue() {
        Page<Notification> page = new PageImpl<>(List.of(notification(false)));
        when(notificationRepository.findByRecipientIdAndRecipientTypeAndRead(
                eq(CLIENT_ID), eq("CLIENT"), eq(false), any(Pageable.class))).thenReturn(page);

        Page<NotificationDto> result =
                notificationService.getMyNotifications(CLIENT_ID, "CLIENT", true, 0, 20);

        assertEquals(1, result.getContent().size());
    }

    @Test
    void getUnreadCount_returnsCorrectCount() {
        when(notificationRepository.countByRecipientIdAndRecipientTypeAndRead(EMPLOYEE_ID, "EMPLOYEE", false))
                .thenReturn(7L);

        Long count = notificationService.getUnreadCount(EMPLOYEE_ID, "EMPLOYEE");

        assertEquals(7L, count.longValue());
    }

    @Test
    void markOneRead_updatesReadFlagAndReturnsDto() {
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification(false)));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDto dto = notificationService.markOneRead(10L, CLIENT_ID, "CLIENT");

        verify(notificationRepository).save(notificationCaptor.capture());
        assertTrue(notificationCaptor.getValue().isRead());
        assertTrue(dto.isRead());
    }

    @Test
    void markOneRead_throwsWhenNotificationBelongsToOtherRecipient() {
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification(false)));

        assertThrows(AccessDeniedException.class,
                () -> notificationService.markOneRead(10L, 999L, "CLIENT"));
    }

    @Test
    void markOneRead_throwsWhenRecipientTypeDoesNotMatch() {
        // Notification belongs to CLIENT_ID as a CLIENT; same id but EMPLOYEE type should be denied.
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification(false)));

        assertThrows(AccessDeniedException.class,
                () -> notificationService.markOneRead(10L, CLIENT_ID, "EMPLOYEE"));
    }

    @Test
    void markOneRead_throwsWhenNotificationNotFound() {
        when(notificationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(InAppNotificationException.class,
                () -> notificationService.markOneRead(404L, CLIENT_ID, "CLIENT"));
    }

    @Test
    void markAllRead_delegatesToRepository() {
        notificationService.markAllRead(EMPLOYEE_ID, "EMPLOYEE");

        verify(notificationRepository).markAllReadForRecipient(EMPLOYEE_ID, "EMPLOYEE");
    }

    private Notification notification(boolean read) {
        return Notification.builder()
                .id(1L)
                .recipientId(CLIENT_ID)
                .recipientType("CLIENT")
                .notificationType(NotificationType.GENERAL)
                .title("Naslov")
                .body("Telo")
                .read(read)
                .build();
    }
}
