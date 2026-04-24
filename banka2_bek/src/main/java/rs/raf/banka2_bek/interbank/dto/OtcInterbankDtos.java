package rs.raf.banka2_bek.interbank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/*
================================================================================
 TODO — SKUP DTO-OVA ZA OTC INTER-BANK SEGMENT
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 438-472 (OTC inter-bank pregovaranje),
                             473-519 (OTC inter-bank SAGA izvrsavanje)
--------------------------------------------------------------------------------
 DTO-ovi u ovom fajlu (svi nested static, radi grupisanja):

 1. OtcInterbankListingDto
    Lista ponudjenih akcija iz drugih banaka (discovery).
    Polje `bankCode` je dodatak u odnosu na intra-bank OtcListingDto.

 2. OtcInterbankOfferDto
    Aktivna ponuda gde je jedna strana u drugoj banci. lastModifiedBy je
    user ID + bankCode, poruke se sinhronizuju kroz InterbankProtocolService.

 3. CreateOtcInterbankOfferDto
    FE salje kad korisnik inicira ponudu za akciju iz druge banke.

 4. CounterOtcInterbankOfferDto
    FE salje kontraponudu.

 5. OtcSagaReserveSharesPayloadDto
    Banka A -> Banka B: rezervisi hartije kod prodavca.

 6. OtcSagaTransferOwnershipPayloadDto
    Banka B -> Banka A: hartije su prenete, ovo su info za upis u
    portfolio kupca na Banci A.

 Svaki DTO ima TODO sa detaljnim opisom; nedostaju samo serialization
 detalji koje ce servisi da popune.
================================================================================
*/
public final class OtcInterbankDtos {

    private OtcInterbankDtos() {
        // grupisani DTO-ovi; ne instantiraj
    }

    // ── Discovery ───────────────────────────────────────────────────────────

    /**
     * TODO — Stavka u listi javnih OTC ponuda iz drugih banaka.
     * Popunjava se iz InterbankOtcService.listRemoteDiscovery() koji
     * povlaci podatke periodicno (scheduler) ili na zahtev.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtcInterbankListingDto {
        private String bankCode;
        private String sellerPublicId; // nije obavezno da je nas userId
        private String sellerName;
        private String listingTicker;
        private String listingName;
        private String listingCurrency;
        private BigDecimal currentPrice;
        private Integer availableQuantity;
    }

    // ── Offers ──────────────────────────────────────────────────────────────

    /**
     * TODO — Ponuda koja ide preko granica banaka. Razlika u odnosu na
     * intra-bank OtcOfferDto: buyerBankCode, sellerBankCode razdvojeni;
     * `myTurn` racuna se iz waitingOnBank + waitingOnUserId.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtcInterbankOfferDto {
        private String offerId;          // UUID, isti kod obe banke
        private String listingTicker;
        private String listingName;
        private String listingCurrency;
        private BigDecimal currentPrice;

        private String buyerBankCode;
        private String buyerUserId;
        private String buyerName;

        private String sellerBankCode;
        private String sellerUserId;
        private String sellerName;

        private Integer quantity;
        private BigDecimal pricePerStock;
        private BigDecimal premium;
        private LocalDate settlementDate;

        private String waitingOnBankCode;
        private String waitingOnUserId;
        private boolean myTurn;

        private String status; // ACTIVE / ACCEPTED / DECLINED / EXPIRED
        private LocalDateTime lastModifiedAt;
        private String lastModifiedByName;
    }

    // ── Create / Counter ────────────────────────────────────────────────────

    /**
     * TODO — Klijent inicira ponudu za akciju iz druge banke (kupac smo mi,
     * prodavac u drugoj banci). BE poziva InterbankOtcService.createOffer
     * koji salje poruku partnerskoj banci.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOtcInterbankOfferDto {
        @NotBlank private String sellerBankCode;
        @NotBlank private String sellerUserId;
        @NotBlank private String listingTicker;
        @NotNull @Positive private Integer quantity;
        @NotNull @Positive private BigDecimal pricePerStock;
        @NotNull @Positive private BigDecimal premium;
        @NotNull private LocalDate settlementDate;
    }

    /**
     * TODO — Kontraponuda. Moze je slati bilo koja strana; referencira
     * postojeci offerId.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CounterOtcInterbankOfferDto {
        @NotBlank private String offerId;
        @NotNull @Positive private Integer quantity;
        @NotNull @Positive private BigDecimal pricePerStock;
        @NotNull @Positive private BigDecimal premium;
        @NotNull private LocalDate settlementDate;
    }

    // ── SAGA payloadi (inter-bank exercise) ─────────────────────────────────

    /**
     * TODO — Faza 2 SAGA: Banka A (kupac) -> Banka B (prodavac):
     * "rezervisi hartije kod prodavca".
     * Spec Celina 4 linije 487-494.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtcSagaReserveSharesPayloadDto {
        private String contractId;
        private String sellerBankCode;
        private String sellerUserId;
        private String listingTicker;
        private Integer quantity;
    }

    /**
     * TODO — Faza 4 SAGA: Banka B -> Banka A: "hartije prenete, evo
     * sertifikata da ih upises kupcu".
     * Spec Celina 4 linije 501-506.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtcSagaTransferOwnershipPayloadDto {
        private String contractId;
        private String listingTicker;
        private Integer quantity;
        private String newOwnerBankCode; // = Banka A
        private String newOwnerUserId;   // = kupac
        private BigDecimal strikePrice;  // za cost basis u portfolio kupca
    }
}
