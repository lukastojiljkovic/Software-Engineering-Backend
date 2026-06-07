package rs.raf.banka2_bek.interbank.protocol;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Spec ref: protokol §2.7.2 Options
 *
 * Opcioni ugovor formiran kroz prihvatanje OTC ponude (§3.6.1).
 * negotiationId — ID OTC pregovora koji je rodio opciju (osigurava da se
 * pseudo-account TxAccount.OPTION nalazi kod prodavca).
 *
 * INVARIJANTE:
 *  - amount mora biti integer > 0
 *  - settlementDate u ISO8601 sa zonom (§2.4)
 *  - posle settlementDate-a opcija nije izvrsiva; banka prodavca otpusta
 *    rezervaciju i markira opciju kao iskoriscenu
 */
public record OptionDescription(
        ForeignBankId negotiationId,
        StockDescription stock,
        MonetaryValue pricePerUnit,
        OffsetDateTime settlementDate,
        /**
         * Broj akcija u opciji (k) — CEO broj > 0 (§2.7.2 "amount mora biti integer").
         *
         * <p>Tip ostaje {@link BigDecimal} (call-site-ovi rade novcanu matematiku
         * {@code amount × strike} i porede sa {@code SharePosition.amount()} BigDecimal-om
         * — isti razlog kao R4-1773 na {@code InterbankOtcContract.quantity}), ali se NA
         * ZICI serijalizuje kao bare JSON integer preko {@link BigDecimalAsIntegerSerializer}.
         *
         * <p><b>P0 [INTEROP] fix:</b> ranije je ovde stajao {@code @JsonFormat(NUMBER_INT)},
         * ali je on NEEFEKTIVAN na BigDecimal polju — u kombinaciji sa
         * {@code WRITE_BIGDECIMAL_AS_PLAIN} (koji interbank mapper ima zbog §2.5) vrednost
         * {@code new BigDecimal("2.0000")} (scale=4, iz DB kolone {@code scale=4}) je isla
         * na žicu doslovno kao {@code 2.0000} → Banka 1 (Go {@code int} unmarshal) je vracala
         * HTTP 400 {@code cannot unmarshal number 2.0000 into int} za svaki accept/exercise
         * koji nosi OptionAsset leg. Custom serializer je mapper-nezavisan: uvek emituje
         * bare integer {@code 2}. Round-trip nazad u {@code BigDecimal("2")} ostaje OK
         * (deserijalizacija je default).
         */
        @JsonSerialize(using = BigDecimalAsIntegerSerializer.class)
        BigDecimal amount
) {}
