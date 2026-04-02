package rs.raf.banka2_bek.option.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.option.model.Option;
import rs.raf.banka2_bek.option.model.OptionType;
import rs.raf.banka2_bek.option.repository.OptionRepository;
import rs.raf.banka2_bek.option.service.BlackScholesService;
import rs.raf.banka2_bek.option.service.OptionGeneratorService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduler za automatsko odrzavanje opcija.
 *
 * Pokrece se svakodnevno u 03:00 (cron: "0 0 3 * * *") i obavlja tri zadatka:
 *
 * 1. BRISANJE ISTEKLIH OPCIJA:
 *    - Brise sve opcije gde je settlementDate < danas
 *    - Koristi optionRepository.deleteBySettlementDateBefore(LocalDate.now())
 *    - Loguje koliko je opcija obrisano
 *
 * 2. GENERISANJE NOVIH OPCIJA:
 *    - Poziva optionGeneratorService.generateAllOptions()
 *    - Ovo ce generisati opcije za nove settlement datume koji jos ne postoje
 *    - Postojece opcije se preskacju (existsByStockListingIdAndSettlementDate check)
 *
 * 3. REKALKULACIJA CENA:
 *    - Za SVE postojece (neistekle) opcije ponovo izracunava cenu
 *    - Koristi Black-Scholes sa azuriranom cenom akcije
 *    - Takodje azurira ask i bid (price * 1.05 i price * 0.95)
 *    - Ovo je potrebno jer se cena akcije menja tokom dana
 *      (ListingServiceImpl.refreshPrices() se pokrece svakih 15 min)
 *
 * NAPOMENA: @EnableScheduling mora biti ukljucen u konfiguraciji aplikacije.
 * Proveriti da li vec postoji na glavnoj Application klasi ili config klasi.
 * ListingServiceImpl vec koristi @Scheduled, pa bi trebalo da je ukljuceno.
 *
 * CRON IZRAZ: "0 0 3 * * *"
 *   - Sekund: 0
 *   - Minut: 0
 *   - Sat: 3 (03:00 ujutru)
 *   - Dan: * (svaki)
 *   - Mesec: * (svaki)
 *   - Dan u nedelji: * (svaki)
 */
@Component
@RequiredArgsConstructor
public class OptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(OptionScheduler.class);

    private final OptionRepository optionRepository;
    private final OptionGeneratorService optionGeneratorService;
    private final BlackScholesService blackScholesService;

    /** Glavni scheduled metod -- pokrece se svakodnevno u 03:00. */
    @Scheduled(cron = "0 0 3 * * *")
    public void dailyOptionMaintenance() {
        log.info("Pocetak dnevnog odrzavanja opcija...");

        try {
            cleanupExpiredOptions();
        } catch (Exception e) {
            log.error("Greska pri brisanju isteklih opcija: {}", e.getMessage(), e);
        }

        try {
            generateNewOptions();
        } catch (Exception e) {
            log.error("Greska pri generisanju novih opcija: {}", e.getMessage(), e);
        }

        try {
            recalculatePrices();
        } catch (Exception e) {
            log.error("Greska pri rekalkulaciji cena opcija: {}", e.getMessage(), e);
        }

        log.info("Dnevno odrzavanje opcija zavrseno.");
    }

    /** Brise sve opcije kojima je istekao settlement datum. */
    @Transactional
    protected void cleanupExpiredOptions() {
        List<Option> expired = optionRepository.findBySettlementDateBefore(LocalDate.now());
        int count = expired.size();
        if (count > 0) {
            optionRepository.deleteBySettlementDateBefore(LocalDate.now());
            log.info("Obrisano {} isteklih opcija", count);
        } else {
            log.info("Nema isteklih opcija za brisanje");
        }
    }

    /** Generise nove opcije za settlement datume koji jos ne postoje. */
    protected void generateNewOptions() {
        log.info("Generisanje novih opcija...");
        optionGeneratorService.generateAllOptions();
    }

    /** Rekalkulise cene svih postojecih opcija koristeci Black-Scholes. */
    @Transactional
    protected void recalculatePrices() {
        List<Option> allOptions = optionRepository.findAll();
        LocalDate today = LocalDate.now();
        int updated = 0;

        for (Option option : allOptions) {
            BigDecimal stockPrice = option.getStockListing().getPrice();
            if (stockPrice == null) continue;

            long daysToExpiry = ChronoUnit.DAYS.between(today, option.getSettlementDate());
            if (daysToExpiry <= 0) continue;

            double T = daysToExpiry / 365.0;
            double S = stockPrice.doubleValue();
            double K = option.getStrikePrice().doubleValue();
            double sigma = option.getImpliedVolatility();

            BigDecimal newPrice;
            if (option.getOptionType() == OptionType.CALL) {
                newPrice = blackScholesService.calculateCallPrice(S, K, T, sigma);
            } else {
                newPrice = blackScholesService.calculatePutPrice(S, K, T, sigma);
            }

            option.setPrice(newPrice);
            option.setAsk(newPrice.multiply(BigDecimal.valueOf(1.05)).setScale(4, RoundingMode.HALF_UP));
            option.setBid(newPrice.multiply(BigDecimal.valueOf(0.95)).setScale(4, RoundingMode.HALF_UP));
            updated++;
        }

        optionRepository.saveAll(allOptions);
        log.info("Rekalkulisane cene za {} opcija", updated);
    }
}
