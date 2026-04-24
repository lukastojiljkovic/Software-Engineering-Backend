package rs.raf.banka2_bek.interbank.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.interbank.dto.InterbankEnvelopeDto;

/*
================================================================================
 TODO — INBOUND ENDPOINT ZA PORUKE OD DRUGIH BANAKA
 Zaduzen: BE tim
 Spec referenca: Celina 4, komunikacija izmedju banaka
--------------------------------------------------------------------------------
 JEDINSTVENA TACKA ZA SVE INBOUND PORUKE:
   POST /bank-api/message

 AUTHORIZATION:
 - Prepoznati partnera po `Authorization: Bearer <token>`.
 - Ako token nije u `interbank.partners[*].authToken`, 401.
 - Dodatno proveriti da `envelope.senderBankCode` odgovara partneru.

 IDEMPOTENTNOST:
 - Pre obrade pozvati InterbankMessageService.isDuplicate(envelope.messageId).
 - Ako true, vrati poslednji odgovor iz audit loga (ili 200 OK sa istim body-jem).

 DISPATCH PO TIPU:
 - PREPARE, COMMIT, ABORT         -> InterbankPaymentService.handle*
 - RESERVE_SHARES, COMMIT_FUNDS,
   TRANSFER_OWNERSHIP, FINAL_CONFIRM -> InterbankOtcService.handle*
 - CHECK_STATUS                   -> vratiti trenutni status transakcije

 ODGOVOR:
 Svaka handle* metoda vraca InterbankEnvelopeDto (odgovor) koji se salje
 nazad. Primer: na PREPARE odgovori READY ili NOT_READY.

 GRESKE:
 - 400 za malformed envelope (validacija)
 - 401 za los token
 - 409 za duplikat koji smo vec obradili (ili 200 sa kesiranim odgovorom)
 - 500 za internal errors (sa InterbankCommunicationException wrapperom)
================================================================================
*/
@RestController
@RequestMapping("/bank-api")
public class InterbankInboundController {

    // TODO: injectovati InterbankPaymentService, InterbankOtcService, InterbankMessageService

    @PostMapping("/message")
    public ResponseEntity<InterbankEnvelopeDto> receiveMessage(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody InterbankEnvelopeDto envelope) {
        // TODO:
        //  1. Parse + validate token
        //  2. isDuplicate check
        //  3. dispatch na PaymentService ili OtcService prema envelope.type
        //  4. Upise INBOUND poruku u audit
        //  5. Vrati response envelope
        throw new UnsupportedOperationException("TODO: implementirati receiveMessage");
    }

    // TODO (opciono): GET /bank-api/otc/public-listings — vraca akcije koje
    //   nase banka javno izlozila drugim bankama. Potrebno za discovery kod
    //   partnera.
    @GetMapping("/otc/public-listings")
    public ResponseEntity<Object> publicListings(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // TODO: validate token, vrati listu javnih akcija (Portfolio.publicQuantity > 0)
        //       u formatu OtcInterbankListingDto
        throw new UnsupportedOperationException("TODO: implementirati publicListings");
    }
}
