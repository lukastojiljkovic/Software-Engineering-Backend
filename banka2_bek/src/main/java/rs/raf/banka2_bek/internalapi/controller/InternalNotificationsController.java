package rs.raf.banka2_bek.internalapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.raf.banka2.contracts.internal.InternalErrorDto;
import rs.raf.banka2.contracts.internal.InternalNotificationRequest;
import rs.raf.banka2_bek.internalapi.service.InternalIdempotencyService;
import rs.raf.banka2_bek.notification.service.NotificationService;

import java.util.Optional;

/**
 * Cross-DB ulaz za in-app notifikacije iz trading-service-a (i drugih
 * mikroservisa) — banka-core je vlasnik {@code notifications} tabele.
 *
 * <p>{@code InternalAuthFilter} stiti {@code /internal/**} rute deljenim
 * {@code X-Internal-Key} kljucem; nije potrebna dodatna autentifikacija.
 *
 * <p>{@code idempotencyKey} u telu (UUID) sprecava dupli upis pri retry-u —
 * naredni POST sa istim kljucem vraca 200 OK bez ponovnog perzistiranja.
 * Ako kljuc nije prosledjen, idempotency provera se preskace (svaki poziv
 * je svez upis).
 */
@Slf4j
@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationsController {

    private static final String ENDPOINT_PATH = "/internal/notifications";

    private final NotificationService notificationService;
    private final InternalIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public InternalNotificationsController(NotificationService notificationService,
                                           InternalIdempotencyService idempotencyService,
                                           ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> postNotification(@RequestBody InternalNotificationRequest req) {
        if (req == null || req.recipientId() == null || req.recipientType() == null
                || req.recipientType().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new InternalErrorDto("BAD_REQUEST",
                            "recipientId i recipientType su obavezni"));
        }

        String idempotencyKey = req.idempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<rs.raf.banka2_bek.internalapi.model.InternalRequest> cached =
                    idempotencyService.findCached(idempotencyKey);
            if (cached.isPresent()) {
                // Idempotent replay — vec smo upisali; vrati 200 bez novog reda.
                return ResponseEntity.ok().build();
            }
        }

        notificationService.createInternalNotification(req);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyService.store(idempotencyKey, ENDPOINT_PATH,
                        HttpStatus.CREATED.value(), objectMapper.writeValueAsString(""));
            } catch (Exception ex) {
                // Idempotency store je best-effort — notifikacija je vec save-ovana
                log.warn("Failed to store idempotency record for key {}: {}",
                        idempotencyKey, ex.getMessage());
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
