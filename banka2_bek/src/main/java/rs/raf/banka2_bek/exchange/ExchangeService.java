package rs.raf.banka2_bek.exchange;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.exchange.dto.ExchangeRateDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ExchangeService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeService.class);

    private final RestTemplate restTemplate;

    @Value("${exchange.api.key:}")
    private String apiKey;

    @Value("${exchange.api.url}")
    private String apiUrl;

    /**
     * BE-PAY-07: snapshot tipa za thread-safe kes.
     *
     * <p>{@code rates} je immutable (kopiramo u List.copyOf pri save-u);
     * {@code timestamp} pamti kad je snapshot napravljen. Cela snapshot se
     * publish-uje atomicno preko {@link AtomicReference#set} pa ne moze
     * doci do delimicnog citanja gde rates pripada novom batch-u a timestamp
     * starom (race u prethodnoj implementaciji sa razdvojenim
     * {@code cachedRates}/{@code cacheTimestamp} field-ovima).</p>
     *
     * @param rates immutable cache-ovani kurs
     * @param timestamp Instant kad je snapshot napravljen
     */
    private record RatesSnapshot(List<ExchangeRateDto> rates, Instant timestamp) {
        RatesSnapshot(List<ExchangeRateDto> rates, Instant timestamp) {
            this.rates = List.copyOf(rates);
            this.timestamp = timestamp;
        }

        boolean isFresh(long ttlMs) {
            return timestamp != null
                    && (Instant.now().toEpochMilli() - timestamp.toEpochMilli()) < ttlMs;
        }
    }

    // BE-PAY-07: thread-safe cache (AtomicReference garantuje atomic publish/read).
    private final AtomicReference<RatesSnapshot> snapshotRef = new AtomicReference<>(null);

    // Cache za kurseve — 5 minuta TTL
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    public ExchangeService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    void warnIfApiKeyMissing() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("SEC-01: exchange.api.key is empty — Fixer.io poziv se nece izvrsavati, "
                    + "ExchangeService koristi fallback (NBS) kurseve. Postavi EXCHANGE_API_KEY "
                    + "env var za live FX kurseve (production deployment MORA da setuje ovaj key).");
        }
    }

    public List<ExchangeRateDto> getAllRates() {
        RatesSnapshot snapshot = snapshotRef.get();
        if (snapshot != null && snapshot.isFresh(CACHE_TTL_MS)) {
            return snapshot.rates();
        }
        return fetchAndCacheRates();
    }

    private synchronized List<ExchangeRateDto> fetchAndCacheRates() {
        // Double-check after acquiring lock
        RatesSnapshot existing = snapshotRef.get();
        if (existing != null && existing.isFresh(CACHE_TTL_MS)) {
            return existing.rates();
        }

        // SEC-01: ne salji zahtev ka Fixer.io bez validnog key-a — odmah fallback.
        if (apiKey == null || apiKey.isBlank()) {
            List<ExchangeRateDto> fallback = getFallbackRates();
            snapshotRef.set(new RatesSnapshot(fallback, Instant.now()));
            return fallback;
        }

        String url = apiUrl + "?access_key=" + apiKey +
                "&symbols=RSD,EUR,CHF,USD,GBP,JPY,CAD,AUD";

        Map<String, Object> body;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = restTemplate.getForEntity(url, Map.class).getBody();
            body = responseBody;
        } catch (Exception e) {
            // API nedostupan ili rate limit — koristimo fallback kurseve
            if (existing != null) return existing.rates();
            List<ExchangeRateDto> fallback = getFallbackRates();
            snapshotRef.set(new RatesSnapshot(fallback, Instant.now()));
            return fallback;
        }

        if (body == null || body.get("rates") == null) {
            if (existing != null) return existing.rates();
            List<ExchangeRateDto> fallback = getFallbackRates();
            snapshotRef.set(new RatesSnapshot(fallback, Instant.now()));
            return fallback;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> rates = (Map<String, Object>) body.get("rates");

        Double eurToRsd = getDouble(rates.get("RSD"));

        if (eurToRsd == null || eurToRsd == 0.0) {
            throw new RuntimeException("RSD rate not found.");
        }

        String[] currencies = {"RSD", "EUR", "CHF", "USD", "GBP", "JPY", "CAD", "AUD"};

        List<ExchangeRateDto> result = new ArrayList<>();

        for (String currency : currencies) {

            if ("RSD".equals(currency)) {
                result.add(new ExchangeRateDto("RSD", 1.0));
                continue;
            }

            if ("EUR".equals(currency)) {
                double rate = round(1.0 / eurToRsd, 6);
                result.add(new ExchangeRateDto("EUR", rate));
                continue;
            }

            Double eurToTarget = getDouble(rates.get(currency));

            if (eurToTarget != null) {
                double rsdToTarget = eurToTarget / eurToRsd;
                result.add(new ExchangeRateDto(currency, round(rsdToTarget, 6)));
            }
        }

        // BE-PAY-07: atomic publish — citaoci vide ili stari snapshot ili novi snapshot
        // (List.copyOf u RatesSnapshot ctor-u + AtomicReference.set garantuje
        // happens-before bez race-a izmedju rates i timestamp polja).
        snapshotRef.set(new RatesSnapshot(result, Instant.now()));
        return result;
    }

    private Double getDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }


    public CalculateExchangeResponseDto calculate(double amount, String toCurrency) {

        if ("RSD".equalsIgnoreCase(toCurrency)) {
            return new CalculateExchangeResponseDto(amount, 1.0, "RSD", "RSD");
        }

        List<ExchangeRateDto> rates = getAllRates();

        double rsdToTarget = rates.stream()
                .filter(r -> r.getCurrency().equalsIgnoreCase(toCurrency))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Currency not supported: " + toCurrency))
                .getRate();

        // Prodajni kurs = +2% na srednji kurs (banka prodaje valutu klijentu)
        double sellRate = round(rsdToTarget * 1.02, 6);

        // Provizija 0.5%
        double commission = 0.005;

        double convertedAmount = round((amount * sellRate) * (1 - commission), 4);

        return new CalculateExchangeResponseDto(convertedAmount, sellRate, "RSD", toCurrency.toUpperCase());
    }


    private double[] convertToRsd(double amount, String fromCurrency, List<ExchangeRateDto> rates) {
        double rsdToFrom = rates.stream()
                .filter(r -> r.getCurrency().equalsIgnoreCase(fromCurrency))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Currency not supported: " + fromCurrency))
                .getRate();

        double sellRate = round((1.0 / rsdToFrom) * 1.02, 6);
        double commission = 0.005;
        double rsdAmount = round((amount * sellRate) * (1 - commission), 4);

        return new double[]{rsdAmount, sellRate};
    }

    /**
     * Fallback kursevi bazirani na prosecnim vrednostima NBS-a.
     * Koriste se kada fixer.io API nije dostupan (rate limit, downtime).
     * Kursevi su u formatu: koliko strane valute se dobije za 1 RSD.
     */
    private List<ExchangeRateDto> getFallbackRates() {
        List<ExchangeRateDto> rates = new ArrayList<>();
        rates.add(new ExchangeRateDto("RSD", 1.0));
        rates.add(new ExchangeRateDto("EUR", 0.008547));   // ~117 RSD za 1 EUR
        rates.add(new ExchangeRateDto("CHF", 0.007800));   // ~128 RSD za 1 CHF
        rates.add(new ExchangeRateDto("USD", 0.009090));   // ~110 RSD za 1 USD
        rates.add(new ExchangeRateDto("GBP", 0.007350));   // ~136 RSD za 1 GBP
        rates.add(new ExchangeRateDto("JPY", 1.363636));   // ~0.73 RSD za 1 JPY
        rates.add(new ExchangeRateDto("CAD", 0.012500));   // ~80 RSD za 1 CAD
        rates.add(new ExchangeRateDto("AUD", 0.013333));   // ~75 RSD za 1 AUD
        return rates;
    }

    public CalculateExchangeResponseDto calculateCross(double amount, String fromCurrency, String toCurrency) {

        if ("RSD".equalsIgnoreCase(fromCurrency)) {
            return calculate(amount, toCurrency);
        }

        List<ExchangeRateDto> rates = getAllRates();
        double[] conversion = convertToRsd(amount, fromCurrency, rates);
        double rsdAmount = conversion[0];
        double sellRate = conversion[1];

        if ("RSD".equalsIgnoreCase(toCurrency)) {
            return new CalculateExchangeResponseDto(rsdAmount, sellRate, fromCurrency.toUpperCase(), "RSD");
        }

        CalculateExchangeResponseDto step2 = calculate(rsdAmount, toCurrency);
        double crossRate = round(step2.getConvertedAmount() / amount, 6);

        return new CalculateExchangeResponseDto(
                step2.getConvertedAmount(),
                crossRate,
                fromCurrency.toUpperCase(),
                toCurrency.toUpperCase()
        );
    }


}
