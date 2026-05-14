package rs.raf.banka2_bek.card.service;

import rs.raf.banka2_bek.card.dto.CardResponseDto;
import rs.raf.banka2_bek.card.dto.CreateCardRequestDto;
import rs.raf.banka2_bek.card.model.CardCategory;
import rs.raf.banka2_bek.card.model.CardType;

import java.math.BigDecimal;
import java.util.List;

public interface CardService {
    CardResponseDto createCard(CreateCardRequestDto request);
    CardResponseDto createCardForAccount(Long accountId, Long clientId, BigDecimal limit, CardType cardType);
    /** Verzija koja prosledjuje kategoriju + kreditni limit (za approve flow-a). */
    CardResponseDto createCardForAccount(Long accountId, Long clientId, BigDecimal limit, CardType cardType,
                                          CardCategory cardCategory, BigDecimal creditLimit);
    List<CardResponseDto> getMyCards();
    List<CardResponseDto> getCardsByAccount(Long accountId);
    CardResponseDto blockCard(Long cardId);
    CardResponseDto unblockCard(Long cardId);
    CardResponseDto deactivateCard(Long cardId);
    CardResponseDto updateCardLimit(Long cardId, BigDecimal newLimit);

    /**
     * Dopuna INTERNET_PREPAID kartice — skida {@code amount} sa {@code sourceAccountId}
     * i povecava {@code Card.prepaidBalance}. Obe operacije u jednoj transakciji.
     * @throws IllegalStateException ako kartica nije INTERNET_PREPAID kategorije
     * @throws IllegalArgumentException ako iznos nije pozitivan ili nema dovoljno na racunu
     */
    CardResponseDto topUpPrepaidCard(Long cardId, Long sourceAccountId, BigDecimal amount);

    /**
     * Povlacenje sredstava sa INTERNET_PREPAID kartice nazad na racun — obrnut smer od
     * top-up-a. Skida {@code amount} sa {@code Card.prepaidBalance} i dodaje na
     * {@code targetAccountId}. Atomicno u jednoj transakciji.
     * @throws IllegalStateException ako kartica nije INTERNET_PREPAID kategorije
     * @throws IllegalArgumentException ako iznos nije pozitivan ili nema dovoljno na kartici
     */
    CardResponseDto withdrawFromPrepaidCard(Long cardId, Long targetAccountId, BigDecimal amount);
}
