package rs.raf.trading.watchlist.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body za POST /watchlists/{id}/items.
 *
 * <p>FE salje JSON body {@code {"listingId": 123}} (vidi
 * {@code Banka-2-Frontend/src/types/watchlist.ts} AddWatchlistItemRequest).
 * Pre fix-a 26.05.2026 BE je ocekivao {@code @RequestParam("listingId")}
 * (query string), pa request ne match-uje i Spring vraca 400 (FE je videla kao
 * "Failed to add" gresku). Fix: koristi @RequestBody DTO kao i ostali
 * watchlist endpoint-i ({@code POST /watchlists}, {@code PATCH /watchlists/{id}}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddWatchlistItemRequest {
    @NotNull(message = "Listing ID je obavezan")
    private Long listingId;
}
