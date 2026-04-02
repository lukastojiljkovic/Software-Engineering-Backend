package rs.raf.banka2_bek.order.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler za ciscenje isteklih ordera.
 *
 * Pokrece se svaki dan u 01:00 ujutru.
 * Pronalazi sve APPROVED ordere kojima je prosao settlement datum
 * i postavlja ih na DECLINED sa razlogom "Settlement date expired".
 *
 * Specifikacija: Celina 3 - Orderi
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCleanupScheduler {

    private final OrderRepository orderRepository;

    /**
     * Dnevno ciscenje isteklih ordera — pokrece se u 01:00 ujutru.
     *
     * Cron format: sekunda minut sat dan-u-mesecu mesec dan-u-nedelji
     * "0 0 1 * * *" = 01:00:00 svakog dana
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void cleanupExpiredOrders() {

        log.info("Pokrecem ciscenje isteklih ordera...");

        List<Order> approvedOrders = orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED);
        LocalDateTime now = LocalDateTime.now();
        int declinedCount = 0;

        for (Order order : approvedOrders) {
            if (order.getLastModification() != null && order.getLastModification().isBefore(now.minusDays(1))) {
                 order.setStatus(OrderStatus.DECLINED);
                 order.setApprovedBy("SYSTEM - Settlement date expired");
                 order.setLastModification(now);
                 orderRepository.save(order);
                 log.info("Order {} (user={}, listing={}) declined - settlement date expired",
                          order.getId(), order.getUserId(), order.getListing().getId());
                 declinedCount++;
             }
         }

        log.info("Ciscenje isteklih ordera zavrseno. Ukupno odbijeno: {}", declinedCount);
    }
}
