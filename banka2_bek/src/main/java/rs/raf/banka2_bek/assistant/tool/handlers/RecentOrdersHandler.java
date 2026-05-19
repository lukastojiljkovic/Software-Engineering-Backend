package rs.raf.banka2_bek.assistant.tool.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.client.TradingServiceClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsOrder;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.ToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * get_recent_orders(limit) -> {orderCount, orders[]}
 *
 * Vraca poslednjih N ordera korisnika (sortirano po lastModification desc).
 * Klijenti i zaposleni dobijaju samo SVOJE ordere.
 *
 * <p>Faza 2f: orderi vise ne dolaze iz in-process {@code OrderRepository}
 * (trgovinski domen je iseljen u {@code trading-service}) nego preko
 * {@link TradingServiceClient} — {@code GET /orders/my} na trading-service-u,
 * koji vec filtrira po JWT korisniku i sortira po {@code lastModification}.
 */
@Component
@RequiredArgsConstructor
public class RecentOrdersHandler implements ToolHandler {

    private final TradingServiceClient tradingServiceClient;
    private final AssistantProperties properties;

    @Override
    public boolean isEnabled() {
        return properties.getTools().getInternal().isRecentOrders();
    }

    @Override
    public String name() {
        return "get_recent_orders";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Vraca poslednjih N ordera korisnika sa osnovnim " +
                        "informacijama (ticker, smer, kolicina, status, datum). " +
                        "Pozovi kad korisnik pita 'koje ordere imam', 'sta sam " +
                        "kupio', 'sta mi je status'.")
                .param(new ToolDefinition.Param("limit", "integer",
                        "Broj poslednjih ordera (1-20).", false, 5, null))
                .build();
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, UserContext user) {
        int limit = Math.min(Math.max(parseInt(args.get("limit"), 5), 1), 20);
        List<TsOrder> orders = tradingServiceClient.recentOrders(limit);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TsOrder o : orders) {
            rows.add(Map.of(
                    "id", o.id(),
                    "ticker", o.listingTicker() != null ? o.listingTicker() : "?",
                    "direction", o.direction() != null ? o.direction() : "?",
                    "type", o.orderType() != null ? o.orderType() : "?",
                    "quantity", o.quantity() != null ? o.quantity() : 0,
                    "status", o.status() != null ? o.status() : "?",
                    "isDone", o.isDone(),
                    "lastModification", o.lastModification() != null
                            ? o.lastModification() : "-"
            ));
        }
        return Map.of(
                "orderCount", rows.size(),
                "orders", rows
        );
    }

    private static int parseInt(Object raw, int fallback) {
        if (raw == null) return fallback;
        try { return Integer.parseInt(String.valueOf(raw)); }
        catch (NumberFormatException e) { return fallback; }
    }
}
