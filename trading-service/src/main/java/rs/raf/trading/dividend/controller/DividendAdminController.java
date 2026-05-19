package rs.raf.trading.dividend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rs.raf.trading.dividend.dto.DividendPayoutDto;
import rs.raf.trading.dividend.service.DividendService;

import java.time.LocalDate;

/**
 * REST kontroler za admin/supervisor pregled istorije dividendi (B9).
 *
 * <p>Security: rute su vec definisane u {@code TradingSecurityConfig}:
 * <ul>
 *   <li>{@code /admin/dividends/**} → ADMIN ili SUPERVISOR</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin/dividends")
@RequiredArgsConstructor
public class DividendAdminController {

    private final DividendService dividendService;

    /**
     * Admin/supervisor pregled svih isplata dividendi, paginiran.
     * Opcionalni filteri {@code from} i {@code to} suzavaju opseg datuma.
     * Ako je zadat samo {@code from}, {@code to} se defaultuje na danasnji datum.
     *
     * <p>GET /admin/dividends
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ADMIN','SUPERVISOR')")
    public ResponseEntity<Page<DividendPayoutDto>> getAdminDividendHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "paymentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(dividendService.getAdminDividendHistory(from, to, pageable));
    }
}
