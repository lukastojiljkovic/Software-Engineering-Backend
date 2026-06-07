package rs.raf.banka2_bek.interbank.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-4 [INTEROP] — protokol §2.7.2 zahteva da je {@code OptionDescription.amount}
 * JSON INTEGER (npr. {@code 3}). {@code amount} je {@link BigDecimal} (call-site-ovi
 * rade {@code amount × strike} novcanu matematiku — vidi R4-1773), pa je Jackson
 * default emitovao {@code 3.0} (scale). Banka 1 (Go {@code int} unmarshal) je tada
 * vracala HTTP 400 ({@code cannot unmarshal number 3.0 into int}) za svaki accept/
 * exercise koji nosi OptionAsset leg.
 *
 * <p>Fix: {@code @JsonFormat(shape = NUMBER_INT)} na polju {@code amount} →
 * serijalizuje se kao {@code 3} i round-trip-uje nazad u {@code BigDecimal("3")}.
 *
 * <p>ObjectMapper konfiguracija je ista kao u InterbankClient/TransactionExecutor
 * (JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS disabled) — reprodukuje stvarnu žicu.
 */
class OptionDescriptionSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private OptionDescription optionWithAmount(BigDecimal amount) {
        return new OptionDescription(
                new ForeignBankId(222, "neg-1"),
                new StockDescription("AAPL"),
                new MonetaryValue(CurrencyCode.USD, BigDecimal.valueOf(150)),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
                amount);
    }

    @Test
    @DisplayName("BUG-4: amount=3 serijalizuje se kao JSON integer 3 (ne 3.0)")
    void amount_serializesAsInteger() throws Exception {
        String json = objectMapper.writeValueAsString(optionWithAmount(BigDecimal.valueOf(3)));

        // String-level: NEMA "3.0", IMA "3" (Go int unmarshal zahtev §2.7.2).
        assertThat(json).contains("\"amount\":3");
        assertThat(json).doesNotContain("\"amount\":3.0");

        // Token-level: cvor je integralan broj (isInt), ne floating-point.
        JsonNode amountNode = objectMapper.readTree(json).get("amount");
        assertThat(amountNode.isInt()).isTrue();
        assertThat(amountNode.asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("BUG-4: amount round-trip-uje nazad u BigDecimal(3)")
    void amount_roundTripsBackToThree() throws Exception {
        OptionDescription original = optionWithAmount(BigDecimal.valueOf(3));
        String json = objectMapper.writeValueAsString(original);

        OptionDescription parsed = objectMapper.readValue(json, OptionDescription.class);

        assertThat(parsed.amount()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    @DisplayName("BUG-4: vise vrednosti (1, 50, 100) sve emituju integer formu")
    void variousAmounts_allEmitIntegers() throws Exception {
        for (long k : new long[]{1L, 50L, 100L}) {
            String json = objectMapper.writeValueAsString(optionWithAmount(BigDecimal.valueOf(k)));
            assertThat(json)
                    .as("amount=%d mora biti integer na žici", k)
                    .contains("\"amount\":" + k)
                    .doesNotContain("\"amount\":" + k + ".0");
        }
    }

    @Test
    @DisplayName("BUG-4: OptionAsset wrapper (Asset sealed) takodje emituje integer amount")
    void optionAssetWrapper_emitsIntegerAmount() throws Exception {
        Asset asset = new Asset.OptionAsset(optionWithAmount(BigDecimal.valueOf(3)));

        String json = objectMapper.writeValueAsString(asset);

        assertThat(json).contains("\"amount\":3");
        assertThat(json).doesNotContain("\"amount\":3.0");
    }

    // ─── P0 [INTEROP] live-wire reprodukcija ──────────────────────────────────
    // Prethodni testovi (gore) su koristili (a) SVEZ `new ObjectMapper()` BEZ
    // produkcijskih feature-a i (b) `BigDecimal.valueOf(3)` koji vec ima scale=0.
    // Live problem: `entity.getAmount()` dolazi iz kolone `precision=19, scale=4`
    // pa je vrednost `new BigDecimal("2.0000")` (scale=4), a serijalizuje je
    // BAS `interbankObjectMapper` (sa WRITE_BIGDECIMAL_AS_PLAIN). U toj kombinaciji
    // je @JsonFormat(NUMBER_INT) BIO NEEFEKTIVAN → na žicu je islo "2.0000" →
    // Banka 1 (Go `int` unmarshal) je vracala HTTP 400.

    /**
     * Produkcijski mapper — IDENTICAN bean-u iz {@link rs.raf.banka2_bek.interbank.config.InterbankConfig}.
     * Ovo je kljucna razlika u odnosu na prethodne testove: WRITE_BIGDECIMAL_AS_PLAIN je
     * ono sto je gazilo NUMBER_INT na BigDecimal-u sa scale-om.
     */
    @SuppressWarnings("deprecation")
    private static ObjectMapper interbankMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    @Test
    @DisplayName("P0 LIVE: amount=BigDecimal(\"2.0000\") (scale=4, iz DB kolone) preko interbankObjectMapper-a emituje \"amount\":2 (NE 2.0000)")
    void scaleBearingAmount_viaInterbankMapper_emitsBareInteger() throws Exception {
        ObjectMapper mapper = interbankMapper();
        // BAS ono sto entity.getAmount() vraca: precision=19, scale=4.
        OptionDescription opt = optionWithAmount(new BigDecimal("2.0000"));

        String json = mapper.writeValueAsString(opt);

        assertThat(json).contains("\"amount\":2");
        assertThat(json).doesNotContain("2.0000");
        assertThat(json).doesNotContain("\"amount\":2.0");

        JsonNode amountNode = mapper.readTree(json).get("amount");
        assertThat(amountNode.isIntegralNumber())
                .as("amount cvor mora biti integralan (Go int unmarshal)")
                .isTrue();
        assertThat(amountNode.asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("P0 LIVE: OptionAsset (Asset.OPTION) wrapper sa scale-4 amount preko interbankObjectMapper-a → \"amount\":2")
    void scaleBearingAmount_optionAssetWrapper_viaInterbankMapper_emitsBareInteger() throws Exception {
        ObjectMapper mapper = interbankMapper();
        Asset asset = new Asset.OptionAsset(optionWithAmount(new BigDecimal("2.0000")));

        String json = mapper.writeValueAsString(asset);

        assertThat(json).contains("\"amount\":2");
        assertThat(json).doesNotContain("2.0000");
        assertThat(json).doesNotContain("\"amount\":2.0");
    }

    @Test
    @DisplayName("P0 LIVE: scale-4 amount round-trip-uje nazad u BigDecimal(2) preko interbankObjectMapper-a")
    void scaleBearingAmount_viaInterbankMapper_roundTrips() throws Exception {
        ObjectMapper mapper = interbankMapper();
        OptionDescription original = optionWithAmount(new BigDecimal("2.0000"));

        String json = mapper.writeValueAsString(original);
        OptionDescription parsed = mapper.readValue(json, OptionDescription.class);

        assertThat(parsed.amount()).isEqualByComparingTo(BigDecimal.valueOf(2));
    }
}
