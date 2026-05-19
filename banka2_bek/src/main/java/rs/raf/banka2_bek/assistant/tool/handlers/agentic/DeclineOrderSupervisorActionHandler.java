package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.client.TradingServiceClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsOrder;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — Supervizor odbija pending order agenta.
 *
 * <p>Faza 2f: poziv ide preko {@link TradingServiceClient} ({@code PATCH
 * /orders/{id}/decline} na trading-service, JWT pozivaoca).
 */
@Component
@RequiredArgsConstructor
public class DeclineOrderSupervisorActionHandler implements WriteToolHandler {

    private final TradingServiceClient tradingServiceClient;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "decline_order_supervisor"; }

    @Override
    public List<String> allowedRoles() { return List.of("EMPLOYEE"); }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Supervizor odbija pending order agenta. Razlikuje se od " +
                        "cancel_order koji moze biti pozvan od bilo kog usera (svoji orderi).")
                .param(new ToolDefinition.Param("orderId", "integer",
                        "ID order-a za odbijanje", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long orderId = support.getLong(args, "orderId");
        if (orderId == null) throw new IllegalArgumentException("orderId je obavezan");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Order ID", orderId);
        fields.put("Akcija", "Odbijanje (DECLINE)");
        return new PreviewResult("Odbij order #" + orderId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        TsOrder dto = tradingServiceClient.declineOrder(support.getLong(args, "orderId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", dto.id());
        result.put("status", dto.status());
        return result;
    }
}
