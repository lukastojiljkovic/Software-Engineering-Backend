package rs.raf.banka2_bek.notification.mapper;

import org.junit.jupiter.api.Test;
import rs.raf.banka2_bek.notification.dto.NotificationDto;
import rs.raf.banka2_bek.notification.model.Notification;
import rs.raf.banka2_bek.notification.model.NotificationType;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NotificationObjectMapperTest {

    @Test
    void toDto_mapsAllFieldsCorrectly() {
        LocalDateTime ts = LocalDateTime.of(2024, 6, 1, 12, 0);
        Notification n = Notification.builder()
                .id(1L)
                .recipientId(5L)
                .recipientType("CLIENT")
                .notificationType(NotificationType.PAYMENT)
                .title("Placanje")
                .body("Vase placanje je izvrseno")
                .read(true)
                .createdAt(ts)
                .referenceType("PAYMENT")
                .referenceId(99L)
                .build();

        NotificationDto dto = NotificationObjectMapper.toDto(n);

        assertEquals(1L, dto.getId());
        assertEquals("PAYMENT", dto.getType());
        assertEquals("Placanje", dto.getTitle());
        assertEquals("Vase placanje je izvrseno", dto.getBody());
        assertTrue(dto.isRead());
        assertEquals(ts, dto.getCreatedAt());
        assertEquals("PAYMENT", dto.getReferenceType());
        assertEquals(99L, dto.getReferenceId());
    }

    @Test
    void toDto_handlesNullReferenceFields() {
        Notification n = Notification.builder()
                .id(2L)
                .recipientId(5L)
                .recipientType("CLIENT")
                .notificationType(NotificationType.GENERAL)
                .title("Obavestenje")
                .body("Telo")
                .read(false)
                .createdAt(LocalDateTime.now())
                .referenceType(null)
                .referenceId(null)
                .build();

        NotificationDto dto = NotificationObjectMapper.toDto(n);

        assertNull(dto.getReferenceType());
        assertNull(dto.getReferenceId());
        assertFalse(dto.isRead());
        assertEquals("GENERAL", dto.getType());
    }
}
