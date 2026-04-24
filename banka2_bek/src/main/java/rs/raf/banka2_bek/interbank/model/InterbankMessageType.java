package rs.raf.banka2_bek.interbank.model;

/*
================================================================================
 TODO — TIPOVI MEDJUBANKARSKIH PORUKA
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 382-416 (PAYMENT 2PC) i 483-519 (OTC SAGA)
--------------------------------------------------------------------------------
 PAYMENT protokol:
   PREPARE      — Banka A -> Banka B: "mogu li da primim ovu transakciju?"
                  payload: transactionId, amount, currency, sender, receiver
   READY        — Banka B -> Banka A: "mogu. evo kurs + provizija + krajnji iznos"
                  payload: transactionId, amount, convertedAmount, rate, commission
   NOT_READY    — Banka B -> Banka A: "ne mogu, razlog: ..."
                  payload: transactionId, reason
   COMMIT       — Banka A -> Banka B: "potvrdjeno, skini rezervisano i dodaj primaocu"
                  payload: transactionId + svi Ready podaci
   COMMITTED    — Banka B -> Banka A: "uplaceno primaocu"
                  payload: transactionId
   ABORT        — Inicijator -> druga banka: "prekini transakciju"
                  payload: transactionId, reason
   ABORTED      — Potvrda primanja abort-a

 OTC SAGA protokol:
   RESERVE_FUNDS          — Banka kupca rezervise sredstva (lokalno, bez poruke)
   RESERVE_SHARES         — Banka A -> Banka B: rezervisi hartije kod prodavca
   RESERVE_SHARES_CONFIRM — Banka B -> Banka A: rezervisane
   RESERVE_SHARES_FAIL    — Banka B -> Banka A: ne mogu (razlog)
   COMMIT_FUNDS           — Banka A -> Banka B: evo para primaocu
   TRANSFER_OWNERSHIP     — Banka B -> Banka A: hartije prenete kupcu
   OWNERSHIP_CONFIRM      — Banka A -> Banka B: potvrdjen prenos
   FINAL_CONFIRM          — Obe strane: saga uspesna

 Generalno:
   CHECK_STATUS           — Bilo koja strana -> pitanje "gde smo?"
                            (za retry ako nismo sigurni da li je poruka stigla)

 JSON payloadi definisani u `interbank/dto/` paketu — vidi tamo.
================================================================================
*/
public enum InterbankMessageType {
    // PAYMENT 2PC
    PREPARE,
    READY,
    NOT_READY,
    COMMIT,
    COMMITTED,
    ABORT,
    ABORTED,

    // OTC SAGA
    RESERVE_SHARES,
    RESERVE_SHARES_CONFIRM,
    RESERVE_SHARES_FAIL,
    COMMIT_FUNDS,
    TRANSFER_OWNERSHIP,
    OWNERSHIP_CONFIRM,
    FINAL_CONFIRM,

    // Shared
    CHECK_STATUS
}
