package rs.raf.trading.otc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rs.raf.trading.otc.dto.OtcNegotiationHistoryDto;
import rs.raf.trading.otc.service.OtcNegotiationHistoryService;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * B10 — REST kontroler koji izlaze istoriju OTC pregovora.
 * (port iz main PR #89, Aja Timotic — package rename ka rs.raf.trading.otc.*)
 *
 * Pristup: autentifikovan korisnik za {@code /{negotiationId}}; admin i
 * supervisor za paginiran pregled (servis baca {@link
 * org.springframework.security.access.AccessDeniedException}).
 */
@RestController
@RequestMapping("/otc/negotiation-history")
@RequiredArgsConstructor
public class OtcNegotiationHistoryController {

    private final OtcNegotiationHistoryService historyService;

    @GetMapping("/{negotiationId}")
    public ResponseEntity<List<OtcNegotiationHistoryDto>> getHistoryForNegotiation(
            @PathVariable Long negotiationId) {
        return ResponseEntity.ok(historyService.getHistoryForNegotiation(negotiationId));
    }

    @GetMapping
    public ResponseEntity<Page<OtcNegotiationHistoryDto>> getHistory(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long modifiedById,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        LocalDateTime fromDt = parseIsoDateTime(from, "from");
        LocalDateTime toDt = parseIsoDateTime(to, "to");
        return ResponseEntity.ok(
                historyService.findWithFilters(status, modifiedById, fromDt, toDt, page, size));
    }

    private LocalDateTime parseIsoDateTime(String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Parametar '" + paramName + "' nije validan ISO-8601 datum-vreme: " + raw);
        }
    }
}
