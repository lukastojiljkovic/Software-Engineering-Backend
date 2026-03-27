package rs.raf.banka2_bek.payment.service.implementation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentDirection;
import rs.raf.banka2_bek.payment.dto.PaymentListItemDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.payment.repository.PaymentAccountRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.payment.service.PaymentReceiptPdfGenerator;
import rs.raf.banka2_bek.payment.service.PaymentService;
import rs.raf.banka2_bek.notification.service.MailNotificationService;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionResponseDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;
import rs.raf.banka2_bek.transaction.service.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAccountRepository paymentAccountRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final TransactionService transactionService;
    private final PaymentReceiptPdfGenerator paymentReceiptPdfGenerator;
    private final ExchangeService exchangeService;
    private final MailNotificationService mailNotificationService;
    private final String bankRegistrationNumber;
    private static final int ORDER_NUMBER_MAX_RETRIES = 5;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentAccountRepository paymentAccountRepository,
                              AccountRepository accountRepository,
                              ClientRepository clientRepository,
                              TransactionService transactionService,
                              PaymentReceiptPdfGenerator paymentReceiptPdfGenerator,
                              ExchangeService exchangeService,
                              MailNotificationService mailNotificationService,
                              @Value("${bank.registration-number}") String bankRegistrationNumber) {
        this.paymentRepository = paymentRepository;
        this.paymentAccountRepository = paymentAccountRepository;
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.transactionService = transactionService;
        this.paymentReceiptPdfGenerator = paymentReceiptPdfGenerator;
        this.exchangeService = exchangeService;
        this.mailNotificationService = mailNotificationService;
        this.bankRegistrationNumber = bankRegistrationNumber;
    }

    @Override
    @Transactional
    public PaymentResponseDto createPayment(CreatePaymentRequestDto request) {
        Account fromAccount = paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())
                .orElseThrow(() -> new IllegalArgumentException("Source account does not exist."));

        Account toAccount = paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())
                .orElseThrow(() -> new IllegalArgumentException("Destination account does not exist."));

        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Source account is not active.");
        }

        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Destination account is not active.");
        }

        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different.");
        }

        Client client = getAuthenticatedClient();

        if (fromAccount.getClient() == null || !fromAccount.getClient().getId().equals(client.getId())) {
            throw new IllegalArgumentException("Source account does not belong to the authenticated client.");
        }

        BigDecimal amount = request.getAmount();

        if (fromAccount.getAvailableBalance() == null || fromAccount.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Nedovoljno sredstava na racunu");
        }

        if (fromAccount.getDailyLimit() != null
                && fromAccount.getDailySpending().add(amount).compareTo(fromAccount.getDailyLimit()) > 0) {
            throw new IllegalArgumentException("Prekoracen dnevni limit za ovaj racun");
        }

        if (fromAccount.getMonthlyLimit() != null
                && fromAccount.getMonthlySpending().add(amount).compareTo(fromAccount.getMonthlyLimit()) > 0) {
            throw new IllegalArgumentException("Prekoracen mesecni limit za ovaj racun");
        }

        BigDecimal transactionFee = BigDecimal.ZERO;
        BigDecimal creditedAmount = amount;
        boolean isCrossCurrency = !fromAccount.getCurrency().getId().equals(toAccount.getCurrency().getId());
        String paymentCode = request.getPaymentCode().getCode();

        if (isCrossCurrency) {
            // Cross-currency payment: route through bank accounts
            String fromCurrencyCode = fromAccount.getCurrency().getCode();
            String toCurrencyCode = toAccount.getCurrency().getCode();

            Account bankFromAccount = accountRepository.findBankAccountForUpdateByCurrency(bankRegistrationNumber, fromCurrencyCode)
                    .orElseThrow(() -> new RuntimeException("Bank account for " + fromCurrencyCode + " not found"));
            Account bankToAccount = accountRepository.findBankAccountForUpdateByCurrency(bankRegistrationNumber, toCurrencyCode)
                    .orElseThrow(() -> new RuntimeException("Bank account for " + toCurrencyCode + " not found"));

            CalculateExchangeResponseDto exchangeResult = exchangeService.calculateCross(
                    amount.doubleValue(), fromCurrencyCode, toCurrencyCode);

            creditedAmount = BigDecimal.valueOf(exchangeResult.getConvertedAmount());
            transactionFee = amount.multiply(new BigDecimal("0.005"));

            if (bankToAccount.getAvailableBalance().compareTo(creditedAmount) < 0) {
                throw new RuntimeException("Bank does not have enough " + toCurrencyCode + " reserves");
            }

            // Bank receives source currency
            bankFromAccount.setBalance(bankFromAccount.getBalance().add(amount));
            bankFromAccount.setAvailableBalance(bankFromAccount.getAvailableBalance().add(amount));

            // Bank pays target currency
            bankToAccount.setBalance(bankToAccount.getBalance().subtract(creditedAmount));
            bankToAccount.setAvailableBalance(bankToAccount.getAvailableBalance().subtract(creditedAmount));

            accountRepository.save(bankFromAccount);
            accountRepository.save(bankToAccount);
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(amount.add(transactionFee)));
        fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(amount.add(transactionFee)));
        fromAccount.setDailySpending(fromAccount.getDailySpending().add(amount));
        fromAccount.setMonthlySpending(fromAccount.getMonthlySpending().add(amount));

        toAccount.setBalance(toAccount.getBalance().add(creditedAmount));
        toAccount.setAvailableBalance(toAccount.getAvailableBalance().add(creditedAmount));

        Payment base = Payment.builder()
                .fromAccount(fromAccount)
                .toAccountNumber(request.getToAccount())
                .amount(amount)
                .fee(transactionFee)
                .currency(fromAccount.getCurrency())
                .paymentCode(paymentCode)
                .referenceNumber(request.getReferenceNumber())
                .purpose(request.getDescription())
                .status(PaymentStatus.COMPLETED)
                .createdBy(client)
                .build();

        Payment savedPayment = null;

        //Pokusava da generise jedinstven payment broj pomocu uk constrainta
        for (int attempt = 1; attempt <= ORDER_NUMBER_MAX_RETRIES; attempt++) {
            try {
                base.setOrderNumber(generateOrderNumber());
                savedPayment = paymentRepository.saveAndFlush(base); // force DB unique check now
                break;
            } catch (DataIntegrityViolationException ex) {
                String msg = ex.getMostSpecificCause().getMessage();
                if (msg == null) throw ex;

                String lower = msg.toLowerCase();
                if (!(lower.contains("order_number") || lower.contains("uk") || lower.contains("unique")))
                    throw ex;
            }
        }

        if (savedPayment == null) {
            throw new IllegalStateException("Failed to generate unique order number.");
        }

        transactionService.recordPaymentSettlement(savedPayment, toAccount, client, creditedAmount);

        try {
            mailNotificationService.sendPaymentConfirmationMail(
                    client.getEmail(),
                    savedPayment.getAmount(),
                    savedPayment.getCurrency() != null ? savedPayment.getCurrency().getCode() : null,
                    savedPayment.getFromAccount() != null ? savedPayment.getFromAccount().getAccountNumber() : null,
                    savedPayment.getToAccountNumber(),
                    savedPayment.getCreatedAt() != null ? savedPayment.getCreatedAt().toLocalDate() : java.time.LocalDate.now(),
                    savedPayment.getStatus().name());
        } catch (Exception e) {
            // Email failure must not roll back the payment transaction
        }

        return toResponse(savedPayment, client.getId());
    }

    @Override
    public Page<PaymentListItemDto> getPayments(
            Pageable pageable,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String accountNumber,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            PaymentStatus status
    ) {
        Client client = getOptionalClient();
        if (client == null) return Page.empty(pageable);
        return paymentRepository.findByUserAccountsWithFilters(
                        client.getId(),
                        fromDate,
                        toDate,
                        accountNumber,
                        minAmount,
                        maxAmount,
                        status,
                        pageable
                )
                .map(payment -> toListItem(payment, client.getId()));
    }

    @Override
    public PaymentResponseDto getPaymentById(Long paymentId) {
        Client client = getAuthenticatedClient();
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment with ID " + paymentId + " not found."));
        return toResponse(payment, client.getId());
    }

    @Override
    public byte[] getPaymentReceipt(Long paymentId) {
        Long clientId = getAuthenticatedClient().getId();
        TransactionResponseDto transaction = transactionService.getReceiptTransaction(paymentId, clientId);

        return paymentReceiptPdfGenerator.generate(transaction);
    }

    @Override
    public Page<TransactionListItemDto> getPaymentHistory(
            Pageable pageable,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            TransactionType type
    ) {
        return transactionService.getTransactions(pageable, fromDate, toDate, minAmount, maxAmount, type);
    }

    private PaymentResponseDto toResponse(Payment payment, Long authenticatedClientId) {
        return PaymentResponseDto.builder()
                .id(payment.getId())
                .orderNumber(payment.getOrderNumber())
                .fromAccount(payment.getFromAccount() != null ? payment.getFromAccount().getAccountNumber() : null)
                .toAccount(payment.getToAccountNumber())
                .amount(payment.getAmount())
                .fee(payment.getFee())
                .currency(payment.getCurrency() != null ? payment.getCurrency().getCode() : null)
                .paymentCode(payment.getPaymentCode())
                .referenceNumber(payment.getReferenceNumber())
                .description(payment.getPurpose())
                .direction(resolveDirection(payment, authenticatedClientId))
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private PaymentListItemDto toListItem(Payment payment, Long authenticatedClientId) {
        PaymentDirection direction = resolveDirection(payment, authenticatedClientId);

        return PaymentListItemDto.builder()
                .id(payment.getId())
                .orderNumber(payment.getOrderNumber())
                .fromAccount(payment.getFromAccount() != null ? payment.getFromAccount().getAccountNumber() : null)
                .toAccount(payment.getToAccountNumber())
                .amount(payment.getAmount())
                .currency(payment.getCurrency() != null ? payment.getCurrency().getCode() : null)
                .recipientName(payment.getPurpose())
                .description(payment.getPurpose())
                .direction(direction)
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private PaymentDirection resolveDirection(Payment payment, Long authenticatedClientId) {
        if (payment.getFromAccount() == null || payment.getFromAccount().getClient() == null) {
            return PaymentDirection.INCOMING;
        }

        return payment.getFromAccount().getClient().getId().equals(authenticatedClientId)
                ? PaymentDirection.OUTGOING
                : PaymentDirection.INCOMING;
    }

    private String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        throw new IllegalArgumentException("Authenticated user is required.");
    }

    private Client getAuthenticatedClient() {
        Client client = getOptionalClient();
        if (client == null) throw new IllegalArgumentException("Authenticated client does not exist.");
        return client;
    }

    private Client getOptionalClient() {
        try {
            String username = getAuthenticatedUsername();
            return clientRepository.findByEmail(username).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String generateOrderNumber() {
        return "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}

