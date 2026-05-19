package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.client.TradingServiceClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.InvestFundReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsFundPosition;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — uplata u investicioni fond.
 *
 * <p>Faza 2f: poziv ide preko {@link TradingServiceClient} ({@code POST
 * /funds/{id}/invest} na trading-service, JWT pozivaoca). Korisnik (klijent ili
 * supervizor u ime banke) se na trading-service strani resolvuje iz JWT-a.
 */
@Component
@RequiredArgsConstructor
public class InvestInFundActionHandler implements WriteToolHandler {

    private final TradingServiceClient tradingServiceClient;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "invest_in_fund"; }

    @Override
    public boolean requiresOtp() { return true; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Uplata u investicioni fond. Klijenti placaju iz svog RSD racuna; " +
                        "supervizori mogu uplatiti u ime banke iz bankin RSD racuna.")
                .param(new ToolDefinition.Param("fundId", "integer",
                        "ID fonda", true, null, null))
                .param(new ToolDefinition.Param("amount", "number",
                        "Iznos u RSD (mora biti >= minimumContribution fonda)", true, null, null))
                .param(new ToolDefinition.Param("accountId", "integer",
                        "ID izvornog racuna (klijentov RSD ili bankin RSD)", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long fundId = support.getLong(args, "fundId");
        BigDecimal amount = support.getBigDecimal(args, "amount");
        Long accountId = support.getLong(args, "accountId");
        if (fundId == null || amount == null || amount.signum() <= 0 || accountId == null) {
            throw new IllegalArgumentException("Nedostaju parametri: fundId, amount > 0, accountId");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Fond ID", fundId);
        fields.put("Iznos", amount.toPlainString() + " RSD");
        fields.put("Sa racuna ID", accountId);
        return new PreviewResult("Uplata " + amount.toPlainString() + " RSD u fond #" + fundId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        // Investicioni fond uvek RSD; LLM moze poslati drugu valutu, mi forsiramo RSD
        String currency = support.getString(args, "currency");
        InvestFundReq req = new InvestFundReq(
                support.getBigDecimal(args, "amount"),
                currency == null ? "RSD" : currency,
                support.getLong(args, "accountId"));
        TsFundPosition pos = tradingServiceClient.investInFund(
                support.getLong(args, "fundId"), req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("positionId", pos.id());
        result.put("totalInvested", pos.totalInvested());
        result.put("currentValue", pos.currentValue());
        return result;
    }
}
