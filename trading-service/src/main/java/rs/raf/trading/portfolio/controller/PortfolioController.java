package rs.raf.trading.portfolio.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.trading.portfolio.dto.PortfolioItemDto;
import rs.raf.trading.portfolio.dto.PortfolioSummaryDto;
import rs.raf.trading.portfolio.service.PortfolioService;

import java.util.List;
import java.util.Map;

/**
 * Controller za portfolio endpointe.
 * Vraca hartije od vrednosti u vlasnistvu korisnika sa trenutnim cenama.
 */
@RestController
@RequestMapping("/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    /**
     * GET /portfolio/my - Lista hartija u vlasnistvu korisnika.
     */
    @GetMapping("/my")
    public ResponseEntity<List<PortfolioItemDto>> getMyPortfolio() {
        return ResponseEntity.ok(portfolioService.getMyPortfolio());
    }

    /**
     * GET /portfolio/summary - Ukupna vrednost, profit, porez.
     */
    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryDto> getSummary() {
        return ResponseEntity.ok(portfolioService.getSummary());
    }

    /**
     * PATCH /portfolio/{id}/public - Postavi broj akcija u javnom rezimu.
     */
    @PatchMapping("/{id}/public")
    public ResponseEntity<PortfolioItemDto> setPublicQuantity(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        int quantity = body.getOrDefault("quantity", 0);
        return ResponseEntity.ok(portfolioService.setPublicQuantity(id, quantity));
    }
}
