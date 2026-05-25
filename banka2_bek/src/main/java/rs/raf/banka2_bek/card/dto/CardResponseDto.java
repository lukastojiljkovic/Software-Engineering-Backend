package rs.raf.banka2_bek.card.dto;

import lombok.Builder;
import lombok.Data;
import rs.raf.banka2_bek.card.model.CardCategory;
import rs.raf.banka2_bek.card.model.CardStatus;
import rs.raf.banka2_bek.card.model.CardType;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CardResponseDto {
    private Long id;
    private String cardNumber;
    private String cardName;
    /**
     * BE-ACC-01 (PCI-DSS): CVV se NIKAD ne vraca u response-u (uvek {@code null}).
     * Polje je zadrzano radi backwards-compat sa starim FE/Mobile klijentima
     * koji deserialize-uju DTO, ali serveri-side se uvek setuje na {@code null}
     * (svi create/get/update/top-up endpoint-i).
     */
    private String cvv;
    private CardType cardType;
    /** Kategorija: DEBIT / CREDIT / INTERNET_PREPAID. */
    private CardCategory cardCategory;
    private Long accountId;
    private String accountNumber;
    private String ownerName;
    private BigDecimal cardLimit;
    /** Za INTERNET_PREPAID: tekuci balance. Za ostale: 0. */
    private BigDecimal prepaidBalance;
    /** Za CREDIT: maksimalni iznos koji klijent moze trositi. Za ostale: 0. */
    private BigDecimal creditLimit;
    /** Za CREDIT: trenutno duguje banci. Za ostale: 0. */
    private BigDecimal outstandingBalance;
    private CardStatus status;
    private LocalDate createdAt;
    private LocalDate expirationDate;
}
