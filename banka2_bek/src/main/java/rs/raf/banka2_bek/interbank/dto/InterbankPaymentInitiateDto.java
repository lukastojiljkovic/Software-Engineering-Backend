package rs.raf.banka2_bek.interbank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/*
================================================================================
 TODO — DTO ZA KLIJENTA: INICIRANJE INTER-BANK PLACANJA
 Zaduzen: BE tim
--------------------------------------------------------------------------------
 Salje ga FE kad korisnik odradi Novo Placanje gde receiverAccountNumber ne
 pocinje sa nasim bank prefixom (prve 3 cifre racuna).

 Flow:
  1. FE -> POST /interbank/payments/initiate sa ovim DTO
  2. BE proverava da li je destinacija druga banka (po prva 3 cifre)
  3. BE proverava senderu raspoloziva sredstva + rezervise
  4. BE kreira InterbankTransaction sa status=INITIATED
  5. BE salje PREPARE poruku
  6. Vraca FE-u transactionId i info "u obradi"

 NAPOMENA:
  - Ako je destinacija NASA banka, koristi postojeci intra-bank flow
    (/transfers/internal ili /payments). Inter-bank endpoint se zove samo
    za prave inter-bank placanja.
  - OTP flow: razmotriti da OTP validacija bude pre kreiranja InterbankTransaction.
================================================================================
*/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterbankPaymentInitiateDto {

    @NotBlank
    private String senderAccountNumber;

    @NotBlank
    private String receiverAccountNumber;

    @NotBlank
    private String receiverName;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String currency;

    private String description;

    private String otpCode; // OTP validacija po existing pattern-u
}
