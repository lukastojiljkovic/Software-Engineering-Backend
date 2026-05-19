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
 * Phase 4 v3.5 — kreira BUY order. Pun signature CreateOrderDto-a.
 * Tip ordera se izvodi iz prisustva limit/stop polja:
 *  - bez limit/stop → MARKET
 *  - samo limit → LIMIT
 *  - samo stop → STOP
 *  - oba → STOP_LIMIT
 *
 * <p>Faza 2f: order se kreira preko {@link TradingServiceClient} ({@code POST
 * /orders} na trading-service, JWT pozivaoca) umesto in-process
 * {@code OrderService}. Logika, validacija i OTP gating su nepromenjeni.
 */
@Component
@RequiredArgsConstructor
public class CreateBuyOrderActionHandler implements WriteToolHandler {

    private final TradingServiceClient tradingServiceClient;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "create_buy_order"; }

    @Override
    public boolean requiresOtp() { return true; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Kreira BUY order za hartiju od vrednosti. Tip ordera se " +
                        "izvodi iz prisustva limitValue/stopValue. Zahteva potvrdu + OTP. " +
                        "Pozovi samo ako korisnik EKSPLICITNO trazi kupovinu hartije.")
                .param(new ToolDefinition.Param("listingId", "integer",
                        "ID listing-a (hartije od vrednosti) za kupovinu", true, null, null))
                .param(new ToolDefinition.Param("ticker", "string",
                        "Ticker simbol kao alternativa listingId-u (npr. AAPL, MSFT)", false, null, null))
                .param(new ToolDefinition.Param("quantity", "integer",
                        "Broj hartija za kupovinu (>= 1)", true, null, null))
                .param(new ToolDefinition.Param("accountId", "integer",
                        "ID racuna sa kog ce biti placanje", true, null, null))
                .param(new ToolDefinition.Param("limitValue", "number",
                        "Maksimalna cena (LIMIT order). Bez ove i stopValue → MARKET.",
                        false, null, null))
                .param(new ToolDefinition.Param("stopValue", "number",
                        "Aktivaciona cena (STOP order)", false, null, null))
                .param(new ToolDefinition.Param("allOrNone", "boolean",
                        "AON flag — order se izvrsava odjednom ili nikako",
                        false, false, null))
                .param(new ToolDefinition.Param("margin", "boolean",
                        "Margin flag — koristi pozajmljena sredstva", false, false, null))
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

        String orderType = orderTypeFor(limitValue, stopValue);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Smer", "KUPOVINA (BUY)");
        fields.put("Hartija", listing.ticker() + " (" + listing.name() + ")");
        fields.put("Kolicina", quantity);
        fields.put("Tip naloga", orderType);
        if (limitValue != null) fields.put("Limit cena", limitValue.toPlainString());
        if (stopValue != null) fields.put("Stop cena", stopValue.toPlainString());
        Boolean aon = support.getBool(args, "allOrNone");
        if (Boolean.TRUE.equals(aon)) fields.put("AON", "Da (sve ili nista)");
        Boolean margin = support.getBool(args, "margin");
        if (Boolean.TRUE.equals(margin)) fields.put("Margin", "Da (pozajmljena sredstva)");

        BigDecimal approxPerUnit = limitValue != null ? limitValue
                : (stopValue != null ? stopValue : listing.price());
        if (approxPerUnit != null) {
            BigDecimal approxTotal = approxPerUnit.multiply(BigDecimal.valueOf(quantity));
            fields.put("Priblizna ukupna cena", approxTotal.toPlainString());
        }

        List<String> warnings = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(margin)) {
            warnings.add("Margin order koristi pozajmljena sredstva — povecan rizik gubitka.");
        }

        String summary = "BUY " + quantity + "× " + listing.ticker() + " (" + orderType + ")";
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
                orderTypeFor(limitValue, stopValue),
                support.getInt(args, "quantity"),
                cs == null ? 1 : cs,
                "BUY",
                limitValue,
                stopValue,
                Boolean.TRUE.equals(support.getBool(args, "allOrNone")),
                Boolean.TRUE.equals(support.getBool(args, "margin")),
                support.getLong(args, "accountId"),
                otpCode,
                support.getLong(args, "fundId"));

        TsOrder created = tradingServiceClient.createOrder(req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", created.id());
        result.put("status", created.status());
        result.put("orderType", created.orderType());
        result.put("quantity", created.quantity());
        result.put("listingId", created.listingId());
        return result;
    }

    static String orderTypeFor(BigDecimal limitValue, BigDecimal stopValue) {
        boolean hasLimit = limitValue != null && limitValue.signum() > 0;
        boolean hasStop = stopValue != null && stopValue.signum() > 0;
        if (hasLimit && hasStop) return "STOP_LIMIT";
        if (hasLimit) return "LIMIT";
        if (hasStop) return "STOP";
        return "MARKET";
    }
}
