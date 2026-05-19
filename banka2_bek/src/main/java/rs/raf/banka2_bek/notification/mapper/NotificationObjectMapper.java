package rs.raf.banka2_bek.notification.mapper;

import rs.raf.banka2_bek.notification.dto.NotificationDto;
import rs.raf.banka2_bek.notification.model.Notification;

/**
 * [B1] Static utility for converting {@link Notification} JPA entities to
 * {@link NotificationDto} REST response objects.
 */
public final class NotificationObjectMapper {

    private NotificationObjectMapper() {
    }

    /**
     * Converts a {@link Notification} entity to its REST response form.
     *
     * <p>The {@code type} field is set to the enum constant name
     * (e.g. {@code "PAYMENT"}, {@code "ORDER_APPROVED"}) so the frontend can
     * switch on it to render type-specific icons or deep-links.
     * Nullable fields {@code referenceType} and {@code referenceId} are passed
     * through as-is.
     *
     * @param notification the entity to convert; must not be {@code null}
     * @return the corresponding DTO
     */
    public static NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .type(notification.getNotificationType().name())
                .title(notification.getTitle())
                .body(notification.getBody())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .build();
    }
}
