package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.client.TradingServiceClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.CreateOrderReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsListing;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsOrder;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — kreira SELL order. Identican signature kao BUY.
 *
 * <p>Faza 2f: order se kreira preko {@link TradingServiceClient} ({@code POST
 * /orders} na trading-service, JWT pozivaoca) umesto in-process
 * {@code OrderService}.
 */
@Component
@RequiredArgsConstructor
public class CreateSellOrderActionHandler implements WriteToolHandler {

    private final TradingServiceClient tradingServiceClient;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "create_sell_order"; }

    @Override
    public boolean requiresOtp() { return true; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Kreira SELL order — prodaje hartiju iz portfolija. " +
                        "Tip ordera se izvodi iz prisustva limitValue/stopValue.")
                .param(new ToolDefinition.Param("listingId", "integer",
                        "ID listing-a za prodaju", true, null, null))
                .param(new ToolDefinition.Param("ticker", "string",
                        "Ticker simbol kao alternativa", false, null, null))
                .param(new ToolDefinition.Param("quantity", "integer",
                        "Broj hartija za prodaju (>= 1)", true, null, null))
                .param(new ToolDefinition.Param("accountId", "integer",
                        "ID racuna na koji ce sredstva", true, null, null))
                .param(new ToolDefinition.Param("limitValue", "number",
                        "Minimalna cena (LIMIT)", false, null, null))
                .param(new ToolDefinition.Param("stopValue", "number",
                        "Aktivaciona cena (STOP)", false, null, null))
                .param(new ToolDefinition.Param("allOrNone", "boolean",
                        "AON flag", false, false, null))
                .param(new ToolDefinition.Param("margin", "boolean",
                        "Margin flag", false, false, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long listingId = support.getLong(args, "listingId");
        TsListing listing;
        if (listingId != null) {
            listing = support.findListing(listingId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Listing #" + listingId + " ne postoji"));
        } else {
            String ticker = support.getString(args, "ticker");
            if (ticker == null) {
                throw new IllegalArgumentException("Mora se proslediti listingId ili ticker");
            }
            listing = support.findListingByTicker(ticker)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Hartija sa ticker-om '" + ticker + "' ne postoji"));
        }

        Integer quantity = support.getInt(args, "quantity");
        Long accountId = support.getLong(args, "accountId");
        if (quantity == null || quantity < 1 || accountId == null) {
            throw new IllegalArgumentException("Nedostaju parametri: quantity (>= 1), accountId");
        }
        BigDecimal limitValue = support.getBigDecimal(args, "limitValue");
        BigDecimal stopValue = support.getBigDecimal(args, "stopValue");

        String orderType = CreateBuyOrderActionHandler.orderTypeFor(limitValue, stopValue);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Smer", "PRODAJA (SELL)");
        fields.put("Hartija", listing.ticker() + " (" + listing.name() + ")");
        fields.put("Kolicina", quantity);
        fields.put("Tip naloga", orderType);
        if (limitValue != null) fields.put("Limit cena", limitValue.toPlainString());
        if (stopValue != null) fields.put("Stop cena", stopValue.toPlainString());
        Boolean aon = support.getBool(args, "allOrNone");
        if (Boolean.TRUE.equals(aon)) fields.put("AON", "Da");
        Boolean margin = support.getBool(args, "margin");
        if (Boolean.TRUE.equals(margin)) fields.put("Margin", "Da");

        BigDecimal approxPerUnit = limitValue != null ? limitValue
                : (stopValue != null ? stopValue : listing.price());
        if (approxPerUnit != null) {
            BigDecimal approxTotal = approxPerUnit.multiply(BigDecimal.valueOf(quantity));
            fields.put("Priblizan prihod", approxTotal.toPlainString());
        }

        List<String> warnings = new java.util.ArrayList<>();
        warnings.add("Prodaja smanjuje portfolio, profit/gubitak se obracunava i porez na " +
                "kapitalnu dobit (15%) ide na drzavni racun mesecno.");

        String summary = "SELL " + quantity + "× " + listing.ticker() + " (" + orderType + ")";
        return new PreviewResult(summary, fields, warnings);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        Long listingId = support.getLong(args, "listingId");
        if (listingId == null) {
            String ticker = support.getString(args, "ticker");
            listingId = support.findListingByTicker(ticker)
                    .orElseThrow(() -> new IllegalArgumentException("Hartija ne postoji"))
                    .id();
        }
        BigDecimal limitValue = support.getBigDecimal(args, "limitValue");
        BigDecimal stopValue = support.getBigDecimal(args, "stopValue");
        Integer cs = support.getInt(args, "contractSize");
        CreateOrderReq req = new CreateOrderReq(
                listingId,
                CreateBuyOrderActionHandler.orderTypeFor(limitValue, stopValue),
                support.getInt(args, "quantity"),
                cs == null ? 1 : cs,
                "SELL",
                limitValue,
                stopValue,
                Boolean.TRUE.equals(support.getBool(args, "allOrNone")),
                Boolean.TRUE.equals(support.getBool(args, "margin")),
                support.getLong(args, "accountId"),
                otpCode,
                null); // SELL nije fund-trgovina (kao stari CreateSellOrderActionHandler)

        TsOrder created = tradingServiceClient.createOrder(req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", created.id());
        result.put("status", created.status());
        result.put("orderType", created.orderType());
        result.put("quantity", created.quantity());
        result.put("listingId", created.listingId());
        return result;
    }
}
