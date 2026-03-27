package rs.raf.banka2_bek.card.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.card.dto.CardResponseDto;
import rs.raf.banka2_bek.card.dto.CreateCardRequestDto;
import rs.raf.banka2_bek.card.model.Card;
import rs.raf.banka2_bek.card.model.CardStatus;
import rs.raf.banka2_bek.card.repository.CardRepository;
import rs.raf.banka2_bek.card.service.CardService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final MailNotificationService mailNotificationService;

    @Override
    @Transactional
    public CardResponseDto createCard(CreateCardRequestDto request) {
        Client client = getAuthenticatedClient();
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new RuntimeException("Racun nije pronadjen"));

        if (account.getClient() == null || !account.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Nemate pristup ovom racunu");
        }

        checkCardLimit(account);

        BigDecimal limit = request.getCardLimit() != null ? request.getCardLimit() : BigDecimal.valueOf(100000);
        return toResponse(createAndSaveCard(account, client, limit));
    }

    @Override
    @Transactional
    public CardResponseDto createCardForAccount(Long accountId, Long clientId, BigDecimal limit) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Racun nije pronadjen"));
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Klijent nije pronadjen"));

        checkCardLimit(account);

        BigDecimal cardLimit = limit != null ? limit : BigDecimal.valueOf(100000);
        return toResponse(createAndSaveCard(account, client, cardLimit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CardResponseDto> getMyCards() {
        Client client = getOptionalClient();
        if (client == null) return Collections.emptyList();
        return cardRepository.findByClientId(client.getId()).stream()
                .map(this::toMaskedResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CardResponseDto> getCardsByAccount(Long accountId) {
        return cardRepository.findByAccountId(accountId).stream()
                .map(this::toMaskedResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CardResponseDto blockCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Kartica nije pronadjena"));

        if (card.getStatus() == CardStatus.DEACTIVATED) {
            throw new RuntimeException("Deaktivirana kartica se ne moze blokirati");
        }
        card.setStatus(CardStatus.BLOCKED);
        CardResponseDto response = toMaskedResponse(cardRepository.save(card));

        try {
            String last4 = card.getCardNumber().substring(card.getCardNumber().length() - 4);
            mailNotificationService.sendCardBlockedMail(
                    card.getClient().getEmail(), last4, LocalDate.now());
        } catch (Exception e) {
            // Email failure must not roll back the card operation
        }

        return response;
    }

    @Override
    @Transactional
    public CardResponseDto unblockCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Kartica nije pronadjena"));

        if (card.getStatus() != CardStatus.BLOCKED) {
            throw new RuntimeException("Samo blokirana kartica se moze deblokirati");
        }
        card.setStatus(CardStatus.ACTIVE);
        CardResponseDto response = toMaskedResponse(cardRepository.save(card));

        try {
            String last4 = card.getCardNumber().substring(card.getCardNumber().length() - 4);
            mailNotificationService.sendCardUnblockedMail(
                    card.getClient().getEmail(), last4);
        } catch (Exception e) {
            // Email failure must not roll back the card operation
        }

        return response;
    }

    @Override
    @Transactional
    public CardResponseDto deactivateCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Kartica nije pronadjena"));

        card.setStatus(CardStatus.DEACTIVATED);
        return toMaskedResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponseDto updateCardLimit(Long cardId, BigDecimal newLimit) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Kartica nije pronadjena"));

        if (card.getStatus() == CardStatus.DEACTIVATED) {
            throw new RuntimeException("Ne moze se menjati limit deaktivirane kartice");
        }
        card.setCardLimit(newLimit);
        return toMaskedResponse(cardRepository.save(card));
    }

    // --- helpers ---

    private void checkCardLimit(Account account) {
        long activeCards = cardRepository.countByAccountIdAndStatusNot(account.getId(), CardStatus.DEACTIVATED);
        boolean isBusiness = account.getAccountType() == AccountType.BUSINESS;
        long maxCards = isBusiness ? 1 : 2;
        if (activeCards >= maxCards) {
            throw new RuntimeException("Dostignut maksimalan broj kartica za ovaj racun (" + maxCards + ")");
        }
    }

    private Card createAndSaveCard(Account account, Client client, BigDecimal limit) {
        String cardNumber;
        do {
            cardNumber = Card.generateCardNumber();
        } while (cardRepository.findByCardNumber(cardNumber).isPresent());

        Card card = Card.builder()
                .cardNumber(cardNumber)
                .cardName("Visa Debit")
                .cvv(Card.generateCvv())
                .account(account)
                .client(client)
                .cardLimit(limit)
                .status(CardStatus.ACTIVE)
                .createdAt(LocalDate.now())
                .expirationDate(LocalDate.now().plusYears(4))
                .build();

        return cardRepository.save(card);
    }

    private CardResponseDto toResponse(Card card) {
        return CardResponseDto.builder()
                .id(card.getId())
                .cardNumber(card.getCardNumber())
                .cardName(card.getCardName())
                .cvv(card.getCvv())
                .accountNumber(card.getAccount().getAccountNumber())
                .ownerName(card.getClient().getFirstName() + " " + card.getClient().getLastName())
                .cardLimit(card.getCardLimit())
                .status(card.getStatus())
                .createdAt(card.getCreatedAt())
                .expirationDate(card.getExpirationDate())
                .build();
    }

    private CardResponseDto toMaskedResponse(Card card) {
        return CardResponseDto.builder()
                .id(card.getId())
                .cardNumber(maskCardNumber(card.getCardNumber()))
                .cardName(card.getCardName())
                .cvv(null)
                .accountNumber(card.getAccount().getAccountNumber())
                .ownerName(card.getClient().getFirstName() + " " + card.getClient().getLastName())
                .cardLimit(card.getCardLimit())
                .status(card.getStatus())
                .createdAt(card.getCreatedAt())
                .expirationDate(card.getExpirationDate())
                .build();
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) return cardNumber;
        String digits = cardNumber.replaceAll("\\s+", "");
        String first4 = digits.substring(0, 4);
        String last4 = digits.substring(digits.length() - 4);
        return first4 + " **** **** " + last4;
    }

    private Client getAuthenticatedClient() {
        Client client = getOptionalClient();
        if (client == null) throw new RuntimeException("Klijent nije pronadjen");
        return client;
    }

    private Client getOptionalClient() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        String email;
        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = principal.toString();
        }
        return clientRepository.findByEmail(email).orElse(null);
    }
}
