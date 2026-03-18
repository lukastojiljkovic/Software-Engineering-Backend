package rs.raf.banka2_bek.transfers.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.transfer.model.Transfer;
import rs.raf.banka2_bek.transfers.dto.TransferFxRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferResponseDto;
import rs.raf.banka2_bek.transfers.repository.AccountRepository;
import rs.raf.banka2_bek.transfers.repository.TransferRepository;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ExchangeService exchangeService;

    @InjectMocks
    private TransferService transferService;

    private Account fromAccount;
    private Account toAccount;
    private Client client;
    private Currency currency;
    private Currency eurCurrency;

    @BeforeEach
    void setUp() {
        currency = new Currency();
        currency.setId(1L);
        currency.setCode("RSD");

        client = new Client();
        client.setId(1L);
        client.setFirstName("Milica");
        client.setLastName("Zoranovic");

        fromAccount = new Account();
        fromAccount.setAccountNumber("111111111111111111");
        fromAccount.setClient(client);
        fromAccount.setCurrency(currency);
        fromAccount.setAvailableBalance(new BigDecimal("10000"));
        fromAccount.setBalance(new BigDecimal("10000"));
        fromAccount.setStatus(AccountStatus.ACTIVE);

        toAccount = new Account();
        toAccount.setAccountNumber("222222222222222222");
        toAccount.setClient(client);
        toAccount.setCurrency(currency);
        toAccount.setAvailableBalance(new BigDecimal("5000"));
        toAccount.setBalance(new BigDecimal("5000"));
        toAccount.setStatus(AccountStatus.ACTIVE);

        eurCurrency = new Currency();
        eurCurrency.setId(2L);
        eurCurrency.setCode("EUR");
    }



    @Test
    void internalTransferSucceeds() {
        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));
        //when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferInternalRequestDto request = new TransferInternalRequestDto();
        request.setFromAccountNumber("111111111111111111");
        request.setToAccountNumber("222222222222222222");
        request.setAmount(new BigDecimal("1000"));

        TransferResponseDto response = transferService.internalTransfer(request);

        assertThat(response.getFromAccountNumber()).isEqualTo("111111111111111111");
        assertThat(response.getToAccountNumber()).isEqualTo("222222222222222222");
        assertThat(response.getCommission()).isEqualTo(BigDecimal.ZERO);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(fromAccount.getAvailableBalance()).isEqualTo(new BigDecimal("9000"));
        assertThat(toAccount.getAvailableBalance()).isEqualTo(new BigDecimal("6000"));
    }

    @Test
    void internalTransferFailsWhenInsufficientFunds() {
        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));

        TransferInternalRequestDto request = new TransferInternalRequestDto();
        request.setFromAccountNumber("111111111111111111");
        request.setToAccountNumber("222222222222222222");
        request.setAmount(new BigDecimal("99999"));

        assertThatThrownBy(() -> transferService.internalTransfer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient funds");
    }


    @Test
    void internalTransferFailsWhenDifferentCurrency() {
        Currency eurCurrency = new Currency();
        eurCurrency.setId(2L);
        eurCurrency.setCode("EUR");
        toAccount.setCurrency(eurCurrency);

        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));

        TransferInternalRequestDto request = new TransferInternalRequestDto();
        request.setFromAccountNumber("111111111111111111");
        request.setToAccountNumber("222222222222222222");
        request.setAmount(new BigDecimal("1000"));

        assertThatThrownBy(() -> transferService.internalTransfer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Accounts must have the same currency");
    }

    @Test
    void internalTransferFailsWhenAccountNotActive() {
        fromAccount.setStatus(AccountStatus.INACTIVE);

        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));

        TransferInternalRequestDto request = new TransferInternalRequestDto();
        request.setFromAccountNumber("111111111111111111");
        request.setToAccountNumber("222222222222222222");
        request.setAmount(new BigDecimal("1000"));

        assertThatThrownBy(() -> transferService.internalTransfer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Source account is not active");
    }

    @Test
    void internalTransferFailsWhenDifferentClients() {
        Client otherClient = new Client();
        otherClient.setId(2L);
        otherClient.setFirstName("Luka");
        otherClient.setLastName("Draskovic");
        toAccount.setClient(otherClient);

        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));

        TransferInternalRequestDto request = new TransferInternalRequestDto();
        request.setFromAccountNumber("111111111111111111");
        request.setToAccountNumber("222222222222222222");
        request.setAmount(new BigDecimal("1000"));

        assertThatThrownBy(() -> transferService.internalTransfer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Accounts must belong to the same client");
    }

    @Test
    void internalTransferFailsWhenSameAccount() {
        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));

        TransferInternalRequestDto request = new TransferInternalRequestDto();
        request.setFromAccountNumber("111111111111111111");
        request.setToAccountNumber("111111111111111111");
        request.setAmount(new BigDecimal("1000"));

        assertThatThrownBy(() -> transferService.internalTransfer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Accounts must be different");
    }
    @Test
    void getTransferByIdSucceeds() {
        Transfer transfer = new Transfer();
        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setFromAmount(new BigDecimal("1000"));
        transfer.setExchangeRate(null);
        transfer.setCommission(BigDecimal.ZERO);
        transfer.setStatus(PaymentStatus.COMPLETED);
        transfer.setCreatedBy(client);

        when(transferRepository.findById(1L)).thenReturn(Optional.of(transfer));

        TransferResponseDto response = transferService.getTransferById(1L);

        assertThat(response.getFromAccountNumber()).isEqualTo("111111111111111111");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void getTransferByIdFailsWhenNotFound() {
        when(transferRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.getTransferById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Transfer not found");
    }



    @Test
    void fxTransferSucceeds() {
        toAccount.setCurrency(eurCurrency);

        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));
        when(exchangeService.calculateCross(1000.0, "RSD", "EUR"))
                .thenReturn(new CalculateExchangeResponseDto(9.5, 0.0095, "RSD", "EUR"));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferFxRequestDto request = new TransferFxRequestDto();
        request.setFromAccountNumber("111111111111111111");
        request.setToAccountNumber("222222222222222222");
        request.setAmount(new BigDecimal("1000"));

        TransferResponseDto response = transferService.fxTransfer(request);

        assertThat(response.getFromAccountNumber()).isEqualTo("111111111111111111");
        assertThat(response.getToAccountNumber()).isEqualTo("222222222222222222");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(fromAccount.getAvailableBalance()).isEqualTo(new BigDecimal("9000"));
        assertThat(toAccount.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("5009.5"));
    }

    @Test
    void fxTransferFailsWhenSameCurrency() {
        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));

        TransferFxRequestDto request = new TransferFxRequestDto();
        request.setFromAccountNumber("111111111111111111");
        request.setToAccountNumber("222222222222222222");
        request.setAmount(new BigDecimal("1000"));

        assertThatThrownBy(() -> transferService.fxTransfer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Accounts must have different currencies");
    }

    @Test
    void fxTransferFailsWhenInsufficientFunds() {
        toAccount.setCurrency(eurCurrency);

        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));
        when(exchangeService.calculateCross(99999.0, "RSD", "EUR"))
                .thenReturn(new CalculateExchangeResponseDto(950.0, 0.0095, "RSD", "EUR"));

        TransferFxRequestDto request = new TransferFxRequestDto();
        request.setFromAccountNumber("111111111111111111");
        request.setToAccountNumber("222222222222222222");
        request.setAmount(new BigDecimal("99999"));

        assertThatThrownBy(() -> transferService.fxTransfer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void fxTransferFailsWhenAccountNotActive() {
        fromAccount.setStatus(AccountStatus.INACTIVE);
        toAccount.setCurrency(eurCurrency);

        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));

        TransferFxRequestDto request = new TransferFxRequestDto();
        request.setFromAccountNumber("111111111111111111");
        request.setToAccountNumber("222222222222222222");
        request.setAmount(new BigDecimal("1000"));

        assertThatThrownBy(() -> transferService.fxTransfer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Source account is not active");
    }
    @Test
    void fxTransferFailsWhenSameAccount() {
        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));

        TransferFxRequestDto request = new TransferFxRequestDto();
        request.setFromAccountNumber("111111111111111111");
        request.setToAccountNumber("111111111111111111");
        request.setAmount(new BigDecimal("1000"));

        assertThatThrownBy(() -> transferService.fxTransfer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Accounts must be different");
    }
    @Test
    void getAllTransfersSucceeds() {
        Transfer transfer1 = new Transfer();
        transfer1.setFromAccount(fromAccount);
        transfer1.setToAccount(toAccount);
        transfer1.setFromAmount(new BigDecimal("1000"));
        transfer1.setExchangeRate(null);
        transfer1.setCommission(BigDecimal.ZERO);
        transfer1.setStatus(PaymentStatus.COMPLETED);
        transfer1.setCreatedBy(client);

        Transfer transfer2 = new Transfer();
        transfer2.setFromAccount(fromAccount);
        transfer2.setToAccount(toAccount);
        transfer2.setFromAmount(new BigDecimal("2000"));
        transfer2.setExchangeRate(null);
        transfer2.setCommission(BigDecimal.ZERO);
        transfer2.setStatus(PaymentStatus.COMPLETED);
        transfer2.setCreatedBy(client);

        when(transferRepository.findByCreatedByOrderByCreatedAtDesc(client))
                .thenReturn(List.of(transfer1, transfer2));

        List<TransferResponseDto> result = transferService.getAllTransfers(client);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }
}