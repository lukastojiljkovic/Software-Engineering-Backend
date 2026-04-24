package rs.raf.banka2_bek.interbank.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
================================================================================
 TODO — RETRY PETLJA ZA ZAGLAVLJENE INTER-BANK TRANSAKCIJE
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 422-424 "mehanizme za resavanje grešaka i
                                           ponavljanje transakcija"; 515-519
--------------------------------------------------------------------------------
 FLOW:
  Svaka 2 minuta proveravamo sve transakcije ciji je status "in-progress"
  (PREPARING, COMMITTING, ABORTING) i cija je lastRetryAt starija od
  configurabilnog praga (npr. 30s). Za njih re-saljemo poslednju poruku
  (ili CHECK_STATUS ako ne znamo gde smo stali) i azuriramo lastRetryAt.

 KONFIGURACIJA (application.properties):
   interbank.retry.interval-seconds=30
   interbank.retry.max-retries=10
   interbank.retry.stuck-timeout-minutes=30

 GLAVNI STEP:
  1. Ucitaj sve in-progress tx gde lastRetryAt < now - interval
  2. Za svaku:
     a. Ako retryCount >= maxRetries:
         - Postavi status=STUCK, failureReason="Max retries exceeded"
         - Oslobodi lokalne rezervacije (safety)
         - Log ERROR (supervizor treba intervenciju)
     b. Inace:
         - posalji CHECK_STATUS poruku partnerskoj banci (ili re-send last)
         - azuriraj retryCount++ i lastRetryAt=now
  3. Obradjuj odgovor asinhrono — idempotentno kroz InterbankMessageService.

 IDEMPOTENCY:
  Poruke su identifikovane sa messageId. Druga banka kada dobije istu
  poruku drugi put (ili CHECK_STATUS) vraca isti rezultat.

 TESTOVI:
  - Retry triggers after configured interval
  - Max retries → STUCK
  - Successful retry → clean up retryCount
================================================================================
*/
@Component
public class InterbankRetryScheduler {

    // TODO: injectovati InterbankTransactionRepository, InterbankClient, InterbankMessageService

    /**
     * TODO: implementiraj petlju kroz in-progress transakcije.
     * Cron svaka 2 minute je dovoljno — ako hoces brzi reagovanje,
     * snizi na 30s.
     */
    @Scheduled(fixedRate = 120_000)
    public void retryStaleTransactions() {
        // TODO: implementirati
    }
}
