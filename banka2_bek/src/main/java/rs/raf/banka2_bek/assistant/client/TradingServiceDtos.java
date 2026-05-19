package rs.raf.banka2_bek.assistant.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lokalni mirror DTO record-i za {@code trading-service} PUBLIC API (faza 2f).
 *
 * <p>banka-core NE zavisi od {@code trading-service} Maven modula, pa se
 * trgovinski {@code rs.raf.trading.*} DTO-i ne mogu import-ovati. Ovde su mali
 * record-i koji preslikavaju SAMO polja koja Arbitro handler-i citaju/salju:
 * <ul>
 *   <li>Request record-i (npr. {@link CreateOrderReq}) serijalizuju se imenom
 *       komponente — poklapa se sa {@code @Data} bean poljima trading-service
 *       DTO-a koje kontroleri ocekuju u telu zahteva.</li>
 *   <li>Response record-i ({@code @JsonIgnoreProperties(ignoreUnknown = true)})
 *       deserijalizuju samo potrebna polja; ostatak trading-service odgovora se
 *       ignorise.</li>
 * </ul>
 */
public final class TradingServiceDtos {

    private TradingServiceDtos() {
    }

    /* ─────────────────────────── Listings ─────────────────────────── */

    /** Hartija od vrednosti — preslikava {@code trading.stock.dto.ListingDto}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TsListing(
            Long id,
            String ticker,
            String name,
            BigDecimal price,
            String listingType,
            String exchangeAcronym,
            String quoteCurrency) {
    }

    /* ──────────────────────────── Orders ──────────────────────────── */

    /** Order — preslikava {@code trading.order.dto.OrderDto}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TsOrder(
            Long id,
            Long listingId,
            String listingTicker,
            String orderType,
            Integer quantity,
            String direction,
            String status,
            String approvedBy,
            boolean isDone,
            Integer remainingPortions,
            String lastModification) {
    }

    /**
     * Telo za {@code POST /orders} — preslikava
     * {@code trading.order.dto.CreateOrderDto}. {@code @JsonInclude(NON_NULL)}
     * izostavlja opcione parametre (limit/stop/fundId) iz JSON-a.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateOrderReq(
            Long listingId,
            String orderType,
            Integer quantity,
            Integer contractSize,
            String direction,
            BigDecimal limitValue,
            BigDecimal stopValue,
            boolean allOrNone,
            boolean margin,
            Long accountId,
            String otpCode,
            Long fundId) {
    }

    /* ───────────────────────────── OTC ────────────────────────────── */

    /** OTC ponuda — preslikava {@code trading.otc.dto.OtcOfferDto}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TsOtcOffer(
            Long id,
            Long listingId,
            Long buyerId,
            Long sellerId,
            Integer quantity,
            BigDecimal pricePerStock,
            BigDecimal premium,
            String status) {
    }

    /** OTC opcioni ugovor — preslikava {@code trading.otc.dto.OtcContractDto}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TsOtcContract(
            Long id,
            Long buyerId,
            BigDecimal strikePrice,
            String status) {
    }

    /**
     * Telo za {@code POST /otc/offers} — preslikava
     * {@code trading.otc.dto.CreateOtcOfferDto}.
     */
    public record CreateOtcOfferReq(
            Long listingId,
            Long sellerId,
            Integer quantity,
            BigDecimal pricePerStock,
            BigDecimal premium,
            LocalDate settlementDate) {
    }

    /**
     * Telo za {@code POST /otc/offers/{id}/counter} — preslikava
     * {@code trading.otc.dto.CounterOtcOfferDto}.
     */
    public record CounterOtcOfferReq(
            Integer quantity,
            BigDecimal pricePerStock,
            BigDecimal premium,
            LocalDate settlementDate) {
    }

    /* ───────────────────────── Investicioni fondovi ───────────────────────── */

    /**
     * Sazet prikaz fonda — preslikava
     * {@code trading.investmentfund.dto.InvestmentFundDtos.InvestmentFundSummaryDto}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TsFundSummary(
            Long id,
            String name,
            BigDecimal minimumContribution) {
    }

    /**
     * Detalj fonda — preslikava
     * {@code trading.investmentfund.dto.InvestmentFundDtos.InvestmentFundDetailDto}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TsFundDetail(
            Long id,
            String name) {
    }

    /**
     * Telo za {@code POST /funds} — preslikava
     * {@code InvestmentFundDtos.CreateFundDto}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateFundReq(
            String name,
            String description,
            BigDecimal minimumContribution) {
    }

    /**
     * Pozicija u fondu — preslikava
     * {@code InvestmentFundDtos.ClientFundPositionDto}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TsFundPosition(
            Long id,
            BigDecimal totalInvested,
            BigDecimal currentValue) {
    }

    /**
     * Transakcija fonda — preslikava
     * {@code InvestmentFundDtos.ClientFundTransactionDto}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TsFundTransaction(
            Long id,
            BigDecimal amountRsd,
            String status) {
    }

    /**
     * Telo za {@code POST /funds/{id}/invest} — preslikava
     * {@code InvestmentFundDtos.InvestFundDto}.
     */
    public record InvestFundReq(
            BigDecimal amount,
            String currency,
            Long sourceAccountId) {
    }

    /**
     * Telo za {@code POST /funds/{id}/withdraw} — preslikava
     * {@code InvestmentFundDtos.WithdrawFundDto}. {@code amount == null} znaci
     * "cela pozicija".
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WithdrawFundReq(
            BigDecimal amount,
            Long destinationAccountId) {
    }

    /* ─────────────────────────── Aktuari ─────────────────────────── */

    /** Aktuarski podaci — preslikava {@code trading.actuary.dto.ActuaryInfoDto}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TsActuaryInfo(
            Long employeeId,
            BigDecimal dailyLimit,
            BigDecimal usedLimit,
            boolean needApproval) {
    }

    /**
     * Telo za {@code PATCH /actuaries/{id}/limit} — preslikava
     * {@code trading.actuary.dto.UpdateActuaryLimitDto}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateActuaryLimitReq(
            BigDecimal dailyLimit,
            Boolean needApproval) {
    }

    /* ─────────────────────── Greska iz trading-service ─────────────────────── */

    /**
     * Telo error odgovora trading-service-a. Trgovinski exception handler-i
     * vracaju {@code {"message": "..."}} (npr. {@code MessageResponseDto}) ili
     * {@code {"error": "..."}}; oba se citaju.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TsError(String message, String error) {
        /** Najinformativnija poruka iz error tela, ili {@code null}. */
        public String bestMessage() {
            if (message != null && !message.isBlank()) {
                return message;
            }
            if (error != null && !error.isBlank()) {
                return error;
            }
            return null;
        }
    }
}
