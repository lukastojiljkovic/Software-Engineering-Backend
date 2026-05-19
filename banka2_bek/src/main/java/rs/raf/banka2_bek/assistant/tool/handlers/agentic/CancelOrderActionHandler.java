package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.client.TradingServiceClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsOrder;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — otkazuje order (full ili parcijalan).
 *  - quantityToCancel == null → full decline
 *  - quantityToCancel = X (X < remaining) → parcijalni cancel
 *
 * <p>Faza 2f: poziv ide preko {@link TradingServiceClient} ({@code PATCH
 * /orders/{id}/decline} na trading-service, JWT pozivaoca).
 */
@Component
@RequiredArgsConstructor
public class CancelOrderActionHandler implements WriteToolHandler {

    private final TradingServiceClient tradingServiceClient;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "cancel_order"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Otkazuje pending/approved order. Ako se prosledi quantityToCancel " +
                        "manji od remaining-a, parcijalno otkazuje. Bez quantityToCancel → full decline.")
                .param(new ToolDefinition.Param("orderId", "integer",
                        "ID order-a za otkazivanje", true, null, null))
                .param(new ToolDefinition.Param("quantityToCancel", "integer",
                        "Broj jedinica za otkazivanje (null = full)", false, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long orderId = support.getLong(args, "orderId");
        if (orderId == null) {
            throw new IllegalArgumentException("orderId je obavezan");
        }
        Integer qty = support.getInt(args, "quantityToCancel");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Order ID", orderId);
        fields.put("Tip otkazivanja", qty == null ? "Pun decline" : "Parcijalno (" + qty + " jedinica)");

        String summary = qty == null
                ? "Otkazivanje order-a #" + orderId
                : "Parcijalno otkazivanje order-a #" + orderId + " za " + qty + " jedinica";
        return new PreviewResult(summary, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        Long orderId = support.getLong(args, "orderId");
        Integer qty = support.getInt(args, "quantityToCancel");
        TsOrder dto = tradingServiceClient.cancelOrder(orderId, qty);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", dto.id());
        result.put("status", dto.status());
        result.put("remainingPortions", dto.remainingPortions());
        return result;
    }
}
