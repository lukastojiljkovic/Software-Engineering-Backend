package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.client.TradingServiceClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsActuaryInfo;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — supervizor resetuje usedLimit agenta na 0 (van auto-reset 23:59h).
 *
 * <p>Faza 2f: poziv ide preko {@link TradingServiceClient} ({@code PATCH
 * /actuaries/{id}/reset-limit} na trading-service, JWT pozivaoca).
 */
@Component
@RequiredArgsConstructor
public class ResetActuaryUsedLimitActionHandler implements WriteToolHandler {

    private final TradingServiceClient tradingServiceClient;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "reset_actuary_used_limit"; }

    @Override
    public List<String> allowedRoles() { return List.of("EMPLOYEE"); }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Supervizor resetuje agentov usedLimit na 0. Korisno kad agent " +
                        "treba da nastavi trgovinu posle prekoracenja, pre auto-reseta u 23:59h.")
                .param(new ToolDefinition.Param("employeeId", "integer",
                        "ID employee-ja agenta", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long employeeId = support.getLong(args, "employeeId");
        if (employeeId == null) throw new IllegalArgumentException("employeeId je obavezan");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Agent ID", employeeId);
        fields.put("Akcija", "Reset usedLimit-a na 0");
        return new PreviewResult("Reset usedLimit agenta #" + employeeId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        TsActuaryInfo resp = tradingServiceClient.resetActuaryUsedLimit(support.getLong(args, "employeeId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", resp.employeeId());
        result.put("usedLimit", resp.usedLimit());
        return result;
    }
}
