package rs.raf.banka2_bek.interbank.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/*
================================================================================
 TODO — PAYLOAD PORUKE COMMIT (faza 2 2PC)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 410-411
--------------------------------------------------------------------------------
 Banka A -> Banka B posle uspesnog READY odgovora. Ovim se finalizuje
 transakcija: Banka A skida rezervisana sredstva sa racuna posiljaoca
 i salje Banci B info da uplati na racun primaoca. Sadrzi sve podatke
 iz READY odgovora plus transactionId.
================================================================================
*/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCommitPayloadDto {
    private BigDecimal finalAmount;
    private String finalCurrency;
    private String receiverAccountNumber;
    private String description;
}
