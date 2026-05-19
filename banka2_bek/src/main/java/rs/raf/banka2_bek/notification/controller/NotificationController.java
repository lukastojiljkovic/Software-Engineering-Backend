package rs.raf.banka2_bek.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserResolver;
import rs.raf.banka2_bek.notification.dto.NotificationDto;
import rs.raf.banka2_bek.notification.service.NotificationService;

import java.util.Map;

/**
 * REST endpoints over the authenticated user's own in-app notifications. The
 * principal is resolved via {@link UserResolver}; ownership of an individual
 * notification is enforced by {@link NotificationService}.
 */
@Tag(name = "Notifications", description = "In-app notifications for the authenticated user")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserResolver userResolver;

    @Operation(
            summary = "List my notifications",
            description = "Returns a paginated list of in-app notifications belonging to the authenticated user, "
                    + "sorted by creation time descending. Pass onlyUnread=true to filter to unread entries only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated notification list returned",
                    content = @Content(schema = @Schema(implementation = NotificationDto.class))),
            @ApiResponse(responseCode = "403", description = "Unauthenticated or access denied",
                    content = @Content)
    })
    @GetMapping
    public ResponseEntity<Page<NotificationDto>> getMyNotifications(
            @Parameter(description = "Zero-based page number (default 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size — number of notifications per page (default 20)")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "When true, returns only unread notifications; omit or false for all")
            @RequestParam(required = false) Boolean onlyUnread) {
        UserContext user = userResolver.resolveCurrent();
        Page<NotificationDto> notifications = notificationService.getMyNotifications(
                user.userId(), user.userRole(), Boolean.TRUE.equals(onlyUnread), page, size);
        return ResponseEntity.ok(notifications);
    }

    @Operation(
            summary = "Unread notification count",
            description = "Returns the total number of unread in-app notifications for the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread count returned as {\"count\": N}",
                    content = @Content(schema = @Schema(example = "{\"count\": 3}"))),
            @ApiResponse(responseCode = "403", description = "Unauthenticated or access denied",
                    content = @Content)
    })
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        UserContext user = userResolver.resolveCurrent();
        Long count = notificationService.getUnreadCount(user.userId(), user.userRole());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(
            summary = "Mark notification as read",
            description = "Marks a single in-app notification as read and returns the updated notification. "
                    + "Returns 403 if the notification does not belong to the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read",
                    content = @Content(schema = @Schema(implementation = NotificationDto.class))),
            @ApiResponse(responseCode = "403", description = "Notification belongs to another user",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Notification with given ID not found",
                    content = @Content)
    })
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markOneRead(
            @Parameter(description = "ID of the notification to mark as read")
            @PathVariable Long id) {
        UserContext user = userResolver.resolveCurrent();
        return ResponseEntity.ok(notificationService.markOneRead(id, user.userId(), user.userRole()));
    }

    @Operation(
            summary = "Mark all notifications as read",
            description = "Marks every unread in-app notification of the authenticated user as read."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "All notifications marked as read"),
            @ApiResponse(responseCode = "403", description = "Unauthenticated or access denied",
                    content = @Content)
    })
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        UserContext user = userResolver.resolveCurrent();
        notificationService.markAllRead(user.userId(), user.userRole());
        return ResponseEntity.noContent().build();
    }
}
