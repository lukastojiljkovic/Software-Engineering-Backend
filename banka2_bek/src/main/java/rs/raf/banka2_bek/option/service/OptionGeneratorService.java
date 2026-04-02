package rs.raf.banka2_bek.option.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.option.model.Option;
import rs.raf.banka2_bek.option.model.OptionType;
import rs.raf.banka2_bek.option.repository.OptionRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Servis za automatsko generisanje opcija za sve STOCK listinge.
 *
 * Logika generisanja:
 * ===================
 *
 * Za svaku akciju (Listing sa tipom STOCK):
 *
 * 1. STRIKE CENE:
 *    - Uzeti trenutnu cenu akcije (Listing.price)
 *    - Generisati 5 strike cena IZNAD trenutne (svaka +5% od prethodne):
 *      price * 1.05, price * 1.10, price * 1.15, price * 1.20, price * 1.25
 *    - Generisati 5 strike cena ISPOD trenutne (svaka -5% od prethodne):
 *      price * 0.95, price * 0.90, price * 0.85, price * 0.80, price * 0.75
 *    - Ukupno: 10 strike cena po settlement datumu
 *
 * 2. SETTLEMENT DATUMI:
 *    - 6 datuma sa razmakom od 6 dana od danas:
 *      today+6, today+12, today+18, today+24, today+30, today+36
 *    - 3 datuma sa razmakom od 30 dana posle toga:
 *      today+66, today+96, today+126
 *    - Ukupno: 9 settlement datuma
 *
 * 3. GENERISANJE OPCIJA:
 *    Za svaku kombinaciju (strikePrice, settlementDate):
 *      a. Kreirati CALL opciju — cena iz BlackScholesService.calculateCallPrice()
 *      b. Kreirati PUT opciju — cena iz BlackScholesService.calculatePutPrice()
 *      c. Generisati ticker: {STOCK}{YYMMDD}{C/P}{STRIKE*1000 padovano na 8}
 *      d. Generisati ask = price * 1.05 (spread)
 *      e. Generisati bid = price * 0.95 (spread)
 *      f. Generisati volume = nasumicno 100-10000
 *      g. Generisati impliedVolatility = nasumicno 0.15 - 0.60
 *    Ukupno: 10 strikes * 9 datuma * 2 tipa = 180 opcija po akciji
 *
 * 4. TICKER FORMAT:
 *    Format: {STOCK_TICKER}{YYMMDD}{C/P}{STRIKE*1000 zero-padded na 8}
 *    Primer: AAPL260408C00185000
 *    Implementirati u generateTicker() metodi.
 *
 * 5. DUPLIKATI:
 *    - Pre generisanja proveriti existsByStockListingIdAndSettlementDate()
 *    - Ako vec postoje opcije za dati listing i datum, preskociti
 *    - Ovo omogucava incrementalni run (ne generisati ponovo vec postojece)
 */
@Service
@RequiredArgsConstructor
public class OptionGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(OptionGeneratorService.class);

    private final OptionRepository optionRepository;
    private final ListingRepository listingRepository;
    private final BlackScholesService blackScholesService;

    /** Procenat pomaka za svaku sledecu strike cenu (5% = 0.05) */
    private static final double STRIKE_STEP_PERCENT = 0.05;

    /** Broj strike cena iznad i ispod trenutne cene */
    private static final int STRIKES_PER_SIDE = 5;

    /** Format za datum deo tikera */
    private static final DateTimeFormatter TICKER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    /**
     * Generise opcije za jednu akciju.
     *
     * @param stock Listing entitet za koji se generisu opcije (mora biti STOCK tip)
     */
    @Transactional
    public void generateOptionsForListing(Listing stock) {
        if (stock == null || stock.getListingType() != ListingType.STOCK) return;
        BigDecimal currentPrice = stock.getPrice();
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Preskacemo listing {} - nema cenu", stock.getTicker());
            return;
        }

        List<BigDecimal> strikes = generateStrikePrices(currentPrice);
        List<LocalDate> dates = generateSettlementDates();
        List<Option> options = new ArrayList<>();

        for (LocalDate date : dates) {
            if (optionRepository.existsByStockListingIdAndSettlementDate(stock.getId(), date)) continue;
            for (BigDecimal strike : strikes) {
                double T = ChronoUnit.DAYS.between(LocalDate.now(), date) / 365.0;
                double sigma = 0.15 + Math.random() * 0.45;
                double S = currentPrice.doubleValue();
                double K = strike.doubleValue();

                // CALL
                Option call = buildOption(stock, OptionType.CALL, strike, date, T, sigma, S, K);
                options.add(call);

                // PUT
                Option put = buildOption(stock, OptionType.PUT, strike, date, T, sigma, S, K);
                options.add(put);
            }
        }

        if (!options.isEmpty()) {
            optionRepository.saveAll(options);
        }
        log.info("Generisano {} opcija za {}", options.size(), stock.getTicker());
    }

    private Option buildOption(Listing stock, OptionType type, BigDecimal strike,
                               LocalDate date, double T, double sigma, double S, double K) {
        BigDecimal price;
        if (type == OptionType.CALL) {
            price = blackScholesService.calculateCallPrice(S, K, T, sigma);
        } else {
            price = blackScholesService.calculatePutPrice(S, K, T, sigma);
        }

        Option option = new Option();
        option.setStockListing(stock);
        option.setOptionType(type);
        option.setStrikePrice(strike);
        option.setSettlementDate(date);
        option.setImpliedVolatility(sigma);
        option.setPrice(price);
        option.setAsk(price.multiply(BigDecimal.valueOf(1.05)).setScale(4, RoundingMode.HALF_UP));
        option.setBid(price.multiply(BigDecimal.valueOf(0.95)).setScale(4, RoundingMode.HALF_UP));
        option.setVolume((long) (100 + Math.random() * 9900));
        option.setOpenInterest(0);
        option.setContractSize(100);
        option.setTicker(generateTicker(stock.getTicker(), date, type, strike));
        return option;
    }

    /** Generise opcije za SVE akcije u sistemu. */
    @Transactional
    public void generateAllOptions() {
        List<Listing> stocks = listingRepository.findAll().stream()
                .filter(l -> l.getListingType() == ListingType.STOCK)
                .toList();

        log.info("Generisanje opcija za {} akcija...", stocks.size());
        int successCount = 0;
        for (Listing stock : stocks) {
            try {
                generateOptionsForListing(stock);
                successCount++;
            } catch (Exception e) {
                log.error("Greska pri generisanju opcija za {}: {}", stock.getTicker(), e.getMessage());
            }
        }
        log.info("Uspesno generisane opcije za {}/{} akcija", successCount, stocks.size());
    }

    /**
     * Generise listu strike cena na osnovu trenutne cene akcije.
     *
     * @param currentPrice trenutna cena akcije
     * @return sortirana lista od 10 strike cena
     */
    protected List<BigDecimal> generateStrikePrices(BigDecimal currentPrice) {
        List<BigDecimal> strikes = new ArrayList<>();
        for (int i = 1; i <= STRIKES_PER_SIDE; i++) {
            strikes.add(currentPrice.multiply(BigDecimal.valueOf(1 + i * STRIKE_STEP_PERCENT))
                    .setScale(2, RoundingMode.HALF_UP));
            strikes.add(currentPrice.multiply(BigDecimal.valueOf(1 - i * STRIKE_STEP_PERCENT))
                    .setScale(2, RoundingMode.HALF_UP));
        }
        strikes.sort(BigDecimal::compareTo);
        return strikes;
    }

    /**
     * Generise listu settlement datuma od danas.
     *
     * @return lista od 9 settlement datuma
     */
    protected List<LocalDate> generateSettlementDates() {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate today = LocalDate.now();
        // 6 dates with 6-day spacing
        for (int i = 1; i <= 6; i++) {
            dates.add(today.plusDays(i * 6L));
        }
        // 3 dates with 30-day spacing after the last
        dates.add(today.plusDays(66));
        dates.add(today.plusDays(96));
        dates.add(today.plusDays(126));
        return dates;
    }

    /**
     * Generise ticker string za opciju.
     *
     * @param stockTicker    ticker osnovne akcije
     * @param settlementDate datum isteka opcije
     * @param optionType     CALL ili PUT
     * @param strikePrice    strike cena
     * @return generisani ticker string
     */
    protected String generateTicker(String stockTicker, LocalDate settlementDate,
                                    OptionType optionType, BigDecimal strikePrice) {
        String dateStr = settlementDate.format(TICKER_DATE_FORMAT);
        String typeChar = optionType == OptionType.CALL ? "C" : "P";
        long strikeInt = strikePrice.multiply(BigDecimal.valueOf(1000)).longValue();
        String strikeStr = String.format("%08d", strikeInt);
        return stockTicker + dateStr + typeChar + strikeStr;
    }
}
