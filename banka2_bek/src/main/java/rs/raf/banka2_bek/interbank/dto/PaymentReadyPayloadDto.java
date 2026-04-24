package rs.raf.banka2_bek.interbank.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/*
================================================================================
 TODO — PAYLOAD PORUKE READY (odgovor na Prepare, 2PC)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 393-401
--------------------------------------------------------------------------------
 Banka B odgovara Banci A: moze da obradi transakciju.
 Vraca kurs, proviziju, i krajnji iznos koji ce biti uplacen primaocu.

 Alternativa: NOT_READY (u PaymentNotReadyPayloadDto) ako ne moze.
================================================================================
*/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReadyPayloadDto {
    private BigDecimal originalAmount;
    private String originalCurrency;
    private BigDecimal finalAmount;
    private String finalCurrency;
    private BigDecimal exchangeRate;
    private BigDecimal commission;
}
