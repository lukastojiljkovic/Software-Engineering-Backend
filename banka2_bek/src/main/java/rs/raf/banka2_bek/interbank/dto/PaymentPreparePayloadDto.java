package rs.raf.banka2_bek.interbank.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/*
================================================================================
 TODO — PAYLOAD PORUKE PREPARE (faza 1 2PC)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 382-388
--------------------------------------------------------------------------------
 Banka A salje Banci B da pita da li moze da obradi transakciju.
 Sadrzi: pocetni iznos + valutu + identifikaciju posiljaoca i primaoca.
 Banka B vraca READY ili NOT_READY (vidi PaymentReadyPayloadDto).
================================================================================
*/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPreparePayloadDto {
    private BigDecimal amount;
    private String currency;
    private String senderAccountNumber;
    private String senderName;
    private String receiverAccountNumber;
    private String description;
}
