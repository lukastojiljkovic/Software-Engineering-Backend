package rs.raf.banka2_bek.transfers.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.transfer.model.Transfer;
import rs.raf.banka2_bek.transfer.model.TransferType;
import rs.raf.banka2_bek.transfers.dto.TransferFxRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferResponseDto;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.transfers.repository.TransferRepository;
import rs.raf.banka2_bek.exchange.ExchangeService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final ExchangeService exchangeService;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    public TransferService(TransferRepository transferRepository,
                           AccountRepository accountRepository,
                           ExchangeService exchangeService,
                           ClientRepository clientRepository,
                           UserRepository userRepository) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.exchangeService = exchangeService;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
    }

    private TransferResponseDto mapToDto(Transfer transfer) {
        TransferResponseDto response = new TransferResponseDto();
        response.setId(transfer.getId());
        response.setOrderNumber(transfer.getOrderNumber());
        response.setFromAccountNumber(transfer.getFromAccount().getAccountNumber());
        response.setToAccountNumber(transfer.getToAccount().getAccountNumber());
        response.setAmount(transfer.getFromAmount());
        response.setToAmount(transfer.getToAmount());
        response.setFromCurrency(transfer.getFromCurrency().getCode());
        response.setToCurrency(transfer.getToCurrency().getCode());
        response.setExchangeRate(transfer.getExchangeRate());
        response.setCommission(transfer.getCommission());
        response.setClientFirstName(transfer.getCreatedBy().getFirstName());
        response.setClientLastName(transfer.getCreatedBy().getLastName());
        response.setStatus(transfer.getStatus());
        response.setCreatedAt(transfer.getCreatedAt());
        return response;
    }


    @Transactional
    public TransferResponseDto internalTransfer(TransferInternalRequestDto request) {

        Account fromAccount = accountRepository.findByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new RuntimeException("From account not found"));

        Account toAccount = accountRepository.findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new RuntimeException("To account not found"));

        // racuni moraju biti razliciti
        if (request.getFromAccountNumber().equals(request.getToAccountNumber())) {
            throw new RuntimeException("Accounts must be different");
        }

        // oba racuna moraju biti aktivna
        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Source account is not active");
        }
        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Destination account is not active");
        }

        // isti klijent
        Client actor = getAuthenticatedClient();
        ensureAccess(actor, fromAccount);
        ensureAccess(actor, toAccount);

        // ista valuta
        if (!fromAccount.getCurrency().getId().equals(toAccount.getCurrency().getId())) {
            throw new RuntimeException("Accounts must have the same currency");
        }


        if (fromAccount.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient funds");
        }


        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));
        toAccount.setAvailableBalance(toAccount.getAvailableBalance().add(request.getAmount()));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);


        Transfer transfer = new Transfer();
        transfer.setOrderNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 30));
        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setFromAmount(request.getAmount());
        transfer.setToAmount(request.getAmount());
        transfer.setFromCurrency(fromAccount.getCurrency());
        transfer.setToCurrency(toAccount.getCurrency());
        transfer.setExchangeRate(null);
        transfer.setCommission(BigDecimal.ZERO);
        transfer.setTransferType(TransferType.INTERNAL_TRANSFER);
        transfer.setStatus(PaymentStatus.COMPLETED);
        transfer.setCreatedBy(actor);

        transferRepository.save(transfer);

        return mapToDto(transfer);
    }

    @Transactional
    public TransferResponseDto fxTransfer(TransferFxRequestDto request) {

        Account fromAccount = accountRepository.findByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new RuntimeException("From account not found"));

        Account toAccount = accountRepository.findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new RuntimeException("To account not found"));

        //  racuni moraju biti razliciti
        if (request.getFromAccountNumber().equals(request.getToAccountNumber())) {
            throw new RuntimeException("Accounts must be different");
        }

        //  oba racuna moraju biti aktivna
        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Source account is not active");
        }
        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Destination account is not active");
        }

        // valute moraju biti razlicite
        if (fromAccount.getCurrency().getId().equals(toAccount.getCurrency().getId())) {
            throw new RuntimeException("Accounts must have different currencies");
        }

        // Dohvati kurs i konvertuj
        CalculateExchangeResponseDto exchangeResult = exchangeService.calculateCross(
                request.getAmount().doubleValue(),
                fromAccount.getCurrency().getCode(),
                toAccount.getCurrency().getCode()
        );

        BigDecimal toAmount = BigDecimal.valueOf(exchangeResult.getConvertedAmount());
        BigDecimal exchangeRate = BigDecimal.valueOf(exchangeResult.getExchangeRate());

        // Provera: dovoljno sredstava
        if (fromAccount.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient funds");
        }


        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(toAmount));
        toAccount.setAvailableBalance(toAccount.getAvailableBalance().add(toAmount));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);


        Transfer transfer = new Transfer();
        transfer.setOrderNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 30));
        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setFromAmount(request.getAmount());
        transfer.setToAmount(toAmount);
        transfer.setFromCurrency(fromAccount.getCurrency());
        transfer.setToCurrency(toAccount.getCurrency());
        transfer.setExchangeRate(exchangeRate);
        transfer.setCommission(BigDecimal.valueOf(0.005));
        transfer.setTransferType(TransferType.EXCHANGE);
        transfer.setStatus(PaymentStatus.COMPLETED);
        Client actor = getAuthenticatedClient();
        ensureAccess(actor, fromAccount);
        ensureAccess(actor, toAccount);

        transfer.setCreatedBy(actor);

        transferRepository.save(transfer);

        return mapToDto(transfer);
    }

    public List<TransferResponseDto> getAllTransfers(Client client, String accountNumber, java.time.LocalDate fromDate, java.time.LocalDate toDate) {
        List<Transfer> transfers = transferRepository.findByCreatedByOrderByCreatedAtDesc(client);

        java.time.LocalDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        java.time.LocalDateTime toDateTime = toDate != null ? toDate.atTime(23, 59, 59) : null;

        List<TransferResponseDto> result = new ArrayList<>();
        for (Transfer transfer : transfers) {
            if (accountNumber != null && !accountNumber.isBlank()) {
                String fromAcc = transfer.getFromAccount().getAccountNumber();
                String toAcc = transfer.getToAccount().getAccountNumber();
                if (!accountNumber.equals(fromAcc) && !accountNumber.equals(toAcc)) {
                    continue;
                }
            }
            if (fromDateTime != null && transfer.getCreatedAt().isBefore(fromDateTime)) continue;
            if (toDateTime != null && transfer.getCreatedAt().isAfter(toDateTime)) continue;
            result.add(mapToDto(transfer));
        }
        return result;
    }

    // Backward-compatible overload (used in tests)
    public List<TransferResponseDto> getAllTransfers(Client client) {
        return getAllTransfers(client, null, null, null);
    }

    public TransferResponseDto getTransferById(Long id) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transfer not found"));
        return mapToDto(transfer);
    }

    // ---------- Helpers ----------

    private void ensureAccess(Client actor, Account account) {
        if (actor == null) throw new RuntimeException("Authenticated client not found");

        if (account.getClient() != null && account.getClient().getId().equals(actor.getId())) {
            return;
        }
        if (account.getCompany() != null && account.getCompany().getAuthorizedPersons() != null) {
            boolean authorized = account.getCompany().getAuthorizedPersons().stream()
                    .anyMatch(ap -> ap.getClient() != null && ap.getClient().getId().equals(actor.getId()));
            if (authorized) return;
        }
        throw new RuntimeException("You do not have access to the specified account");
    }

    private Client getAuthenticatedClient() {
        String email = getAuthenticatedEmail();
        return clientRepository.findByEmail(email).orElseThrow(() ->
                new RuntimeException("Client not found for authenticated user"));
    }

    private String getAuthenticatedEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User is not authenticated");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof String s) {
            return s;
        }
        throw new RuntimeException("Unable to resolve user email");
    }
}
