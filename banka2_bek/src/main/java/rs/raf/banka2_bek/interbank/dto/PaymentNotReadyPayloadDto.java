package rs.raf.banka2_bek.interbank.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
================================================================================
 TODO — PAYLOAD PORUKE NOT_READY (odgovor kad Banka B ne moze)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linija 401
--------------------------------------------------------------------------------
 Banka B odgovara "ne mogu da obradim" sa razlogom.
 Razlog: racun ne postoji, nije aktivan, valuta nije podrzana, itd.
 Banka A na osnovu ovoga oslobadja rezervaciju i prekida transakciju.
================================================================================
*/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotReadyPayloadDto {
    private String reasonCode;    // npr. "ACCOUNT_NOT_FOUND", "INACTIVE_ACCOUNT"
    private String reasonMessage; // human-readable
}
