package rs.raf.banka2_bek.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import rs.raf.banka2_bek.payment.model.PaymentCode;

import java.math.BigDecimal;

@Data
public class CreatePaymentRequestDto {

    @NotBlank(message = "Source account is required")
    @Length(min = 10, max = 20, message = "Account number must be between 10 and 20 characters")
    private String fromAccount;

    // [P2-input-validation-1 / R1 327] kolona payments.to_account_number sada je
    // length=34 (Payment.toAccountNumber) — inter-bank primalac moze imati racun
    // razlicite duzine (Banka 1 = 19 cifara). Cap na 34 (IBAN max) da cross-bank
    // placanje ne padne na DTO validaciji, a i dalje cisto 400 umesto 500.
    @NotBlank(message = "Destination account is required")
    @Length(min = 10, max = 34, message = "Account number must be between 10 and 34 characters")
    private String toAccount;

    // [R1-645] kolona payments.amount je precision=19, scale=4 (15 cifara ispred +
    // 4 decimale). Bez @Digits-a je vrednost sa >4 decimale tiho trunc-ovana, a
    // >15 cifara ispred zareza je proizvodila sirovu DataIntegrityViolation (500)
    // na cuvanju. @Digits(15,4) daje cistu 400 validaciju usklađenu sa kolonom.
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Iznos sme imati najvise 15 cifara i 4 decimale")
    private BigDecimal amount;

    @NotNull(message = "Payment code is required")
    private PaymentCode paymentCode;

    private String referenceNumber;

    // [P2-input-validation-1 / R1 328] svrha placanja se mapira u payments.purpose
    // (length=200) — bez cap-a je FE-ova granica od 256 znakova proizvodila tihu
    // truncation/DataIntegrityViolation. Cap na 200 da BE i kolona budu uskladjeni.
    // (FE granicu od 256 treba spustiti na 200 — vidi FE batch / R1 328.)
    @NotBlank(message = "Description is required")
    @Length(max = 200, message = "Opis (svrha) moze imati najvise 200 znakova")
    private String description;

    private String recipientName;

    // [P2-input-validation-1 / R1 331] OTP mora biti tacno 6 cifara (paritet sa
    // Quick Approve `ApprovePaymentRequest`). Bez ovoga je nevalidan format isao
    // do OtpService.verify pa vracao genericku gresku.
    @NotBlank(message = "Verifikacioni kod je obavezan")
    @Pattern(regexp = "\\d{6}", message = "Verifikacioni kod mora biti 6 cifara")
    private String otpCode;
}

