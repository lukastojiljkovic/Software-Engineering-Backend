package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.client.TradingServiceClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsFundTransaction;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.WithdrawFundReq;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — povlacenje sredstava iz fonda. Null amount = sva pozicija.
 *
 * <p>Faza 2f: poziv ide preko {@link TradingServiceClient} ({@code POST
 * /funds/{id}/withdraw} na trading-service, JWT pozivaoca).
 */
@Component
@RequiredArgsConstructor
public class WithdrawFromFundActionHandler implements WriteToolHandler {

    private final TradingServiceClient tradingServiceClient;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "withdraw_from_fund"; }

    @Override
    public boolean requiresOtp() { return true; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Povlacenje novca iz investicionog fonda. Bez amount-a → " +
                        "povlaci celu poziciju. Klijent placa konverzionu proviziju ako je " +
                        "destinacija u drugoj valuti.")
                .param(new ToolDefinition.Param("fundId", "integer", "ID fonda", true, null, null))
                .param(new ToolDefinition.Param("amount", "number",
                        "Iznos u RSD; null/empty = sva pozicija", false, null, null))
                .param(new ToolDefinition.Param("destinationAccountId", "integer",
                        "ID racuna na koji ide novac", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long fundId = support.getLong(args, "fundId");
        Long destAcc = support.getLong(args, "destinationAccountId");
        BigDecimal amount = support.getBigDecimal(args, "amount");
        if (fundId == null || destAcc == null) {
            throw new IllegalArgumentException("fundId + destinationAccountId su obavezni");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Fond ID", fundId);
        fields.put("Iznos", amount == null ? "Cela pozicija" : amount.toPlainString() + " RSD");
        fields.put("Na racun ID", destAcc);
        return new PreviewResult("Povlacenje iz fonda #" + fundId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        WithdrawFundReq req = new WithdrawFundReq(
                support.getBigDecimal(args, "amount"),
                support.getLong(args, "destinationAccountId"));
        TsFundTransaction tx = tradingServiceClient.withdrawFromFund(
                support.getLong(args, "fundId"), req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transactionId", tx.id());
        result.put("amount", tx.amountRsd());
        result.put("status", tx.status());
        return result;
    }
}
