package rs.raf.banka2_bek.notification.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

/**
 * [B1] {@link ControllerAdvice} scoped to the notification controller package.
 * Maps notification-specific exceptions to structured JSON error responses
 * of the form {@code {"message": "..."}}.
 */
@ControllerAdvice(basePackages = "rs.raf.banka2_bek.notification.controller")
public class InAppNotificationExceptionHandler {

    /** Maps {@link InAppNotificationException} (notification not found) to HTTP 404. */
    @ExceptionHandler(InAppNotificationException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(InAppNotificationException ex) {
        return ResponseEntity.status(404).body(Map.of("message", ex.getMessage()));
    }

    /** Maps {@link AccessDeniedException} (notification belongs to another user) to HTTP 403. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(Map.of("message", ex.getMessage()));
    }

    /** Maps {@link IllegalArgumentException} (malformed request arguments) to HTTP 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
