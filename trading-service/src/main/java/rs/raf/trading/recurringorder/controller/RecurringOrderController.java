package rs.raf.trading.recurringorder.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.trading.recurringorder.dto.CreateRecurringOrderDto;
import rs.raf.trading.recurringorder.dto.RecurringOrderDto;
import rs.raf.trading.recurringorder.service.RecurringOrderService;

import java.util.List;

// ============================================================
// [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic] - DONE
//
// REST kontroler za upravljanje trajnim nalozima. Base path: /recurring-orders
//
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
@RestController
@RequestMapping("/recurring-orders")
@RequiredArgsConstructor
public class RecurringOrderController {

    private final RecurringOrderService recurringOrderService;

    @PostMapping
    public ResponseEntity<RecurringOrderDto> create(@Valid @RequestBody CreateRecurringOrderDto dto) {
        RecurringOrderDto created = recurringOrderService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<RecurringOrderDto>> listMy() {
        List<RecurringOrderDto> orders = recurringOrderService.listMy();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringOrderDto> getById(@PathVariable Long id) {
        RecurringOrderDto order = recurringOrderService.getById(id);
        return ResponseEntity.ok(order);
    }

    @PatchMapping("/{id}/pause")
    public ResponseEntity<RecurringOrderDto> pause(@PathVariable Long id) {
        RecurringOrderDto paused = recurringOrderService.pause(id);
        return ResponseEntity.ok(paused);
    }

    @PatchMapping("/{id}/resume")
    public ResponseEntity<RecurringOrderDto> resume(@PathVariable Long id) {
        RecurringOrderDto resumed = recurringOrderService.resume(id);
        return ResponseEntity.ok(resumed);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        recurringOrderService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
