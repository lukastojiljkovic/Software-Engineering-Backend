package rs.raf.banka2_bek.berza.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.berza.dto.ExchangeDto;
import rs.raf.banka2_bek.berza.service.ExchangeManagementService;

import java.util.List;
import java.util.Map;

/**
 * REST kontroler za upravljanje berzama.
 *
 * Endpointovi:
 *   GET  /exchanges              - lista svih aktivnih berzi sa statusom
 *   GET  /exchanges/{acronym}    - detalji jedne berze
 *   PATCH /exchanges/{acronym}/test-mode - ukljuci/iskljuci test mode (samo admin)
 *
 * Specifikacija: Celina 3 - Berza
 */
@RestController
@RequestMapping("/exchanges")
@RequiredArgsConstructor
public class ExchangeManagementController {

    private final ExchangeManagementService exchangeManagementService;

    /**
     * GET /exchanges
     * Vraca listu svih aktivnih berzi sa computed statusom (isOpen, currentLocalTime, nextOpenTime).
     *
     */
    @GetMapping
    public ResponseEntity<List<ExchangeDto>> getAllExchanges() {
        return ResponseEntity.ok(exchangeManagementService.getAllExchanges());
    }

    @GetMapping("/{acronym}")
    public ResponseEntity<ExchangeDto> getByAcronym(@PathVariable String acronym) {
        return ResponseEntity.ok(exchangeManagementService.getByAcronym(acronym));
    }

    @PatchMapping("/{acronym}/test-mode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> setTestMode(
            @PathVariable String acronym,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", false);
        exchangeManagementService.setTestMode(acronym, enabled);
        return ResponseEntity.ok(Map.of("message", "Test mode set to " + enabled + " for " + acronym));
    }
}
