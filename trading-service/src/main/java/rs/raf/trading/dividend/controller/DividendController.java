package rs.raf.trading.dividend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.raf.trading.dividend.dto.DividendPayoutDto;
import rs.raf.trading.dividend.service.DividendService;

import java.util.List;

/**
 * REST kontroler za korisnicki pregled istorije dividendi (B9).
 *
 * <p>Security: rute su vec definisane u {@code TradingSecurityConfig}:
 * <ul>
 *   <li>{@code /dividends/**} → authenticated()</li>
 * </ul>
 */
@RestController
@RequestMapping("/dividends")
@RequiredArgsConstructor
public class DividendController {

    private final DividendService dividendService;

    /**
     * Vraca sve dividende ulogovanog korisnika (CLIENT ili EMPLOYEE),
     * sortirano paymentDate DESC.
     *
     * <p>GET /dividends/my
     */
    @GetMapping("/my")
    public ResponseEntity<List<DividendPayoutDto>> getMyDividendHistory() {
        return ResponseEntity.ok(dividendService.getMyDividendHistory());
    }

    /**
     * Vraca istoriju dividendi za konkretnu Portfolio poziciju.
     * Vraca 403 ako ulogovani korisnik ne poseduje tu poziciju.
     *
     * <p>GET /dividends/by-position/{portfolioId}
     */
    @GetMapping("/by-position/{portfolioId}")
    public ResponseEntity<List<DividendPayoutDto>> getDividendHistoryByPosition(
            @PathVariable Long portfolioId) {
        return ResponseEntity.ok(dividendService.getDividendHistoryByPosition(portfolioId));
    }
}
