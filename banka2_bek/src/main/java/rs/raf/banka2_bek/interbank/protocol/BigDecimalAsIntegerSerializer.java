package rs.raf.banka2_bek.interbank.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Serijalizuje {@link BigDecimal} kao GO JSON integer (bez decimala) na žicu.
 *
 * <p><b>Zasto postoji:</b> protokol §2.7.2 zahteva da je {@code OptionDescription.amount}
 * CEO broj ({@code amount mora biti integer}). Banka 1 (Go) ga unmarshal-uje u
 * {@code int}, pa svaki {@code 2.0} / {@code 2.0000} na žici → HTTP 400
 * {@code cannot unmarshal number 2.0000 into int}.
 *
 * <p><b>Zasto NE {@code @JsonFormat(Shape.NUMBER_INT)}:</b> taj shape je NEEFEKTIVAN na
 * {@link BigDecimal} polju — BigDecimal je vec broj, pa Jackson ne odbacuje scale.
 * U kombinaciji sa {@code WRITE_BIGDECIMAL_AS_PLAIN} (koji interbank mapper ima zbog
 * §2.5 MonetaryValue) vrednost {@code new BigDecimal("2.0000")} (scale=4, iz DB kolone
 * {@code precision=19, scale=4}) je isla na žicu doslovno kao {@code 2.0000}. Custom
 * serializer je MAPPER-NEZAVISAN: emituje bare integer bez obzira na feature-e mappera.
 *
 * <p>Round-trip: deserijalizacija ostaje default ({@code BigDecimal}), pa
 * {@code "amount":2} → {@code BigDecimal("2")} (call-site-ovi rade {@code amount × strike}
 * BigDecimal matematiku — zato tip ostaje BigDecimal, ne {@code int}).
 *
 * <p>Defenzivno: ako amount ima ne-nula frakcioni deo ({@code 2.5}), to je protokolarna
 * greska (amount MORA biti ceo broj) — bacamo {@link IllegalArgumentException} umesto da
 * tiho odbacimo decimale i posaljemo pogresnu kolicinu opcija na žicu.
 */
public class BigDecimalAsIntegerSerializer extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        BigInteger asInt;
        try {
            // toBigIntegerExact() baca ArithmeticException ako postoji ne-nula frakcija
            // (npr. 2.5) — bolje fail-loud nego tiho odseci decimale i poslati 2 umesto 2.5.
            asInt = value.toBigIntegerExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "OptionDescription.amount mora biti ceo broj (§2.7.2), a dobijeno: " + value, ex);
        }
        gen.writeNumber(asInt);
    }
}
