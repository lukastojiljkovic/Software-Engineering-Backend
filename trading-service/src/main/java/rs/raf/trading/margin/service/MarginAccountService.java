package rs.raf.trading.margin.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2.contracts.internal.DebitFundsRequest;
import rs.raf.banka2.contracts.internal.InternalAccountDto;
import rs.raf.banka2.contracts.internal.InternalUserDto;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.client.BankaCoreClientException;
import rs.raf.trading.common.UserContext;
import rs.raf.trading.common.UserRole;
import rs.raf.trading.margin.dto.CreateMarginAccountDto;
import rs.raf.trading.margin.dto.MarginAccountCheckDto;
import rs.raf.trading.margin.dto.MarginAccountDto;
import rs.raf.trading.margin.dto.MarginTransactionDto;
import rs.raf.trading.margin.event.MarginAccountBlockedEvent;
import rs.raf.trading.margin.model.MarginAccount;
import rs.raf.trading.margin.model.MarginAccountStatus;
import rs.raf.trading.margin.model.MarginTransaction;
import rs.raf.trading.margin.model.MarginTransactionType;
import rs.raf.trading.margin.repository.MarginAccountRepository;
import rs.raf.trading.margin.repository.MarginTransactionRepository;
import rs.raf.trading.security.TradingUserResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Servis za upravljanje margin racunima.
 * <p>
 * Specifikacija: Celina 3 - Margin racuni
 * <p>
 * Kljucne formule:
 * initialMargin     = deposit / (1 - bankParticipation)
 * loanValue          = initialMargin - deposit
 * maintenanceMargin  = initialMargin * 0.5  (za akcije)
 * <p>
 * Margin call: ako initialMargin padne ispod maintenanceMargin, racun se blokira.
 *
 * <p><b>NAPOMENA (copy-first ekstrakcija, faza 2d-D — money-seam rewiring):</b>
 * monolitna verzija je direktno menjala {@code Account.balance} /
 * {@code Account.availableBalance} baznog racuna preko {@code AccountRepository}
 * i razresavala identitet klijenta preko {@code ClientRepository}. U
 * trading-service-u racuni i klijenti zive u banka-core domenu, pa:
 * <ul>
 *   <li><b>{@code createForUser}</b> — bazni racun se cita preko
 *       {@link BankaCoreClient#getAccount} (provera vlasnistva/statusa/balansa),
 *       a debit baznog racuna ide kroz {@code POST /internal/funds/debit}
 *       ({@link BankaCoreClient#debitFunds}; banka-core 409 → faithful
 *       {@code IllegalArgumentException} "Insufficient available balance ...").
 *       Idempotency kljuc je deterministicki {@code "margin-create-" + accountId}
 *       — jedan margin racun po baznom racunu, pa retry koji je rollback-ovao
 *       re-koristi isti kljuc i banka-core replay-uje umesto dupliranja debita.</li>
 *   <li><b>{@code deposit}/{@code withdraw}/{@code checkMaintenanceMargin}</b> —
 *       mutiraju samo {@code margin}-owned tabele ({@link MarginAccount},
 *       {@link MarginTransaction}); novcane noge nema, kopirani verbatim.</li>
 *   <li>Identitet ide kroz {@link TradingUserResolver#resolveCurrent()} umesto
 *       {@code clientRepository.findByEmail(authentication.getName())}.</li>
 *   <li>{@code checkMaintenanceMargin} email vlasnika za
 *       {@link MarginAccountBlockedEvent} razresava per blokirani racun preko
 *       {@link BankaCoreClient#getUserById} (native query vise ne JOIN-uje
 *       {@code clients}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarginAccountService {

    private final MarginAccountRepository marginAccountRepository;
    private final MarginTransactionRepository marginTransactionRepository;
    private final BankaCoreClient bankaCoreClient;
    private final TradingUserResolver userResolver;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Podrazumevani procenat ucestva banke (50%)
     */
    private static final BigDecimal DEFAULT_BANK_PARTICIPATION = new BigDecimal("0.50");

    /**
     * Faktor za izracunavanje maintenance margine (50% od initial za akcije)
     */
    private static final BigDecimal MAINTENANCE_FACTOR = new BigDecimal("0.50");

    /**
     * Kreira novi margin racun za autentifikovanog korisnika (klijenta).
     *
     * @param dto DTO sa accountId i initialDeposit
     * @return kreiran MarginAccountDto
     */
    @Transactional
    public MarginAccountDto createForUser(CreateMarginAccountDto dto) {
        Long userId = currentUserId();
        if (dto == null || dto.getAccountId() == null || dto.getInitialDeposit() == null) {
            throw new IllegalArgumentException("Account id and initial deposit are required.");
        }

        BigDecimal initialDeposit = dto.getInitialDeposit();
        if (initialDeposit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial deposit must be greater than zero.");
        }

        // Bazni racun se cita preko banka-core (monolit: accountRepository.findForUpdateById).
        InternalAccountDto account;
        try {
            account = bankaCoreClient.getAccount(dto.getAccountId());
        } catch (BankaCoreClientException ex) {
            throw new IllegalArgumentException("Account not found.");
        }

        if (account.ownerClientId() == null || !userId.equals(account.ownerClientId())) {
            throw new IllegalStateException("You are not allowed to create a margin account for this base account.");
        }
        if (!"ACTIVE".equalsIgnoreCase(account.status())) {
            throw new IllegalArgumentException("Base account must be active.");
        }
        if (!marginAccountRepository.findByAccountId(account.id()).isEmpty()) {
            throw new IllegalArgumentException("Margin account already exists for this base account.");
        }

        // Pre-check: stvarnu garanciju daje debitFunds (banka-core 409).
        BigDecimal availableBalance = account.availableBalance() == null
                ? BigDecimal.ZERO
                : account.availableBalance();
        if (availableBalance.compareTo(initialDeposit) < 0) {
            throw new IllegalArgumentException("Insufficient available balance for initial margin deposit.");
        }

        BigDecimal divisor = BigDecimal.ONE.subtract(DEFAULT_BANK_PARTICIPATION);
        BigDecimal initialMargin = initialDeposit.divide(divisor, 4, RoundingMode.HALF_UP);
        BigDecimal loanValue = initialMargin.subtract(initialDeposit).setScale(4, RoundingMode.HALF_UP);
        BigDecimal maintenanceMargin = initialMargin.multiply(MAINTENANCE_FACTOR).setScale(4, RoundingMode.HALF_UP);

        // Debit baznog racuna za pocetni margin depozit — monolit je direktno
        // menjao Account.balance/availableBalance; sad to radi banka-core.
        // Idempotency: jedan margin racun po baznom racunu (servis odbija duplikat),
        // pa je deterministicki kljuc retry-safe (rollback-ovan create re-koristi
        // isti kljuc → banka-core replay umesto dvostrukog skidanja).
        try {
            bankaCoreClient.debitFunds(
                    "margin-create-" + dto.getAccountId(),
                    new DebitFundsRequest(dto.getAccountId(), initialDeposit, BigDecimal.ZERO,
                            account.currencyCode(), "Initial margin deposit"));
        } catch (BankaCoreClientException ex) {
            if (ex.getHttpStatus() == 409) {
                throw new IllegalArgumentException("Insufficient available balance for initial margin deposit.");
            }
            throw ex;
        }

        MarginAccount savedMarginAccount = marginAccountRepository.save(
                MarginAccount.builder()
                        .accountId(account.id())
                        .accountNumber(account.accountNumber())
                        .userId(userId)
                        .initialMargin(initialMargin)
                        .loanValue(loanValue)
                        .maintenanceMargin(maintenanceMargin)
                        .bankParticipation(DEFAULT_BANK_PARTICIPATION)
                        .status(MarginAccountStatus.ACTIVE)
                        .build()
        );

        marginTransactionRepository.save(
                MarginTransaction.builder()
                        .marginAccount(savedMarginAccount)
                        .type(MarginTransactionType.DEPOSIT)
                        .amount(initialDeposit.setScale(4, RoundingMode.HALF_UP))
                        .description("Initial margin deposit")
                        .build()
        );

        log.info("Created margin account {} for user {} on base account {}",
                savedMarginAccount.getId(), userId, account.id());

        return toDto(savedMarginAccount);
    }

    /**
     * Vraca sve margin racune za autentifikovanog korisnika.
     *
     * @return lista margin racuna
     */
    public List<MarginAccountDto> getMyMarginAccounts() {
        Long clientId = currentUserId();

        List<MarginAccountDto> accounts = marginAccountRepository.findByUserId(clientId)
                .stream()
                .map(this::toDto)
                .toList();

        log.info("Fetched {} margin accounts for client {}", accounts.size(), clientId);
        return accounts;
    }

    /**
     * Uplata sredstava na margin racun.
     *
     * @param marginAccountId ID margin racuna
     * @param amount          iznos za uplatu
     */
    @Transactional
    public void deposit(Long marginAccountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 1)
            throw new IllegalArgumentException("Amount must be a positive number.");

        Long clientId = currentUserId();

        // 1. find MarginAccount by id
        MarginAccount account = marginAccountRepository.findById(marginAccountId)
                .orElseThrow(
                        () -> new EntityNotFoundException("Account with id: " + marginAccountId + " not found.")
                );

        // OWNERSHIP CHECK
        if (!clientId.equals(account.getUserId()))
            throw new IllegalStateException("Only the owner of margin account with id = " + marginAccountId + " can deposit funds.");


        // 2. increase initialMargin for the amount
        account.setInitialMargin(account.getInitialMargin().add(amount));

        // 3. set new maintenanceMargin = initialMargin * MAINTENANCE_FACTOR
        account.setMaintenanceMargin(account.getInitialMargin().multiply(MAINTENANCE_FACTOR));

        // 4. if account could be unblocked -> activate it
        boolean isBlocked = account.getStatus().equals(MarginAccountStatus.BLOCKED);
        if (isBlocked) account.setStatus(MarginAccountStatus.ACTIVE);

        // 5. save marginAccount
        marginAccountRepository.save(account);

        String transactionDescription =
                "Executed transaction. Amount deposited: " + amount + ". Current balance: " + account.getInitialMargin() + ".";

        // 6. create Transaction (type = DEPOSIT)
        MarginTransaction transaction = MarginTransaction.builder()
                .marginAccount(account)
                .type(MarginTransactionType.DEPOSIT)
                .amount(amount)
                .description(transactionDescription)
                .build();

        // 7. save Transaction
        marginTransactionRepository.save(transaction);

        log.info("Deposit {} to margin account {}", amount, marginAccountId);
    }

    /**
     * Isplata sredstava sa margin racuna.
     *
     * @param marginAccountId ID margin racuna
     * @param amount          iznos za isplatu
     */
    @Transactional
    public void withdraw(Long marginAccountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 1)
            throw new IllegalArgumentException("Amount must be a positive number.");

        Long clientId = currentUserId();

        // 1. find MarginAccount by marginAccountId, if it doesn't exist exception is thrown
        MarginAccount account = marginAccountRepository.findById(marginAccountId).orElseThrow(
                () -> new EntityNotFoundException("Account with id: " + marginAccountId + " not found.")
        );

        // CHECK ACCOUNT OWNERSHIP
        if (!clientId.equals(account.getUserId()))
            throw new IllegalStateException("Only the owner of margin account with id = " + marginAccountId + " can withdraw funds.");

        // 2. not active accounts can't do withdraw
        if (!account.getStatus().equals(MarginAccountStatus.ACTIVE))
            throw new IllegalStateException("Account with id: " + marginAccountId + " is not active.");

        // 3. is initial_margin - amount < maintenance_margin  <==>  initialMargin - amount >= maintenanceMargin
        boolean withdrawalBelowMaintenance =
                account.getInitialMargin().subtract(amount).compareTo(account.getMaintenanceMargin()) < 0;

        // if dropped below maintenance
        if (withdrawalBelowMaintenance)
            throw new IllegalArgumentException(
                    "Funds in the account cannot be below " + account.getMaintenanceMargin() + " amount."
            );

        // 4. update initialMargin = initialMargin - amount

        account.setInitialMargin(account.getInitialMargin().subtract(amount));

        account.setMaintenanceMargin(account.getInitialMargin().multiply(MAINTENANCE_FACTOR));

        // 5. save margin account
        marginAccountRepository.save(account);

        // 6. create new Transaction (type = WITHDRAWAL)
        String description = "Executed transaction. Amount withdrawn: " + amount + ". Current balance: " + account.getInitialMargin() + ".";

        MarginTransaction transaction = MarginTransaction.builder()
                .marginAccount(account)
                .type(MarginTransactionType.WITHDRAWAL)
                .amount(amount)
                .description(description)
                .build();

        // 7. save margin transaction
        marginTransactionRepository.save(transaction);

        log.info("Withdraw {} from margin account {}", amount, marginAccountId);
    }

    /**
     * Dnevna provera maintenance margine za sve aktivne margin racune.
     * Pokrece se automatski svaki dan u ponoc.
     *
     * <p>NAPOMENA (copy-first ekstrakcija, faza 2d-D): {@code @Scheduled} anotacija
     * je zadrzana verbatim, ali je u trading-service-u OVAJ scheduler USPAVAN —
     * {@code TradingServiceApplication} namerno nema {@code @EnableScheduling} do
     * cutover-a (Faza 2f). Bean se registruje ali se {@code checkMaintenanceMargin}
     * ne okida automatski; rucni poziv (npr. iz testa) i dalje radi.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkMaintenanceMargin() {

        log.info("Running daily maintenance margin check...");

        // get all about to be blocked accounts
        List<MarginAccountCheckDto> accountsForBlocking =
                marginAccountRepository.findAccountsForMarginCheck(MarginAccountStatus.ACTIVE.toString());

        marginAccountRepository.blockAccountsWhereMaintenanceExceedsInitial(
                MarginAccountStatus.ACTIVE.toString(),
                MarginAccountStatus.BLOCKED.toString()
        );

        for (MarginAccountCheckDto account : accountsForBlocking) {

            // Native query vise ne JOIN-uje clients tabelu (baza-po-servisu) —
            // email vlasnika se razresava ovde preko banka-core internog API-ja.
            String ownerEmail = resolveOwnerEmail(account.ownerUserId());

            // for mail sending logic listen for MarginAccountBlockedEvent publish
            eventPublisher.publishEvent(
                    new MarginAccountBlockedEvent(
                            ownerEmail,
                            account.maintenanceMargin().toString(),
                            account.initialMargin().toString(),
                            account.calculateMaintenanceDeficit().toString()
                    )
            );

            log.warn(
                    "MARGIN CALL: Account {} blocked. initialMargin={}, maintenanceMargin={}",
                    account.marginAccountId(),
                    account.initialMargin(),
                    account.maintenanceMargin()
            );
        }

        log.info("Daily maintenance margin check completed. Amount of blocked accounts : {}.", accountsForBlocking.size());

    }

    /**
     * Vraca istoriju transakcija za dati margin racun.
     *
     * @param marginAccountId ID margin racuna
     * @return lista transakcija sortirana od najnovije
     */
    public List<MarginTransactionDto> getTransactions(Long marginAccountId) {
        Long clientId = currentUserId();

        MarginAccount marginAccount = marginAccountRepository.findById(marginAccountId).orElseThrow(
                () -> new EntityNotFoundException("Margin account with id: " + marginAccountId + " does not exist.")
        );

        // CHECK ACCOUNT OWNERSHIP
        if (!marginAccount.getUserId().equals(clientId))
            throw new IllegalStateException("Only the owner of the margin account with id = " + marginAccountId + " can access margin account transactions.");

        return marginTransactionRepository.findByMarginAccountIdOrderByCreatedAtDesc(marginAccountId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ── Helper metode ───────────────────────────────────────────────────────────

    /**
     * Razresava numericki id autentifikovanog korisnika preko banka-core seam-a
     * i potvrdjuje da je klijent (margin racune mogu samo klijenti).
     *
     * <p>Verno monolitovom {@code clientRepository.findByEmail(...).getId()} —
     * ne-klijenti i neautentifikovani zahtevi dobijaju 403 (IllegalStateException).
     */
    private Long currentUserId() {
        UserContext me = userResolver.resolveCurrent();
        if (!UserRole.CLIENT.equals(me.userRole())) {
            throw new IllegalStateException("Only clients can manage margin accounts.");
        }
        return me.userId();
    }

    /**
     * Razresava email vlasnika margin racuna (klijenta) preko banka-core.
     * Rezilijentno — ako banka-core ne moze da razresi, vraca {@code null}
     * (event se i dalje publish-uje; notifikacioni listener handluje null).
     */
    private String resolveOwnerEmail(Long clientId) {
        if (clientId == null) {
            return null;
        }
        try {
            InternalUserDto user = bankaCoreClient.getUserById(UserRole.CLIENT, clientId);
            return user.email();
        } catch (RuntimeException ex) {
            log.warn("Margin call: nije moguce razresiti email vlasnika klijenta {}: {}",
                    clientId, ex.getMessage());
            return null;
        }
    }

    /**
     * Mapira MarginAccount entitet u MarginAccountDto.
     *
     * <p>NAPOMENA (faza 2d-D): cita soft {@code accountId} + denormalizovan
     * {@code accountNumber} umesto da dereferira uklonjenu {@code Account} vezu.
     */
    private MarginAccountDto toDto(MarginAccount marginAccount) {
        return MarginAccountDto.builder()
                .id(marginAccount.getId())
                .accountId(marginAccount.getAccountId())
                .accountNumber(marginAccount.getAccountNumber())
                .userId(marginAccount.getUserId())
                .initialMargin(marginAccount.getInitialMargin())
                .loanValue(marginAccount.getLoanValue())
                .maintenanceMargin(marginAccount.getMaintenanceMargin())
                .bankParticipation(marginAccount.getBankParticipation())
                .status(marginAccount.getStatus() != null ? marginAccount.getStatus().name() : null)
                .createdAt(marginAccount.getCreatedAt())
                .build();
    }

    private MarginTransactionDto toDto(MarginTransaction transaction) {
        return MarginTransactionDto.builder()
                .id(transaction.getId())
                .marginAccountId(transaction.getMarginAccount() != null ? transaction.getMarginAccount().getId() : null)
                .type(transaction.getType() != null ? transaction.getType().name() : null)
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }


}
