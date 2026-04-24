package rs.raf.banka2_bek.interbank.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.interbank.dto.InterbankEnvelopeDto;
import rs.raf.banka2_bek.interbank.dto.InterbankPaymentInitiateDto;
import rs.raf.banka2_bek.interbank.model.InterbankTransaction;

/*
================================================================================
 TODO — SERVIS KOJI VODI INTER-BANK PLACANJA SA NASE STRANE
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 368-437 (PAYMENT 2PC)
--------------------------------------------------------------------------------
 METODE SA NASE STRANE KAO INICIJATOR (Banka A):

 1. InterbankTransaction initiatePayment(InterbankPaymentInitiateDto dto, Long userId);
    - Validacija racuna + raspoloziva sredstva (FundReservationService.reserveForInterbank)
    - Generisi transactionId (UUID)
    - Kreiraj InterbankTransaction sa status=INITIATED
    - Pozovi sendPrepare(tx)
    - Vrati tx (FE vidi status)
    Flow: Celina 4 linije 376-388.

 2. void sendPrepare(InterbankTransaction tx);
    - Kreiraj PreparePayload iz tx
    - Envelope sa messageId UUID, type=PREPARE
    - InterbankClient.send(envelope)
    - Ako Ready -> handleReady(tx, response)
    - Ako NotReady -> abort(tx, reason)
    Flow: Celina 4 linije 382-401.

 3. void handleReady(InterbankTransaction tx, InterbankEnvelopeDto response);
    - Parsiraj PaymentReadyPayloadDto iz response.payload
    - Upise kurs, proviziju, finalAmount u tx
    - status = PREPARED, preparedAt = now
    - Pozovi sendCommit(tx)
    Flow: Celina 4 linije 393-400.

 4. void sendCommit(InterbankTransaction tx);
    - Envelope type=COMMIT, payload = PaymentCommitPayloadDto
    - InterbankClient.send(envelope)
    - Ako Committed: skini rezervaciju (fundReservation.commitForInterbank),
      status=COMMITTED, committedAt=now
    - Ako greska: abort(tx)
    Flow: Celina 4 linije 409-419.

 5. void abort(InterbankTransaction tx, String reason);
    - Salje ABORT poruku ako smo vec u PREPARED fazi
    - Oslobadja rezervaciju (fundReservation.releaseForInterbank)
    - status=ABORTED, failureReason=reason, abortedAt=now
    Flow: Celina 4 linije 403-406.


 METODE SA NASE STRANE KAO PRIMALAC (Banka B):

 6. InterbankEnvelopeDto handlePrepare(InterbankEnvelopeDto env);
    - Validiraj primaoca (naseg klijenta)
    - Izracunaj kurs i proviziju (CurrencyConversionService)
    - Vrati READY envelope sa payload PaymentReadyPayloadDto
    - Ako racun ne postoji ili neaktivan: vrati NOT_READY sa reasonom
    Flow: Celina 4 linije 389-401.

 7. InterbankEnvelopeDto handleCommit(InterbankEnvelopeDto env);
    - Uplati finalAmount na receiver racun (u valuti primaoca)
    - Upise Transaction record (credit)
    - Vrati COMMITTED envelope
    - Notifikacija klijenta (PushNotification ili mail)
    Flow: Celina 4 linije 413-416.

 8. InterbankEnvelopeDto handleAbort(InterbankEnvelopeDto env);
    - Ako smo rezervisali nesto (npr. marker ili deo), oslobodi.
    - Za plain primaoca obicno nema sta da se oslobadja.

 ROLLBACK / KOMPENZACIJA:
  Ako commit ne uspe (npr. mrezna greska posle poslate Prepare+Ready ali
  pre primljenog Committed): InterbankRetryScheduler ce ponovo poslati
  Commit. Druga banka ga mora idempotentno obraditi (videti
  InterbankMessageService.isDuplicate).
================================================================================
*/
@Service
public class InterbankPaymentService {

    // TODO: injectovati: InterbankClient client, InterbankMessageService messages,
    //   BankRoutingService routing, InterbankTransactionRepository txRepo,
    //   AccountRepository accounts, FundReservationService reservations,
    //   CurrencyConversionService fx, TransactionRepository localTx

    @Transactional
    public InterbankTransaction initiatePayment(InterbankPaymentInitiateDto dto, Long userId) {
        // TODO: implementirati — sve po TODO bloku iznad
        throw new UnsupportedOperationException("TODO: implementirati initiatePayment");
    }

    @Transactional
    public void sendPrepare(InterbankTransaction tx) {
        // TODO
        throw new UnsupportedOperationException("TODO: implementirati sendPrepare");
    }

    @Transactional
    public void handleReady(InterbankTransaction tx, InterbankEnvelopeDto response) {
        // TODO
        throw new UnsupportedOperationException("TODO: implementirati handleReady");
    }

    @Transactional
    public void sendCommit(InterbankTransaction tx) {
        // TODO
        throw new UnsupportedOperationException("TODO: implementirati sendCommit");
    }

    @Transactional
    public void abort(InterbankTransaction tx, String reason) {
        // TODO
        throw new UnsupportedOperationException("TODO: implementirati abort");
    }

    @Transactional
    public InterbankEnvelopeDto handlePrepare(InterbankEnvelopeDto env) {
        // TODO — mi smo primalac; Banka A pita moze li da uplati nasem klijentu
        throw new UnsupportedOperationException("TODO: implementirati handlePrepare");
    }

    @Transactional
    public InterbankEnvelopeDto handleCommit(InterbankEnvelopeDto env) {
        // TODO — mi smo primalac; Banka A nam kaze da uplatimo primaocu
        throw new UnsupportedOperationException("TODO: implementirati handleCommit");
    }

    @Transactional
    public InterbankEnvelopeDto handleAbort(InterbankEnvelopeDto env) {
        // TODO — mi smo primalac; Banka A prekida transakciju
        throw new UnsupportedOperationException("TODO: implementirati handleAbort");
    }
}
