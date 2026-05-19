package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.client.TradingServiceClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.CounterOtcOfferReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsOtcOffer;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — kontraponuda u OTC pregovaranju.
 *
 * <p>Faza 2f: poziv ide preko {@link TradingServiceClient} ({@code POST
 * /otc/offers/{id}/counter} na trading-service, JWT pozivaoca).
 */
@Component
@RequiredArgsConstructor
public class CounterOtcOfferActionHandler implements WriteToolHandler {

    private final TradingServiceClient tradingServiceClient;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "counter_otc_offer"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Salje kontraponudu na postojecu OTC pregovor. Druga strana " +
                        "ce videti izmene i moze prihvatiti, odbiti ili poslati svoju kontraponudu.")
                .param(new ToolDefinition.Param("offerId", "integer",
                        "ID OTC ponude", true, null, null))
                .param(new ToolDefinition.Param("quantity", "integer",
                        "Nova kolicina akcija", true, null, null))
                .param(new ToolDefinition.Param("pricePerStock", "number",
                        "Nova cena po akciji u valuti listinga", true, null, null))
                .param(new ToolDefinition.Param("premium", "number",
                        "Nova premija za opcioni ugovor", true, null, null))
                .param(new ToolDefinition.Param("settlementDate", "string",
                        "Datum isteka opcije (ISO format YYYY-MM-DD)", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long offerId = support.getLong(args, "offerId");
        Integer quantity = support.getInt(args, "quantity");
        BigDecimal price = support.getBigDecimal(args, "pricePerStock");
        BigDecimal premium = support.getBigDecimal(args, "premium");
        String settlement = support.getString(args, "settlementDate");
        if (offerId == null || quantity == null || price == null || premium == null || settlement == null) {
            throw new IllegalArgumentException("Nedostajuci parametri za kontraponudu");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Offer ID", offerId);
        fields.put("Kolicina", quantity);
        fields.put("Cena po akciji", price.toPlainString());
        fields.put("Premija", premium.toPlainString());
        fields.put("Settlement", settlement);
        return new PreviewResult("Kontraponuda za OTC #" + offerId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        CounterOtcOfferReq req = new CounterOtcOfferReq(
                support.getInt(args, "quantity"),
                support.getBigDecimal(args, "pricePerStock"),
                support.getBigDecimal(args, "premium"),
                LocalDate.parse(support.getString(args, "settlementDate")));
        TsOtcOffer resp = tradingServiceClient.counterOtcOffer(support.getLong(args, "offerId"), req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("offerId", resp.id());
        result.put("status", resp.status());
        result.put("quantity", resp.quantity());
        result.put("pricePerStock", resp.pricePerStock());
        return result;
    }
}
