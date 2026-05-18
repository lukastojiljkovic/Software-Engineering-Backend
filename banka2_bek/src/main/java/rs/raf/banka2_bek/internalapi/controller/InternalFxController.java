package rs.raf.banka2_bek.internalapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.raf.banka2.contracts.internal.FxRateDto;
import rs.raf.banka2_bek.exchange.ExchangeService;

import java.util.List;

/**
 * Interni FX endpoint za trading-service.
 * trading-service-ov ListingServiceImpl racuna FOREX cross-kurseve, a nema
 * sopstveni izvor deviznih kurseva — exchange paket (fixer.io + 5-min cache +
 * fallback kursevi) ostaje u banka-core. Ova ruta izlaze srednje kurseve
 * preko istog X-Internal-Key seam-a kao /internal/funds (Korak 0);
 * InternalAuthFilter pokriva /internal/**.
 */
@RestController
@RequestMapping("/internal/fx")
public class InternalFxController {

    private final ExchangeService exchangeService;

    public InternalFxController(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    /**
     * Vraca srednje devizne kurseve (currency -> rate, koliko strane valute
     * za 1 RSD). Delegira na ExchangeService.getAllRates() koji ima 5-min
     * cache i fixer.io fallback — nikad ne baca na API gresci.
     */
    @GetMapping("/rates")
    public ResponseEntity<List<FxRateDto>> getRates() {
        List<FxRateDto> rates = exchangeService.getAllRates().stream()
                .map(r -> new FxRateDto(r.getCurrency(), r.getRate()))
                .toList();
        return ResponseEntity.ok(rates);
    }
}
