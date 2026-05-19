package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.client.TradingServiceClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.CreateFundReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsFundDetail;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — supervizor kreira investicioni fond.
 *
 * <p>Faza 2f: poziv ide preko {@link TradingServiceClient} ({@code POST /funds}
 * na trading-service, JWT pozivaoca). Menadzer fonda se na trading-service
 * strani resolvuje iz JWT-a (supervizor koji poziva) — handler vise ne salje
 * {@code userId} eksplicitno.
 */
@Component
@RequiredArgsConstructor
public class CreateFundActionHandler implements WriteToolHandler {

    private final TradingServiceClient tradingServiceClient;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "create_fund"; }

    @Override
    public List<String> allowedRoles() { return List.of("EMPLOYEE"); }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Supervizor kreira novi investicioni fond. Automatski se " +
                        "otvara dinarski racun fonda, supervizor postaje menadzer.")
                .param(new ToolDefinition.Param("name", "string",
                        "Naziv fonda (3-128 karaktera, mora biti unique)", true, null, null))
                .param(new ToolDefinition.Param("description", "string",
                        "Kratak opis investicione strategije", false, null, null))
                .param(new ToolDefinition.Param("minimumContribution", "number",
                        "Minimalni ulog u RSD", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        String fundName = support.getString(args, "name");
        BigDecimal minContrib = support.getBigDecimal(args, "minimumContribution");
        if (fundName == null || fundName.isBlank() || minContrib == null || minContrib.signum() <= 0) {
            throw new IllegalArgumentException("name + minimumContribution > 0 su obavezni");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Naziv fonda", fundName);
        fields.put("Minimalni ulog", minContrib.toPlainString() + " RSD");
        String desc = support.getString(args, "description");
        if (desc != null && !desc.isBlank()) fields.put("Opis", desc);
        return new PreviewResult("Kreiranje fonda: " + fundName, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        CreateFundReq req = new CreateFundReq(
                support.getString(args, "name"),
                support.getString(args, "description"),
                support.getBigDecimal(args, "minimumContribution"));
        TsFundDetail fund = tradingServiceClient.createFund(req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fundId", fund.id());
        result.put("name", fund.name());
        return result;
    }
}
