package rs.raf.banka2_bek.interbank.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.interbank.dto.InterbankPaymentInitiateDto;
import rs.raf.banka2_bek.interbank.model.InterbankTransaction;

/*
================================================================================
 TODO — LOCAL ENDPOINT ZA INICIRANJE INTER-BANK PLACANJA
 Zaduzen: BE tim
--------------------------------------------------------------------------------
 ENDPOINT:
   POST /interbank/payments/initiate
   GET  /interbank/payments/{transactionId}        -- status pooling
   GET  /interbank/payments/my                      -- istorija mojih

 AUTHORIZATION:
 - authenticated (authsta u GlobalSecurityConfig)

 FE INTEGRACIJA (PaymentCreatePage):
 - Posle popunjavanja forme, FE prvo proverava da li receiverAccount ide
   u drugu banku. Ako da -> zove ovaj endpoint. Inace -> standardni
   /payments endpoint.

 NAPOMENA:
 - PaymentController postojeci endpoint treba prosirit da automatski
   detektuje i redirektuje (ili: FE eksplicitno izabere). Oba pristupa
   su moguca; spec kaze sistem sam detektuje po prve 3 cifre (linija 377).
================================================================================
*/
@RestController
@RequestMapping("/interbank/payments")
public class InterbankPaymentController {

    // TODO: injectovati InterbankPaymentService

    @PostMapping("/initiate")
    public ResponseEntity<InterbankTransaction> initiate(@Valid @RequestBody InterbankPaymentInitiateDto dto) {
        // TODO: uzmi userId iz SecurityContext, pozovi service.initiatePayment(dto, userId)
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<InterbankTransaction> getStatus(@PathVariable String transactionId) {
        // TODO: service.getTransactionStatus(transactionId)
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/my")
    public ResponseEntity<Object> getMyHistory() {
        // TODO: service.getHistoryForCurrentUser()
        throw new UnsupportedOperationException("TODO");
    }
}
