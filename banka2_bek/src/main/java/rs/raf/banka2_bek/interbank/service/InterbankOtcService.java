package rs.raf.banka2_bek.interbank.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.interbank.dto.InterbankEnvelopeDto;
import rs.raf.banka2_bek.interbank.dto.OtcInterbankDtos;
import rs.raf.banka2_bek.interbank.model.InterbankTransaction;

import java.util.List;

/*
================================================================================
 TODO — SERVIS ZA INTER-BANK OTC TRGOVINU (PREGOVARANJE + SAGA EXERCISE)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 438-519
--------------------------------------------------------------------------------
 Dve velike celine:
 (A) Pregovaranje o ponudama preko granica banaka (bez SAGA)
 (B) Izvrsavanje opcionog ugovora preko SAGA pattern-a

 (A) PREGOVARANJE — slicno intra-bank OTC (OtcService), ali sa dodatnim
     inter-bank pozivima. Stanje ponude se sinhronizuje izmedju banaka
     kroz poruke.

 METODE (A):
  1. List<OtcListingDto> listRemoteListings();
     - periodicno povuci od partnera (GET {partner}/bank-api/otc/public-listings)
     - cache-iraj lokalno (kratak TTL: 5 min) u OtcInterbankListingDto
     - FE zove ovu metodu na stranici "Iz drugih banaka"

  2. OtcInterbankOfferDto createOffer(CreateOtcInterbankOfferDto dto, Long myUserId);
     - validacija iz spec-a (positive brojevi, settlementDate u buducnosti)
     - generisi offerId (UUID)
     - posalji poruku partnerskoj banci: CREATE_OTC_OFFER
     - sacekaj potvrdu, pa upise lokalno (InterbankOtcOffer tabela)
     - Napomena: InterbankOtcOffer tabela NIJE jos kreirana; koristi novu
       entitet klasu ili prosirenje postojece OtcOffer sa "remotePartnerCode".

  3. OtcInterbankOfferDto counterOffer(CounterOtcInterbankOfferDto dto, Long myUserId);
     - proveri da je my red da odgovori
     - posalji COUNTER_OTC_OFFER poruku, sinhronizuj stanje lokalno

  4. OtcInterbankOfferDto declineOffer(String offerId, Long myUserId);
     - salje DECLINE_OTC_OFFER poruku

  5. OtcInterbankOfferDto acceptOffer(String offerId, Long myAccountId, Long myUserId);
     - salje ACCEPT_OTC_OFFER poruku
     - rezervise premiju sa naseg racuna (FundReservationService)
     - druga banka upise contract lokalno, nasa takodje
     - premia transfer krece kao SAGA (vidi B)

 (B) SAGA EXERCISE — kupac iskoriscava opciju kupovine akcija
     (Spec Celina 4 linije 473-519, ISO shema u liniji 80).

 METODE (B):
  6. InterbankTransaction exerciseContract(String contractId, Long buyerAccountId,
                                            Long buyerUserId);
     - Validacija: my moram biti kupac, contract ACTIVE, settlementDate > today
     - FAZA 1 (lokalno): FundReservationService.reserve totalCost = strike*qty
     - Upise InterbankTransaction tx sa type=OTC, status=INITIATED
     - Pozovi sendReserveShares(tx)

  7. void sendReserveShares(InterbankTransaction tx);
     - FAZA 2: envelope RESERVE_SHARES sa OtcSagaReserveSharesPayloadDto
     - Odgovor RESERVE_SHARES_CONFIRM → sendCommitFunds
     - Odgovor RESERVE_SHARES_FAIL → abort (oslobodi rezervaciju)

  8. void sendCommitFunds(InterbankTransaction tx);
     - FAZA 3: envelope COMMIT_FUNDS sa PaymentCommitPayloadDto analog
     - Odgovor success → cekamo TRANSFER_OWNERSHIP poruku od Banke B

  9. InterbankEnvelopeDto handleTransferOwnership(InterbankEnvelopeDto env);
     - FAZA 4 (primalac, Banka A): upise Portfolio kupca sa akcijama
     - Vrati OWNERSHIP_CONFIRM

  10. void finalConfirm(InterbankTransaction tx);
      - FAZA 5: tx.status = COMMITTED, oslobodi rezervacije
      - Posalji FINAL_CONFIRM envelope (broadcast obema stranama za audit)


 METODE KAO PRIMALAC (Banka B, prodavac iskoristava ugovor):
  11. InterbankEnvelopeDto handleReserveShares(InterbankEnvelopeDto env);
      - validiraj da prodavac ima dovoljno rezervisanih akcija u OtcContract
      - rezervisi u portfoliu (portfolio.reservedQuantity += qty)
      - vrati RESERVE_SHARES_CONFIRM

  12. InterbankEnvelopeDto handleCommitFunds(InterbankEnvelopeDto env);
      - upise prihod na seller racun
      - posalji TRANSFER_OWNERSHIP envelope Banci A (tj. inicira 4. fazu)

  13. InterbankEnvelopeDto handleFinalConfirm(InterbankEnvelopeDto env);
      - oznaci local contract kao EXERCISED


 ROLLBACK SAGA — vidi Celina 4 linije 515-519 i dijagram u 80:
  U svakoj fazi, ako sledeca ne uspe, inicira se kompenzacija:
   - Reserve funds fail -> nema sta, lokalno failuje
   - Reserve shares fail -> oslobodi funds rezervaciju
   - Commit funds fail -> oslobodi funds rezervaciju, posalji Bank B "oslobodi
     shares rezervaciju"
   - Transfer ownership fail -> Bank B vraca shares prodavcu, Bank A refunduje
     funds kupcu
================================================================================
*/
@Service
public class InterbankOtcService {

    // TODO: injectovati: InterbankClient client, InterbankMessageService messages,
    //   BankRoutingService routing, InterbankTransactionRepository txRepo,
    //   OtcOfferRepository intraOffers (reuse?), OtcContractRepository contracts,
    //   PortfolioRepository portfolios, FundReservationService reservations,
    //   AccountRepository accounts, CurrencyConversionService fx

    public List<OtcInterbankDtos.OtcInterbankListingDto> listRemoteListings() {
        // TODO
        throw new UnsupportedOperationException("TODO: implementirati listRemoteListings");
    }

    @Transactional
    public OtcInterbankDtos.OtcInterbankOfferDto createOffer(
            OtcInterbankDtos.CreateOtcInterbankOfferDto dto, Long myUserId) {
        throw new UnsupportedOperationException("TODO: implementirati createOffer");
    }

    @Transactional
    public OtcInterbankDtos.OtcInterbankOfferDto counterOffer(
            OtcInterbankDtos.CounterOtcInterbankOfferDto dto, Long myUserId) {
        throw new UnsupportedOperationException("TODO: implementirati counterOffer");
    }

    @Transactional
    public OtcInterbankDtos.OtcInterbankOfferDto declineOffer(String offerId, Long myUserId) {
        throw new UnsupportedOperationException("TODO: implementirati declineOffer");
    }

    @Transactional
    public OtcInterbankDtos.OtcInterbankOfferDto acceptOffer(String offerId, Long myAccountId, Long myUserId) {
        throw new UnsupportedOperationException("TODO: implementirati acceptOffer");
    }

    @Transactional
    public InterbankTransaction exerciseContract(String contractId, Long buyerAccountId, Long buyerUserId) {
        throw new UnsupportedOperationException("TODO: implementirati exerciseContract");
    }

    @Transactional
    public void sendReserveShares(InterbankTransaction tx) {
        throw new UnsupportedOperationException("TODO: implementirati sendReserveShares");
    }

    @Transactional
    public void sendCommitFunds(InterbankTransaction tx) {
        throw new UnsupportedOperationException("TODO: implementirati sendCommitFunds");
    }

    @Transactional
    public InterbankEnvelopeDto handleTransferOwnership(InterbankEnvelopeDto env) {
        throw new UnsupportedOperationException("TODO: implementirati handleTransferOwnership");
    }

    @Transactional
    public void finalConfirm(InterbankTransaction tx) {
        throw new UnsupportedOperationException("TODO: implementirati finalConfirm");
    }

    @Transactional
    public InterbankEnvelopeDto handleReserveShares(InterbankEnvelopeDto env) {
        throw new UnsupportedOperationException("TODO: implementirati handleReserveShares");
    }

    @Transactional
    public InterbankEnvelopeDto handleCommitFunds(InterbankEnvelopeDto env) {
        throw new UnsupportedOperationException("TODO: implementirati handleCommitFunds");
    }

    @Transactional
    public InterbankEnvelopeDto handleFinalConfirm(InterbankEnvelopeDto env) {
        throw new UnsupportedOperationException("TODO: implementirati handleFinalConfirm");
    }
}
