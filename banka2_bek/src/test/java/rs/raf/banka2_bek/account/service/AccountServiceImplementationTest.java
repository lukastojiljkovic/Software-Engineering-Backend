package rs.raf.banka2_bek.account.service;

import rs.raf.banka2_bek.account.dto.AccountResponseDto;
import rs.raf.banka2_bek.account.model.*;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.account.service.implementation.AccountServiceImplementation;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.card.service.CardService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.company.repository.CompanyRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.account.dto.CreateAccountDto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplementationTest {

    @Mock private AccountRepository accountRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private CardService cardService;

    @InjectMocks
    private AccountServiceImplementation accountService;

    private void mockAuthenticatedUser(String email) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password("password")
                .authorities("ROLE_CLIENT")
                .build();

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Nested
    @DisplayName("getMyAccounts")
    class GetMyAccounts {

        @Test
        @DisplayName("vraca 3 racuna za Stefana, sortirano po availableBalance DESC")
        void returnsAccountsSorted() {
            var stefan = Client.builder()
                    .id(1L).firstName("Stefan").lastName("Jovanović")
                    .email("stefan.jovanovic@gmail.com").build();

            var rsd = new Currency();
            rsd.setId(8L);
            rsd.setCode("RSD");

            var eur = new Currency();
            eur.setId(1L);
            eur.setCode("EUR");

            var nikola = Employee.builder()
                    .id(1L).firstName("Nikola").lastName("Milenković").build();
            var tamara = Employee.builder()
                    .id(2L).firstName("Tamara").lastName("Pavlović").build();

            var stednja = Account.builder()
                    .id(2L).name("Štednja").accountNumber("222000112345678912")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.SAVINGS)
                    .currency(rsd).client(stefan).employee(nikola)
                    .balance(new BigDecimal("520000.0000"))
                    .availableBalance(new BigDecimal("520000.0000"))
                    .dailyLimit(new BigDecimal("100000.0000"))
                    .monthlyLimit(new BigDecimal("500000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            var glavni = Account.builder()
                    .id(1L).name("Glavni račun").accountNumber("222000112345678911")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.STANDARD)
                    .currency(rsd).client(stefan).employee(nikola)
                    .balance(new BigDecimal("185000.0000"))
                    .availableBalance(new BigDecimal("178000.0000"))
                    .dailyLimit(new BigDecimal("250000.0000"))
                    .monthlyLimit(new BigDecimal("1000000.0000"))
                    .dailySpending(new BigDecimal("7000.0000"))
                    .monthlySpending(new BigDecimal("45000.0000"))
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            var euro = Account.builder()
                    .id(3L).name("Euro račun").accountNumber("222000121345678921")
                    .accountType(AccountType.FOREIGN).accountSubtype(AccountSubtype.PERSONAL)
                    .currency(eur).client(stefan).employee(tamara)
                    .balance(new BigDecimal("2500.0000"))
                    .availableBalance(new BigDecimal("2350.0000"))
                    .dailyLimit(new BigDecimal("5000.0000"))
                    .monthlyLimit(new BigDecimal("20000.0000"))
                    .dailySpending(new BigDecimal("150.0000"))
                    .monthlySpending(new BigDecimal("800.0000"))
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            mockAuthenticatedUser("stefan.jovanovic@gmail.com");
            when(clientRepository.findByEmail("stefan.jovanovic@gmail.com")).thenReturn(Optional.of(stefan));
            when(accountRepository.findAccessibleAccounts(1L, AccountStatus.ACTIVE))
                    .thenReturn(List.of(stednja, glavni, euro));

            List<AccountResponseDto> result = accountService.getMyAccounts();

            assertEquals(3, result.size());
            assertEquals("Štednja", result.get(0).getName());
            assertEquals("SAVINGS", result.get(0).getAccountSubType());
            assertEquals("Glavni račun", result.get(1).getName());
            assertEquals(new BigDecimal("7000.0000"), result.get(1).getReservedFunds());
            assertEquals("Euro račun", result.get(2).getName());
            assertEquals("EUR", result.get(2).getCurrencyCode());
        }

        @Test
        @DisplayName("vraca praznu listu ako klijent nema aktivne racune")
        void returnsEmptyList() {
            Client testClient = Client.builder()
                    .id(1L).firstName("Stefan").lastName("Jovanović")
                    .email("stefan.jovanovic@gmail.com").build();

            mockAuthenticatedUser("stefan.jovanovic@gmail.com");
            when(clientRepository.findByEmail("stefan.jovanovic@gmail.com")).thenReturn(Optional.of(testClient));
            when(accountRepository.findAccessibleAccounts(1L, AccountStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());

            assertTrue(accountService.getMyAccounts().isEmpty());
        }

        @Test
        @DisplayName("baca izuzetak ako klijent ne postoji u bazi")
        void clientNotFound_returnsEmptyList() {
            mockAuthenticatedUser("nepostojeci@example.com");
            when(clientRepository.findByEmail("nepostojeci@example.com")).thenReturn(Optional.empty());

            List<AccountResponseDto> result = accountService.getMyAccounts();
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("vraca praznu listu ako korisnik nije autentifikovan")
        void notAuthenticated_returnsEmptyList() {
            SecurityContext ctx = mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(null);
            SecurityContextHolder.setContext(ctx);

            List<AccountResponseDto> result = accountService.getMyAccounts();
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getAccountById")
    class GetAccountById {

        @Test
        @DisplayName("Stefan vidi detalje svog tekuceg racuna")
        void ownerCanAccess() {
            Client testClient = Client.builder()
                    .id(1L).firstName("Stefan").lastName("Jovanović")
                    .email("stefan.jovanovic@gmail.com").build();

            Currency rsdCurrency = new Currency();
            rsdCurrency.setId(8L);
            rsdCurrency.setCode("RSD");

            var nikola = Employee.builder()
                    .id(1L).firstName("Nikola").lastName("Milenković").build();

            var glavni = Account.builder()
                    .id(1L).name("Glavni račun").accountNumber("222000112345678911")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.STANDARD)
                    .currency(rsdCurrency).client(testClient).employee(nikola)
                    .balance(new BigDecimal("185000.0000"))
                    .availableBalance(new BigDecimal("178000.0000"))
                    .dailyLimit(new BigDecimal("250000.0000"))
                    .monthlyLimit(new BigDecimal("1000000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            mockAuthenticatedUser("stefan.jovanovic@gmail.com");
            when(clientRepository.findByEmail("stefan.jovanovic@gmail.com")).thenReturn(Optional.of(testClient));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(glavni));

            AccountResponseDto result = accountService.getAccountById(1L);

            assertEquals("Glavni račun", result.getName());
            assertEquals("CHECKING", result.getAccountType());
            assertEquals("Stefan Jovanović", result.getOwnerName());
            assertEquals("Nikola Milenković", result.getCreatedByEmployee());
            assertEquals(new BigDecimal("7000.0000"), result.getReservedFunds());
            assertNull(result.getCompany());
        }

        @Test
        @DisplayName("poslovni racun sadrzi CompanyDto (TechStar DOO)")
        void businessAccountIncludesCompany() {
            Client testClient = Client.builder()
                    .id(2L).firstName("Milica").lastName("Nikolić")
                    .email("milica.nikolic@gmail.com").build();

            Currency rsdCurrency = new Currency();
            rsdCurrency.setId(8L);
            rsdCurrency.setCode("RSD");

            Employee testEmployee = Employee.builder()
                    .id(2L).firstName("Tamara").lastName("Pavlović").build();

            Company testCompany = Company.builder().id(1L).name("TechStar DOO").registrationNumber("12345678")
                            .taxNumber("123456789").activityCode("62.01").address("Bulevar Mihajla Pupina 10, Novi Beograd").build();

            Account testBusinessAccount = Account.builder()
                    .id(5L).name("TechStar poslovanje").accountNumber("222000112345678914")
                    .accountType(AccountType.BUSINESS).accountSubtype(AccountSubtype.STANDARD)
                    .currency(rsdCurrency).client(testClient).company(testCompany).employee(testEmployee)
                    .balance(new BigDecimal("1250000.0000"))
                    .availableBalance(new BigDecimal("1230000.0000"))
                    .dailyLimit(new BigDecimal("1000000.0000"))
                    .monthlyLimit(new BigDecimal("5000000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            mockAuthenticatedUser("milica.nikolic@gmail.com");
            when(clientRepository.findByEmail("milica.nikolic@gmail.com")).thenReturn(Optional.of(testClient));
            when(accountRepository.findById(5L)).thenReturn(Optional.of(testBusinessAccount));

            AccountResponseDto result = accountService.getAccountById(5L);

            assertEquals("BUSINESS", result.getAccountType());
            assertNotNull(result.getCompany());
            assertEquals("TechStar DOO", result.getCompany().getName());
            assertEquals("12345678", result.getCompany().getRegistrationNumber());
            assertEquals("123456789", result.getCompany().getTaxNumber());
            assertEquals("62.01", result.getCompany().getActivityCode());
        }

        @Test
        @DisplayName("baca izuzetak ako racun ne postoji")
        void notFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> accountService.getAccountById(999L));
        }

        @Test
        @DisplayName("Stefan ne moze da vidi Milicin racun")
        void accessDenied() {
            Client testClientWantsToSeeMilicasAccount = Client.builder()
                    .id(1L).firstName("Stefan").lastName("Jovanović")
                    .email("stefan.jovanovic@gmail.com").build();
            Client testClientMilicaSaysGoToHellStefanMyMoney = Client.builder()
                    .id(2L).firstName("Milica").lastName("Nikolić")
                    .email("milica.nikolic@gmail.com").build();

            var rsd = new Currency();
            rsd.setId(8L);
            rsd.setCode("RSD");

            var nikola = Employee.builder()
                    .id(1L).firstName("Nikola").lastName("Milenković").build();

            var milicin = Account.builder()
                    .id(4L).name("Lični račun").accountNumber("222000112345678913")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.STANDARD)
                    .currency(rsd).client(testClientMilicaSaysGoToHellStefanMyMoney).employee(nikola)
                    .balance(new BigDecimal("95000.0000"))
                    .availableBalance(new BigDecimal("92000.0000"))
                    .dailyLimit(new BigDecimal("250000.0000"))
                    .monthlyLimit(new BigDecimal("1000000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            mockAuthenticatedUser("stefan.jovanovic@gmail.com");
            when(clientRepository.findByEmail("stefan.jovanovic@gmail.com")).thenReturn(Optional.of(testClientWantsToSeeMilicasAccount));
            when(accountRepository.findById(4L)).thenReturn(Optional.of(milicin));

            assertThrows(IllegalStateException.class, () -> accountService.getAccountById(4L));
        }
    }

    @Nested
    @DisplayName("updateAccountName")
    class UpdateAccountName {

        @Test
        @DisplayName("uspesno menja naziv 'Glavni račun' u 'Moj novi račun'")
        void success() {
            Client testClientChangingNameOfAcc = Client.builder()
                    .id(1L).firstName("Stefan").lastName("Jovanović")
                    .email("stefan.jovanovic@gmail.com").build();

            Currency rsdCurrency = Currency.builder().id(8L).code("RSD").build();

            Employee testEmployee = Employee.builder()
                    .id(1L).firstName("Nikola").lastName("Milenković").build();

            Account testMainAccount = Account.builder()
                    .id(1L).name("Glavni račun").accountNumber("222000112345678911")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.STANDARD)
                    .currency(rsdCurrency).client(testClientChangingNameOfAcc).employee(testEmployee)
                    .balance(new BigDecimal("185000.0000"))
                    .availableBalance(new BigDecimal("178000.0000"))
                    .dailyLimit(new BigDecimal("250000.0000"))
                    .monthlyLimit(new BigDecimal("1000000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            Account testCheckingAccount = Account.builder()
                    .id(2L).name("Štednja").accountNumber("222000112345678912")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.SAVINGS)
                    .currency(rsdCurrency).client(testClientChangingNameOfAcc).employee(testEmployee)
                    .balance(new BigDecimal("520000.0000"))
                    .availableBalance(new BigDecimal("520000.0000"))
                    .dailyLimit(new BigDecimal("100000.0000"))
                    .monthlyLimit(new BigDecimal("500000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            mockAuthenticatedUser("stefan.jovanovic@gmail.com");
            when(clientRepository.findByEmail("stefan.jovanovic@gmail.com")).thenReturn(Optional.of(testClientChangingNameOfAcc));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testMainAccount));
            when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(1L, AccountStatus.ACTIVE))
                    .thenReturn(List.of(testMainAccount, testCheckingAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(testMainAccount);

            assertNotNull(accountService.updateAccountName(1L, "Moj novi račun"));
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("baca izuzetak ako racun ne postoji")
        void notFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class,
                    () -> accountService.updateAccountName(999L, "Novo ime"));
        }

        @Test
        @DisplayName("baca izuzetak ako korisnik nije vlasnik")
        void accessDenied() {
            Client testClientOwner = Client.builder()
                    .id(1L).firstName("Stefan").lastName("Jovanović")
                    .email("stefan.jovanovic@gmail.com").build();
            Client testClientNonOwner = Client.builder()
                    .id(2L).firstName("Milica").lastName("Nikolić")
                    .email("milica.nikolic@gmail.com").build();

            Currency rsd = Currency.builder().id(8L).code("RSD").build();

            Employee testEmployee = Employee.builder()
                    .id(1L).firstName("Nikola").lastName("Milenković").build();

            Account testAccount = Account.builder()
                    .id(4L).name("Lični račun").accountNumber("222000112345678913")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.STANDARD)
                    .currency(rsd).client(testClientNonOwner).employee(testEmployee)
                    .balance(new BigDecimal("95000.0000"))
                    .availableBalance(new BigDecimal("92000.0000"))
                    .dailyLimit(new BigDecimal("250000.0000"))
                    .monthlyLimit(new BigDecimal("1000000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            mockAuthenticatedUser("stefan.jovanovic@gmail.com");
            when(clientRepository.findByEmail("stefan.jovanovic@gmail.com")).thenReturn(Optional.of(testClientOwner));
            when(accountRepository.findById(4L)).thenReturn(Optional.of(testAccount));

            assertThrows(IllegalStateException.class,
                    () -> accountService.updateAccountName(4L, "Novo ime"));
        }

        @Test
        @DisplayName("baca izuzetak ako je novo ime isto kao staro")
        void sameName() {
            Client testClient = Client.builder()
                    .id(1L).firstName("Stefan").lastName("Jovanović")
                    .email("stefan.jovanovic@gmail.com").build();

            Currency rsd = Currency.builder().id(8L).code("RSD").build();

            Employee testEmployee = Employee.builder()
                    .id(1L).firstName("Nikola").lastName("Milenković").build();

            Account testCheckingAcocunt = Account.builder()
                    .id(1L).name("Glavni račun").accountNumber("222000112345678911")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.STANDARD)
                    .currency(rsd).client(testClient).employee(testEmployee)
                    .balance(new BigDecimal("185000.0000"))
                    .availableBalance(new BigDecimal("178000.0000"))
                    .dailyLimit(new BigDecimal("250000.0000"))
                    .monthlyLimit(new BigDecimal("1000000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            mockAuthenticatedUser("stefan.jovanovic@gmail.com");
            when(clientRepository.findByEmail("stefan.jovanovic@gmail.com")).thenReturn(Optional.of(testClient));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testCheckingAcocunt));

            Exception ex = assertThrows(IllegalStateException.class,
                    () -> accountService.updateAccountName(1L, "Glavni račun"));
            assertTrue(ex.getMessage().contains("differ"));
        }

        @Test
        @DisplayName("baca izuzetak jer 'Štednja' vec koristi drugi racun")
        void duplicateName() {
            Client testClient = Client.builder()
                    .id(1L).firstName("Stefan").lastName("Jovanović")
                    .email("stefan.jovanovic@gmail.com").build();

            Currency rsd = Currency.builder().id(8L).code("RSD").build();

            Employee nikola = Employee.builder()
                    .id(1L).firstName("Nikola").lastName("Milenković").build();

            Account testMainAccount = Account.builder()
                    .id(1L).name("Glavni račun").accountNumber("222000112345678911")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.STANDARD)
                    .currency(rsd).client(testClient).employee(nikola)
                    .balance(new BigDecimal("185000.0000"))
                    .availableBalance(new BigDecimal("178000.0000"))
                    .dailyLimit(new BigDecimal("250000.0000"))
                    .monthlyLimit(new BigDecimal("1000000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            Account testAccountSavings = Account.builder()
                    .id(2L).name("Štednja").accountNumber("222000112345678912")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.SAVINGS)
                    .currency(rsd).client(testClient).employee(nikola)
                    .balance(new BigDecimal("520000.0000"))
                    .availableBalance(new BigDecimal("520000.0000"))
                    .dailyLimit(new BigDecimal("100000.0000"))
                    .monthlyLimit(new BigDecimal("500000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            mockAuthenticatedUser("stefan.jovanovic@gmail.com");
            when(clientRepository.findByEmail("stefan.jovanovic@gmail.com")).thenReturn(Optional.of(testClient));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testMainAccount));
            when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(1L, AccountStatus.ACTIVE))
                    .thenReturn(List.of(testMainAccount, testAccountSavings));

            Exception ex = assertThrows(IllegalStateException.class,
                    () -> accountService.updateAccountName(1L, "Štednja"));
            assertTrue(ex.getMessage().contains("already used"));
        }
    }

    @Nested
    @DisplayName("updateAccountLimits")
    class UpdateAccountLimits {

        @Test
        @DisplayName("uspesno menja oba limita (daily=300k, monthly=1.5M)")
        void bothLimits() {
            Client testClient = Client.builder()
                    .id(1L).firstName("Stefan").lastName("Jovanović")
                    .email("stefan.jovanovic@gmail.com").build();

            Currency rsd = Currency.builder().id(8L).code("RSD").build();

            Employee testEmployee = Employee.builder()
                    .id(1L).firstName("Nikola").lastName("Milenković").build();

            Account testMainAccount = Account.builder()
                    .id(1L).name("Glavni račun").accountNumber("222000112345678911")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.STANDARD)
                    .currency(rsd).client(testClient).employee(testEmployee)
                    .balance(new BigDecimal("185000.0000"))
                    .availableBalance(new BigDecimal("178000.0000"))
                    .dailyLimit(new BigDecimal("250000.0000"))
                    .monthlyLimit(new BigDecimal("1000000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            mockAuthenticatedUser("stefan.jovanovic@gmail.com");
            when(clientRepository.findByEmail("stefan.jovanovic@gmail.com")).thenReturn(Optional.of(testClient));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testMainAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(testMainAccount);

            assertNotNull(accountService.updateAccountLimits(
                    1L, new BigDecimal("300000"), new BigDecimal("1500000")));
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("menja samo dnevni limit, mesecni ostaje 1M")
        void onlyDailyLimit() {
            Client testClient = Client.builder()
                    .id(1L).firstName("Stefan").lastName("Jovanović")
                    .email("stefan.jovanovic@gmail.com").build();

            Currency rsd = Currency.builder().id(8L).code("RSD").build();

            Employee testEmployee = Employee.builder()
                    .id(1L).firstName("Nikola").lastName("Milenković").build();

            Account testMainAccount = Account.builder()
                    .id(1L).name("Glavni račun").accountNumber("222000112345678911")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.STANDARD)
                    .currency(rsd).client(testClient).employee(testEmployee)
                    .balance(new BigDecimal("185000.0000"))
                    .availableBalance(new BigDecimal("178000.0000"))
                    .dailyLimit(new BigDecimal("250000.0000"))
                    .monthlyLimit(new BigDecimal("1000000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            mockAuthenticatedUser("stefan.jovanovic@gmail.com");
            when(clientRepository.findByEmail("stefan.jovanovic@gmail.com")).thenReturn(Optional.of(testClient));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testMainAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(testMainAccount);

            accountService.updateAccountLimits(1L, new BigDecimal("300000"), null);

            assertEquals(new BigDecimal("1000000.0000"), testMainAccount.getMonthlyLimit());
        }

        @Test
        @DisplayName("baca izuzetak ako racun ne postoji")
        void notFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class,
                    () -> accountService.updateAccountLimits(999L, new BigDecimal("300000"), null));
        }

        @Test
        @DisplayName("baca izuzetak ako korisnik nije vlasnik")
        void accessDenied() {
            Client testClientMilica = Client.builder()
                    .id(2L).firstName("Milica").lastName("Nikolić")
                    .email("milica.nikolic@gmail.com").build();

            Client testClient = Client.builder()
                    .id(1L).firstName("Stefan").lastName("Jovanović")
                    .email("stefan.jovanovic@gmail.com").build();

            Currency rsd = Currency.builder().id(8L).code("RSD").build();

            Employee testEmployee = Employee.builder()
                    .id(1L).firstName("Nikola").lastName("Milenković").build();

            Account testAccountMilicaPersonal = Account.builder()
                    .id(4L).name("Lični račun").accountNumber("222000112345678913")
                    .accountType(AccountType.CHECKING).accountSubtype(AccountSubtype.STANDARD)
                    .currency(rsd).client(testClientMilica).employee(testEmployee)
                    .balance(new BigDecimal("95000.0000"))
                    .availableBalance(new BigDecimal("92000.0000"))
                    .dailyLimit(new BigDecimal("250000.0000"))
                    .monthlyLimit(new BigDecimal("1000000.0000"))
                    .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            mockAuthenticatedUser("stefan.jovanovic@gmail.com");
            when(clientRepository.findByEmail("stefan.jovanovic@gmail.com")).thenReturn(Optional.of(testClient));
            when(accountRepository.findById(4L)).thenReturn(Optional.of(testAccountMilicaPersonal));

            assertThrows(IllegalStateException.class,
                    () -> accountService.updateAccountLimits(4L, new BigDecimal("300000"), null));
        }
    }

    @Nested
    @DisplayName("createAccount")
    class CreateAccount {

        @Test
        @DisplayName("uspesno kreira tekuci RSD racun za klijenta")
        void createCheckingRsd() {
            mockAuthenticatedUser("admin@banka.rs");

            Currency rsd = Currency.builder().id(8L).code("RSD").build();
            Employee admin = Employee.builder().id(1L).firstName("Nikola").lastName("Milenković").active(true).build();
            Client stefan = Client.builder().id(1L).firstName("Stefan").lastName("Jovanović").email("stefan@gmail.com").build();

            CreateAccountDto dto = new CreateAccountDto();
            dto.setAccountType(AccountType.CHECKING);
            dto.setAccountSubtype(rs.raf.banka2_bek.account.model.AccountSubtype.STANDARD);
            dto.setCurrency("RSD");
            dto.setInitialDeposit(10000.0);
            dto.setOwnerEmail("stefan@gmail.com");
            dto.setCreateCard(false);

            when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
            when(employeeRepository.findByEmail("admin@banka.rs")).thenReturn(Optional.of(admin));
            when(clientRepository.findByEmail("stefan@gmail.com")).thenReturn(Optional.of(stefan));
            when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
                Account a = inv.getArgument(0);
                a.setId(1L);
                return a;
            });

            AccountResponseDto result = accountService.createAccount(dto);

            assertNotNull(result);
            assertEquals("RSD", result.getCurrencyCode());
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("baca gresku za nepostojecu valutu")
        void invalidCurrency() {
            // Koristimo lenient jer mockAuthenticatedUser pravi stubbinge koji se ne koriste
            // posto createAccount baca izuzetak pre nego sto stigne do auth provere
            Authentication authentication = mock(Authentication.class);
            lenient().when(authentication.isAuthenticated()).thenReturn(true);
            SecurityContext securityContext = mock(SecurityContext.class);
            lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(securityContext);

            CreateAccountDto dto = new CreateAccountDto();
            dto.setCurrency("XYZ");

            when(currencyRepository.findByCode("XYZ")).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> accountService.createAccount(dto));
        }

        @Test
        @DisplayName("baca gresku bez vlasnika za licni racun")
        void noOwnerForPersonal() {
            mockAuthenticatedUser("admin@banka.rs");

            Currency rsd = Currency.builder().id(8L).code("RSD").build();
            Employee admin = Employee.builder().id(1L).firstName("Admin").lastName("Admin").active(true).build();

            CreateAccountDto dto = new CreateAccountDto();
            dto.setAccountType(AccountType.CHECKING);
            dto.setCurrency("RSD");

            when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
            when(employeeRepository.findByEmail("admin@banka.rs")).thenReturn(Optional.of(admin));

            assertThrows(RuntimeException.class, () -> accountService.createAccount(dto));
        }

        @Test
        @DisplayName("kreira racun sa auto-karticom")
        void createWithAutoCard() {
            mockAuthenticatedUser("admin@banka.rs");

            Currency rsd = Currency.builder().id(8L).code("RSD").build();
            Employee admin = Employee.builder().id(1L).firstName("Nikola").lastName("M").active(true).build();
            Client stefan = Client.builder().id(1L).firstName("Stefan").lastName("J").email("stefan@gmail.com").build();

            CreateAccountDto dto = new CreateAccountDto();
            dto.setAccountType(AccountType.CHECKING);
            dto.setCurrency("RSD");
            dto.setOwnerEmail("stefan@gmail.com");
            dto.setCreateCard(true);

            when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
            when(employeeRepository.findByEmail("admin@banka.rs")).thenReturn(Optional.of(admin));
            when(clientRepository.findByEmail("stefan@gmail.com")).thenReturn(Optional.of(stefan));
            when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
                Account a = inv.getArgument(0);
                a.setId(5L);
                return a;
            });

            accountService.createAccount(dto);
            verify(cardService).createCardForAccount(eq(5L), eq(1L), isNull());
        }

        @Test
        @DisplayName("kreira poslovni racun sa firmom")
        void createBusinessAccount() {
            mockAuthenticatedUser("admin@banka.rs");

            Currency rsd = Currency.builder().id(8L).code("RSD").build();
            Employee admin = Employee.builder().id(1L).firstName("Nikola").lastName("M").active(true).build();
            Client milica = Client.builder().id(2L).firstName("Milica").lastName("N").email("milica@gmail.com").build();

            CreateAccountDto dto = new CreateAccountDto();
            dto.setAccountType(AccountType.CHECKING);
            dto.setCurrency("RSD");
            dto.setOwnerEmail("milica@gmail.com");
            dto.setCompanyName("Test DOO");
            dto.setRegistrationNumber("12345678");
            dto.setTaxId("123456789");
            dto.setActivityCode("62.01");
            dto.setCreateCard(false);

            when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
            when(employeeRepository.findByEmail("admin@banka.rs")).thenReturn(Optional.of(admin));
            when(clientRepository.findByEmail("milica@gmail.com")).thenReturn(Optional.of(milica));
            when(companyRepository.save(any(Company.class))).thenAnswer(inv -> {
                Company c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });
            when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
                Account a = inv.getArgument(0);
                a.setId(10L);
                return a;
            });

            AccountResponseDto result = accountService.createAccount(dto);
            assertNotNull(result);
            verify(companyRepository).save(any(Company.class));
        }

        @Test
        @DisplayName("kreira klijenta iz users tabele ako ne postoji u clients")
        void createClientFromUsersTable() {
            mockAuthenticatedUser("admin@banka.rs");

            Currency rsd = Currency.builder().id(8L).code("RSD").build();
            Employee admin = Employee.builder().id(1L).firstName("Nikola").lastName("M").active(true).build();

            User user = new User();
            user.setFirstName("Luka");
            user.setLastName("S");
            user.setEmail("luka@gmail.com");
            user.setPassword("pass");

            CreateAccountDto dto = new CreateAccountDto();
            dto.setAccountType(AccountType.CHECKING);
            dto.setCurrency("RSD");
            dto.setOwnerEmail("luka@gmail.com");
            dto.setCreateCard(false);

            when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
            when(employeeRepository.findByEmail("admin@banka.rs")).thenReturn(Optional.of(admin));
            when(clientRepository.findByEmail("luka@gmail.com")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("luka@gmail.com")).thenReturn(Optional.of(user));
            when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
                Client c = inv.getArgument(0);
                c.setId(10L);
                return c;
            });
            when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
                Account a = inv.getArgument(0);
                a.setId(20L);
                return a;
            });

            AccountResponseDto result = accountService.createAccount(dto);
            assertNotNull(result);
            verify(clientRepository).save(any(Client.class)); // auto-kreiran client
        }
    }

    @Nested
    @DisplayName("getAllAccounts / getAccountsByClient")
    class PortalQueries {

        @Test
        @DisplayName("getAllAccounts vraca paginiranu listu")
        void getAllAccounts() {
            Currency rsd = Currency.builder().id(8L).code("RSD").build();
            Employee nikola = Employee.builder().id(1L).firstName("Nikola").lastName("M").build();
            Client stefan = Client.builder().id(1L).firstName("Stefan").lastName("J").build();

            Account a = Account.builder().id(1L).accountNumber("222000112345678911")
                    .accountType(AccountType.CHECKING).accountSubtype(rs.raf.banka2_bek.account.model.AccountSubtype.STANDARD)
                    .currency(rsd).client(stefan).employee(nikola)
                    .balance(BigDecimal.valueOf(100000)).availableBalance(BigDecimal.valueOf(100000))
                    .dailyLimit(BigDecimal.valueOf(250000)).monthlyLimit(BigDecimal.valueOf(1000000))
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            Page<Account> page = new PageImpl<>(List.of(a), PageRequest.of(0, 10), 1);
            when(accountRepository.findAllWithOwnerFilter(isNull(), any(PageRequest.class))).thenReturn(page);

            Page<AccountResponseDto> result = accountService.getAllAccounts(0, 10, null);
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("getAccountsByClient vraca samo racune jednog klijenta")
        void getAccountsByClient() {
            Currency rsd = Currency.builder().id(8L).code("RSD").build();
            Employee nikola = Employee.builder().id(1L).firstName("Nikola").lastName("M").build();
            Client stefan = Client.builder().id(1L).firstName("Stefan").lastName("J").build();

            Account a1 = Account.builder().id(1L).accountNumber("111")
                    .accountType(AccountType.CHECKING).currency(rsd).client(stefan).employee(nikola)
                    .balance(BigDecimal.TEN).availableBalance(BigDecimal.TEN)
                    .dailyLimit(BigDecimal.TEN).monthlyLimit(BigDecimal.TEN)
                    .status(AccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

            when(accountRepository.findByClientId(1L)).thenReturn(List.of(a1));

            List<AccountResponseDto> result = accountService.getAccountsByClient(1L);
            assertEquals(1, result.size());
        }
    }
}