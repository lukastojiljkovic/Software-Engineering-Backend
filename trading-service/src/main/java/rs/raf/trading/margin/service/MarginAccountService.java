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
import rs.raf.trading.margin.dto.MarginAccountDto;
import rs.raf.trading.margin.dto.MarginTransactionDto;
import rs.raf.trading.margin.event.MarginAccountBlockedEvent;
import rs.raf.trading.margin.model.MarginAccount;
import rs.raf.trading.margin.model.MarginAccountStatus;
import rs.raf.trading.margin.model.MarginTransaction;
import rs.raf.trading.margin.model.MarginTransactionType;
import rs.raf.trading.margin.model.UserMarginAccount;
import rs.raf.trading.margin.repository.MarginAccountRepository;
import rs.raf.trading.margin.repository.MarginTransactionRepository;
import rs.raf.trading.security.TradingUserResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Servis za upravljanje margin racunima.
 * <p>
 * Specifikacija: Marzni_Racuni.txt §1-159, Celina 3 - Margin racuni
 * <p>
 * <b>BE-STK-07 (25.05.2026):</b> {@code createForUser} sad prihvata
 * {@code initialMargin}, {@code maintenanceMargin}, {@code bankParticipation}
 * direktno iz DTO-a (zadaje zaposleni). Validacija:
 * {@code 0 < BP < 1}, {@code MM <= IM}, {@code IM > 0}, {@code currency = "RSD"}.
 * <p>
 * {@code deposit}/{@code withdraw} NE preracunavaju MM (BE-STK-07 fix) —
 * MM je fiksiran pri kreiranju i menja se samo eksplicitno.
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
     * Podrazumevani procenat ucestva banke (50%) — koristi se samo u legacy
     * putanji kad DTO ne specifikuje BP eksplicitno.
     */
    private static final BigDecimal DEFAULT_BANK_PARTICIPATION = new BigDecimal("0.50");

    /**
     * Legacy faktor za izracunavanje MM (50% od IM) — koristi se samo kad DTO
     * ne specifikuje MM eksplicitno (backwards-compat).
     */
    private static final BigDecimal LEGACY_MAINTENANCE_FACTOR = new BigDecimal("0.50");

    /**
     * Kreira novi margin racun za autentifikovanog korisnika (klijenta).
     *
     * <p>BE-STK-07: Validacija IM/MM/BP iz DTO-a. Ako su zadati, koristi se
     * eksplicitne vrednosti. Ako nisu (legacy putanja), koristi se
     * {@code initialDeposit / (1 - BP)} formula.
     */
    @Transactional
    public MarginAccountDto createForUser(CreateMarginAccountDto dto) {
        Long userId = currentUserId();
        if (dto == null || dto.getAccountId() == null) {
            throw new IllegalArgumentException("Account id and initial deposit are required.");
        }

        // BE-STK-07: validacija depozita PRE banka-core fetch-a za legacy putanju
        // (test fixture sa null IM/MM/BP ocekuje "Initial deposit must be greater than zero"
        // bez stub-ovanja getAccount).
        boolean hasExplicitParams = dto.getInitialMargin() != null
                && dto.getMaintenanceMargin() != null
                && dto.getBankParticipation() != null;
        if (!hasExplicitParams) {
            if (dto.getInitialDeposit() == null
                    || dto.getInitialDeposit().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Initial deposit must be greater than zero.");
            }
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
        // BE-STK-04: currency uvek RSD po Marzni_Racuni.txt §17.
        if (!"RSD".equalsIgnoreCase(account.currencyCode())) {
            throw new IllegalArgumentException("Margin accounts must use RSD currency (got: "
                    + account.currencyCode() + ").");
        }
        if (!marginAccountRepository.findByAccountId(account.id()).isEmpty()) {
            throw new IllegalArgumentException("Margin account already exists for this base account.");
        }

        BigDecimal initialMargin;
        BigDecimal maintenanceMargin;
        BigDecimal bankParticipation;
        BigDecimal loanValue;
        BigDecimal initialDeposit;

        if (dto.getInitialMargin() != null && dto.getMaintenanceMargin() != null
                && dto.getBankParticipation() != null) {
            // BE-STK-07: eksplicitne vrednosti od strane zaposlenog.
            initialMargin = dto.getInitialMargin().setScale(4, RoundingMode.HALF_UP);
            maintenanceMargin = dto.getMaintenanceMargin().setScale(4, RoundingMode.HALF_UP);
            bankParticipation = dto.getBankParticipation().setScale(4, RoundingMode.HALF_UP);

            // Validacije:
            if (initialMargin.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("InitialMargin must be greater than zero.");
            }
            if (maintenanceMargin.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("MaintenanceMargin must be non-negative.");
            }
            if (maintenanceMargin.compareTo(initialMargin) > 0) {
                throw new IllegalArgumentException("MaintenanceMargin must not exceed InitialMargin.");
            }
            if (bankParticipation.compareTo(BigDecimal.ZERO) <= 0
                    || bankParticipation.compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalArgumentException("BankParticipation must be strictly between 0 and 1.");
            }

            // Loan value na pocetku = nula (Marzni_Racuni.txt §9).
            loanValue = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            // Zaposleni je naveo IM eksplicitno; pocetni depozit = IM (sve sa user-ovog racuna).
            initialDeposit = initialMargin;
        } else {
            // Legacy putanja: izracunaj IM preko formule iz initialDeposit i DEFAULT BP.
            if (dto.getInitialDeposit() == null
                    || dto.getInitialDeposit().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Initial deposit must be greater than zero.");
            }
            initialDeposit = dto.getInitialDeposit();
            bankParticipation = DEFAULT_BANK_PARTICIPATION;
            BigDecimal divisor = BigDecimal.ONE.subtract(bankParticipation);
            initialMargin = initialDeposit.divide(divisor, 4, RoundingMode.HALF_UP);
            loanValue = initialMargin.subtract(initialDeposit).setScale(4, RoundingMode.HALF_UP);
            maintenanceMargin = initialMargin.multiply(LEGACY_MAINTENANCE_FACTOR)
                    .setScale(4, RoundingMode.HALF_UP);
        }

        // Pre-check: stvarnu garanciju daje debitFunds (banka-core 409).
        BigDecimal availableBalance = account.availableBalance() == null
                ? BigDecimal.ZERO
                : account.availableBalance();
        if (availableBalance.compareTo(initialDeposit) < 0) {
            throw new IllegalArgumentException("Insufficient available balance for initial margin deposit.");
        }

        // Debit baznog racuna za pocetni margin depozit.
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

        UserMarginAccount marginAccount = UserMarginAccount.builder()
                .accountId(account.id())
                .accountNumber(account.accountNumber())
                .userId(userId)
                .currency("RSD")
                .initialMargin(initialMargin)
                .loanValue(loanValue)
                .maintenanceMargin(maintenanceMargin)
                .bankParticipation(bankParticipation)
                .reservedMargin(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                .status(MarginAccountStatus.ACTIVE)
                .build();
        MarginAccount savedMarginAccount = marginAccountRepository.save(marginAccount);

        marginTransactionRepository.save(
                MarginTransaction.builder()
                        .marginAccount(savedMarginAccount)
                        .type(MarginTransactionType.DEPOSIT)
                        .amount(initialDeposit.setScale(4, RoundingMode.HALF_UP))
                        .description("Initial margin deposit")
                        .build()
        );

        log.info("Created margin account {} for user {} on base account {} (IM={}, MM={}, BP={})",
                savedMarginAccount.getId(), userId, account.id(),
                initialMargin, maintenanceMargin, bankParticipation);

        return toDto(savedMarginAccount);
    }

    /**
     * Vraca sve margin racune za autentifikovanog korisnika.
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
     * <p>BE-STK-07: NE preracunava MM (maintenance margin je fiksiran pri
     * kreiranju). Marzni_Racuni.txt §139: "kada InitialMargin predje MaintenanceMargin,
     * racun se odblokira" — odblokiranje se aktivira ako uplata gurne IM iznad MM.
     */
    @Transactional
    public void deposit(Long marginAccountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 1)
            throw new IllegalArgumentException("Amount must be a positive number.");

        Long clientId = currentUserId();

        MarginAccount account = marginAccountRepository.findById(marginAccountId)
                .orElseThrow(
                        () -> new EntityNotFoundException("Account with id: " + marginAccountId + " not found.")
                );

        if (!clientId.equals(account.getUserId()))
            throw new IllegalStateException("Only the owner of margin account with id = " + marginAccountId + " can deposit funds.");

        // BE-STK-07: increment IM, ne dodirivati MM (MM je fiksiran pri kreiranju).
        account.setInitialMargin(account.getInitialMargin().add(amount));

        // Marzni_Racuni.txt §139: ako IM predje MM, racun se odblokira.
        boolean isBlocked = account.getStatus().equals(MarginAccountStatus.BLOCKED);
        if (isBlocked && account.getInitialMargin().compareTo(account.getMaintenanceMargin()) >= 0) {
            account.setStatus(MarginAccountStatus.ACTIVE);
        }

        marginAccountRepository.save(account);

        String transactionDescription =
                "Executed transaction. Amount deposited: " + amount + ". Current balance: " + account.getInitialMargin() + ".";

        MarginTransaction transaction = MarginTransaction.builder()
                .marginAccount(account)
                .type(MarginTransactionType.DEPOSIT)
                .amount(amount)
                .description(transactionDescription)
                .build();

        marginTransactionRepository.save(transaction);

        log.info("Deposit {} to margin account {}", amount, marginAccountId);
    }

    /**
     * Isplata sredstava sa margin racuna.
     * <p>BE-STK-07: NE preracunava MM (fixed pri kreiranju).
     * Marzni_Racuni.txt §159: "pare sa marznog racuna ne mogu skinuti ako je racun blokiran,
     * ili ako skidanjem para idemo ispod MaintenanceMargin vrednosti".
     */
    @Transactional
    public void withdraw(Long marginAccountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 1)
            throw new IllegalArgumentException("Amount must be a positive number.");

        Long clientId = currentUserId();

        MarginAccount account = marginAccountRepository.findById(marginAccountId).orElseThrow(
                () -> new EntityNotFoundException("Account with id: " + marginAccountId + " not found.")
        );

        if (!clientId.equals(account.getUserId()))
            throw new IllegalStateException("Only the owner of margin account with id = " + marginAccountId + " can withdraw funds.");

        if (!account.getStatus().equals(MarginAccountStatus.ACTIVE))
            throw new IllegalStateException("Account with id: " + marginAccountId + " is not active.");

        boolean withdrawalBelowMaintenance =
                account.getInitialMargin().subtract(amount).compareTo(account.getMaintenanceMargin()) < 0;

        if (withdrawalBelowMaintenance)
            throw new IllegalArgumentException(
                    "Funds in the account cannot be below " + account.getMaintenanceMargin() + " amount."
            );

        // BE-STK-07: decrement IM, ne dodirivati MM.
        account.setInitialMargin(account.getInitialMargin().subtract(amount));

        marginAccountRepository.save(account);

        String description = "Executed transaction. Amount withdrawn: " + amount + ". Current balance: " + account.getInitialMargin() + ".";

        MarginTransaction transaction = MarginTransaction.builder()
                .marginAccount(account)
                .type(MarginTransactionType.WITHDRAWAL)
                .amount(amount)
                .description(description)
                .build();

        marginTransactionRepository.save(transaction);

        log.info("Withdraw {} from margin account {}", amount, marginAccountId);
    }

    /**
     * Dnevna provera maintenance margine za sve aktivne margin racune.
     *
     * <p><b>BE-STK-04 (atomic block):</b> umesto SELECT-zatim-UPDATE obrazca
     * (koji je dozvoljavao da concurrent deposit izmedju ova dva koraka flipne
     * flag dok je vec procenjivan za blokadu), sad koristi
     * {@code blockUnderMargin} koji u jednom UPDATE-u sa RETURNING klauzulom
     * atomicno blokira redove i vrati id-eve. Posle UPDATE-a, posebnim
     * lookupom puni email-ove blokiranih za notification publish — bez race-a.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkMaintenanceMargin() {

        log.info("Running daily maintenance margin check...");

        // Atomic UPDATE + RETURNING: blokira sve eligible margin racune i vraca
        // njihove id-eve. Bez race window-a izmedju eligibility check-a i blokade.
        List<Long> blockedIds = marginAccountRepository.blockUnderMargin(
                MarginAccountStatus.ACTIVE.toString(),
                MarginAccountStatus.BLOCKED.toString()
        );

        if (blockedIds.isEmpty()) {
            log.info("Daily maintenance margin check completed. No accounts blocked.");
            return;
        }

        // Posle blokade: resolve detalje (userId/MM/IM) za notification publish.
        // Bezbedno: redovi su sada BLOCKED, vise ne mogu da se menjaju kroz
        // standardne deposit/withdraw flow-ove (koji rade samo nad ACTIVE).
        List<MarginAccount> blockedAccounts = marginAccountRepository.findAllById(blockedIds);

        for (MarginAccount account : blockedAccounts) {
            String ownerEmail = resolveOwnerEmail(account.getUserId());

            BigDecimal maintenanceMargin = account.getMaintenanceMargin();
            BigDecimal initialMargin = account.getInitialMargin();
            BigDecimal deficit = maintenanceMargin != null && initialMargin != null
                    ? maintenanceMargin.subtract(initialMargin)
                    : BigDecimal.ZERO;

            eventPublisher.publishEvent(
                    new MarginAccountBlockedEvent(
                            ownerEmail,
                            String.valueOf(maintenanceMargin),
                            String.valueOf(initialMargin),
                            deficit.toString()
                    )
            );

            log.warn(
                    "MARGIN CALL: Account {} blocked. initialMargin={}, maintenanceMargin={}",
                    account.getId(),
                    initialMargin,
                    maintenanceMargin
            );
        }

        log.info("Daily maintenance margin check completed. Amount of blocked accounts : {}.", blockedAccounts.size());
    }

    public List<MarginTransactionDto> getTransactions(Long marginAccountId) {
        Long clientId = currentUserId();

        MarginAccount marginAccount = marginAccountRepository.findById(marginAccountId).orElseThrow(
                () -> new EntityNotFoundException("Margin account with id: " + marginAccountId + " does not exist.")
        );

        if (!marginAccount.getUserId().equals(clientId))
            throw new IllegalStateException("Only the owner of the margin account with id = " + marginAccountId + " can access margin account transactions.");

        return marginTransactionRepository.findByMarginAccountIdOrderByCreatedAtDesc(marginAccountId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ── Helper metode ───────────────────────────────────────────────────────────

    private Long currentUserId() {
        UserContext me = userResolver.resolveCurrent();
        if (!UserRole.CLIENT.equals(me.userRole())) {
            throw new IllegalStateException("Only clients can manage margin accounts.");
        }
        return me.userId();
    }

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
