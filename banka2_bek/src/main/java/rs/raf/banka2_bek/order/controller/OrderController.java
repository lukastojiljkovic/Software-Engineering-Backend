package rs.raf.banka2_bek.order.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.service.OrderService;
import rs.raf.banka2_bek.otp.service.OtpService;

import java.util.Map;

/**
 * Controller za kreiranje i upravljanje orderima.
 * SecurityConfig je vec konfigurisan za ove rute.
 */
@Tag(name = "Orders", description = "Kreiranje i upravljanje nalozima za trgovinu hartijama od vrednosti")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OtpService otpService;

    /**
     * POST /orders - Kreiranje novog ordera (BUY ili SELL)
     * Pristup: aktuari i klijenti sa permisijom za trgovinu.
     *
     * OTP se verifikuje u istoj transakciji kao i kreiranje ordera.
     * Ako kreiranje ordera pukne, rollback ponistava i OTP used=true marking
     * (OtpService.verify koristi default Propagation.REQUIRED).
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderDto dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null ? auth.getName() : null;
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Neautorizovan pristup"));
        }

        Map<String, Object> otpResult = otpService.verify(email, dto.getOtpCode());
        if (!Boolean.TRUE.equals(otpResult.get("verified"))) {
            String message = (String) otpResult.getOrDefault("message", "Verifikacija neuspesna");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", message));
        }

        OrderDto response = orderService.createOrder(dto);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /orders - Pregled svih ordera (supervizor portal)
     * Filtriranje po statusu: ALL, PENDING, APPROVED, DECLINED, DONE
     */
    @GetMapping
    public ResponseEntity<Page<OrderDto>> getAllOrders(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.getAllOrders(status, page, size));
    }

    /**
     * GET /orders/my - Moji orderi (za korisnika)
     */
    @GetMapping("/my")
    public ResponseEntity<Page<OrderDto>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.getMyOrders(page, size));
    }

    /**
     * GET /orders/{id} - Detalji jednog ordera
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    /**
     * PATCH /orders/{id}/approve - Supervizor odobrava order
     */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<OrderDto> approveOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.approveOrder(id));
    }

    /**
     * PATCH /orders/{id}/decline - Supervizor odbija order
     */
    @PatchMapping("/{id}/decline")
    public ResponseEntity<OrderDto> declineOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.declineOrder(id));
    }
}
