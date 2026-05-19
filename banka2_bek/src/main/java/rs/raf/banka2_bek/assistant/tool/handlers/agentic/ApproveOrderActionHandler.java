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
 * Phase 4 v3.5 — Supervizor odobrava pending order agenta.
 *
 * <p>Faza 2f: poziv ide preko {@link TradingServiceClient} ({@code PATCH
 * /orders/{id}/approve} na trading-service, JWT pozivaoca).
 */
@Component
@RequiredArgsConstructor
public class ApproveOrderActionHandler implements WriteToolHandler {

    private final TradingServiceClient tradingServiceClient;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "approve_order"; }

    @Override
    public List<String> allowedRoles() { return List.of("EMPLOYEE"); }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Supervizor odobrava pending order. Samo zaposleni sa " +
                        "SUPERVISOR permisijom (BE proverava).")
                .param(new ToolDefinition.Param("orderId", "integer",
                        "ID order-a za odobravanje", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long orderId = support.getLong(args, "orderId");
        if (orderId == null) throw new IllegalArgumentException("orderId je obavezan");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Order ID", orderId);
        fields.put("Akcija", "Odobravanje (APPROVE)");
        return new PreviewResult("Odobri order #" + orderId, fields,
                List.of("Posle odobravanja agent moze nastaviti sa izvrsenjem (rezervisu se sredstva ili akcije)."));
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        TsOrder dto = tradingServiceClient.approveOrder(support.getLong(args, "orderId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", dto.id());
        result.put("status", dto.status());
        result.put("approvedBy", dto.approvedBy());
        return result;
    }
}
