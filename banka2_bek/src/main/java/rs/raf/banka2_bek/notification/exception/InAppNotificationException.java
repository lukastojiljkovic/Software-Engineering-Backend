package rs.raf.banka2_bek.notification.exception;

/**
 * [B1] Thrown when a notification cannot be found or when an unrecoverable
 * error occurs in the in-app notification flow (e.g. unknown recipient type).
 * Mapped to HTTP 404 by {@link InAppNotificationExceptionHandler}.
 */
public class InAppNotificationException extends RuntimeException {

    public InAppNotificationException(String message) {
        super(message);
    }

}
