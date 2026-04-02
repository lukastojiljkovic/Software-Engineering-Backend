package rs.raf.banka2_bek.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.order.model.Order;

/**
 * Servis za validaciju All-or-None (AON) naloga.
 *
 * Specifikacija: Celina 3 - AON Order Validation
 *
 * AON nalog se moze izvrsiti SAMO ako je moguce popuniti celu kolicinu odjednom.
 * Ako nema dovoljno volumena za kompletno izvrsavanje, nalog ceka do sledeceg ciklusa.
 * Parcijalni fill-ovi NISU dozvoljeni za AON naloge.
 */
@Service
public class AonValidationService {

    private static final Logger log = LoggerFactory.getLogger(AonValidationService.class);

    /**
     * Proverava da li se AON nalog moze izvrsiti sa datim raspolozivim volumenom.
     *
     * @param order           nalog koji se proverava
     * @param availableVolume raspolozivi volume za fill (izracunat na osnovu listing volumena)
     * @return true ako se nalog moze izvrsiti, false ako ne moze (AON uslov nije ispunjen)
     */
    public boolean checkCanExecuteAon(Order order, int availableVolume) {
        if (!order.isAllOrNone()) {
            return true;
        }
        boolean canFill = availableVolume >= order.getRemainingPortions();
        if (!canFill) {
            log.debug("AON order #{} cannot execute: available {} < remaining {}",
                    order.getId(), availableVolume, order.getRemainingPortions());
        }
        return canFill;
    }
}
