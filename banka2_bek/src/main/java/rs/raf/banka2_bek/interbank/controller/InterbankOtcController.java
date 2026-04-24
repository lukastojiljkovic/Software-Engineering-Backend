package rs.raf.banka2_bek.interbank.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.interbank.dto.OtcInterbankDtos;
import rs.raf.banka2_bek.interbank.model.InterbankTransaction;

import java.util.List;

/*
================================================================================
 TODO — LOCAL ENDPOINT ZA OTC INTER-BANK (FE JE POZIVA)
 Zaduzen: BE tim
--------------------------------------------------------------------------------
 ENDPOINTI:
   GET   /interbank/otc/listings                — discovery iz drugih banaka
   POST  /interbank/otc/offers                  — kreiraj ponudu
   GET   /interbank/otc/offers/my               — moje aktivne inter-bank ponude
   PATCH /interbank/otc/offers/{offerId}/counter — kontraponuda
   PATCH /interbank/otc/offers/{offerId}/decline — odbij
   PATCH /interbank/otc/offers/{offerId}/accept  — prihvati
   GET   /interbank/otc/contracts/my            — moji inter-bank ugovori
   POST  /interbank/otc/contracts/{contractId}/exercise — pokreni SAGA

 AUTHORIZATION:
 - authenticated
================================================================================
*/
@RestController
@RequestMapping("/interbank/otc")
public class InterbankOtcController {

    // TODO: injectovati InterbankOtcService

    @GetMapping("/listings")
    public ResponseEntity<List<OtcInterbankDtos.OtcInterbankListingDto>> listRemoteListings() {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/offers")
    public ResponseEntity<OtcInterbankDtos.OtcInterbankOfferDto> createOffer(
            @Valid @RequestBody OtcInterbankDtos.CreateOtcInterbankOfferDto dto) {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/offers/my")
    public ResponseEntity<List<OtcInterbankDtos.OtcInterbankOfferDto>> myOffers() {
        throw new UnsupportedOperationException("TODO");
    }

    @PatchMapping("/offers/{offerId}/counter")
    public ResponseEntity<OtcInterbankDtos.OtcInterbankOfferDto> counter(
            @PathVariable String offerId,
            @Valid @RequestBody OtcInterbankDtos.CounterOtcInterbankOfferDto dto) {
        throw new UnsupportedOperationException("TODO");
    }

    @PatchMapping("/offers/{offerId}/decline")
    public ResponseEntity<OtcInterbankDtos.OtcInterbankOfferDto> decline(@PathVariable String offerId) {
        throw new UnsupportedOperationException("TODO");
    }

    @PatchMapping("/offers/{offerId}/accept")
    public ResponseEntity<OtcInterbankDtos.OtcInterbankOfferDto> accept(
            @PathVariable String offerId,
            @RequestParam Long accountId) {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/contracts/my")
    public ResponseEntity<Object> myContracts(@RequestParam(required = false) String status) {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/contracts/{contractId}/exercise")
    public ResponseEntity<InterbankTransaction> exercise(
            @PathVariable String contractId,
            @RequestParam Long buyerAccountId) {
        throw new UnsupportedOperationException("TODO");
    }
}
