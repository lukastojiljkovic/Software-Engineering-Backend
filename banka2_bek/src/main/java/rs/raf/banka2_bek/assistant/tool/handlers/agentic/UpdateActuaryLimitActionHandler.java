package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.client.TradingServiceClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsActuaryInfo;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.UpdateActuaryLimitReq;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — supervizor postavlja dnevni limit agenta.
 *
 * <p>Faza 2f: poziv ide preko {@link TradingServiceClient} ({@code PATCH
 * /actuaries/{id}/limit} na trading-service, JWT pozivaoca).
 */
@Component
@RequiredArgsConstructor
public class UpdateActuaryLimitActionHandler implements WriteToolHandler {

    private final TradingServiceClient tradingServiceClient;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "update_actuary_limit"; }

    @Override
    public List<String> allowedRoles() { return List.of("EMPLOYEE"); }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Supervizor postavlja dnevni limit i needApproval flag agenta. " +
                        "Limit u RSD; needApproval=true znaci da supervizor mora odobriti " +
                        "svaki order agenta.")
                .param(new ToolDefinition.Param("employeeId", "integer",
                        "ID employee-ja agenta", true, null, null))
                .param(new ToolDefinition.Param("dailyLimit", "number",
                        "Novi dnevni limit u RSD (>= 0)", true, null, null))
                .param(new ToolDefinition.Param("needApproval", "boolean",
                        "true = svaki order ide na approval supervizoru", false, false, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long employeeId = support.getLong(args, "employeeId");
        BigDecimal limit = support.getBigDecimal(args, "dailyLimit");
        if (employeeId == null || limit == null || limit.signum() < 0) {
            throw new IllegalArgumentException("employeeId + dailyLimit (>= 0) su obavezni");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Agent ID", employeeId);
        fields.put("Novi limit", limit.toPlainString() + " RSD");
        Boolean approval = support.getBool(args, "needApproval");
        if (approval != null) {
            fields.put("Need approval", Boolean.TRUE.equals(approval) ? "Da" : "Ne");
        }
        return new PreviewResult("Postavi limit agentu #" + employeeId + " na " + limit.toPlainString() + " RSD", fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        UpdateActuaryLimitReq req = new UpdateActuaryLimitReq(
                support.getBigDecimal(args, "dailyLimit"),
                support.getBool(args, "needApproval"));
        TsActuaryInfo resp = tradingServiceClient.updateActuaryLimit(
                support.getLong(args, "employeeId"), req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", resp.employeeId());
        result.put("dailyLimit", resp.dailyLimit());
        result.put("needApproval", resp.needApproval());
        return result;
    }
}
