package rs.raf.trading.order.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.trading.order.service.OrderExecutionService;
import rs.raf.trading.order.service.StopOrderActivationService;

/**
 * Scheduler komponenta koja periodicno pokrece proveru i izvrsavanje naloga.
 *
 * Specifikacija: Celina 3 - Order Execution Scheduler
 *
 * Dva schedulovana zadatka:
 * 1. processStopOrders — svakih 30 sekundi proverava STOP/STOP_LIMIT naloge
 * 2. executeApprovedOrders — svakih 10 sekundi izvrsava APPROVED MARKET/LIMIT naloge
 *
 * NAPOMENA (copy-first ekstrakcija, faza 2c): {@code @Scheduled} anotacije su
 * zadrzane verbatim, ali su USPAVANE — {@code TradingServiceApplication} nema
 * {@code @EnableScheduling} (kao od pod-faza 2a/2b). Do cutover-a (2f) monolit
 * jos uvek vrti svoje order-execution schedulere; aktiviranje ovde bi izazvalo
 * duplo izvrsavanje + duplo gadjanje banka-core internog API-ja. Cutover (2f)
 * dodaje {@code @EnableScheduling} i gasi monolitnu kopiju.
 */
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderScheduler.class);

    private final StopOrderActivationService stopOrderActivationService;
    private final OrderExecutionService orderExecutionService;

    /**
     * Proverava STOP i STOP_LIMIT naloge svakih 30 sekundi.
     * Ako trzisna cena dostigne stop vrednost, nalog se aktivira
     * (STOP -> MARKET, STOP_LIMIT -> LIMIT).
     */
    @Scheduled(fixedRate = 30000)
    public void processStopOrders() {

        log.debug("Stop order check cycle started");

            try {
                stopOrderActivationService.checkAndActivateStopOrders();
            } catch (Exception e) {
                log.error("Error processing stop orders: {}", e.getMessage(), e);
            }

         /* 2. Opciono: dodati metriku (broj aktiviranih naloga po ciklusu)
             za monitoring putem Actuator-a ili custom metrike.*/

        log.debug("Stop order check cycle completed");
    }

    /**
     * Izvrsava APPROVED naloge (MARKET i LIMIT) svakih 10 sekundi.
     * Za svaki nalog pokusava da izvrsi jedan parcijalni fill.
     */
    @Scheduled(fixedRate = 10000)
    public void executeApprovedOrders() {

        log.debug("Execute approved orders cycle started");

             try {
                 orderExecutionService.executeOrders();
             } catch (Exception e) {
                 log.error("Error executing approved orders: {}", e.getMessage(), e);
             }

         //2. Opciono: dodati metriku (broj izvrsenih fill-ova po ciklusu).

         /*3. Opciono: proveriti da li je berza otvorena pre pokretanja:
             - Ako se ne trguje vikendom, preskociti ciklus (ali after-hours nalozi
               se ipak izvrsavaju van radnog vremena berze).
               Za sada, izvrsavati uvek (24/7 simulacija). */

        log.debug("Execute approved orders cycle completed");
    }
}
