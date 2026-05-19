package rs.raf.banka2_bek.internalapi.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2.contracts.internal.InternalAccountDto;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountCategory;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.account.util.AccountNumberUtils;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.company.repository.CompanyRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Interni write API: provizionira gotovinski (RSD) racun za nov investicioni
 * fond. Logika je istovetna onoj iz {@code InvestmentFundService.createFund}
 * (Account.builder() blok) — kad se fond domen iseli u trading-service, taj
 * servis ce zvati ovaj endpoint umesto da sam pravi Account (banka-core ostaje
 * jedini vlasnik racuna).
 *
 * Zasticen X-Internal-Key (InternalAuthFilter + ROLE_INTERNAL).
 */
@Service
public class InternalAccountProvisioningService {

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrencyRepository currencyRepository;
    private final CompanyRepository companyRepository;

    public InternalAccountProvisioningService(AccountRepository accountRepository,
                                              EmployeeRepository employeeRepository,
                                              CurrencyRepository currencyRepository,
                                              CompanyRepository companyRepository) {
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
        this.currencyRepository = currencyRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Kreira FUND gotovinski racun za dati fond.
     *
     * @param fundName          ime fonda — racun dobija ime "Fund: " + fundName
     * @param managerEmployeeId id supervizora (menadzera fonda)
     * @return metadata novokreiranog racuna
     * @throws IllegalArgumentException ako menadzer (zaposleni) ne postoji
     * @throws IllegalStateException    ako RSD valuta ili bankina firma nedostaju
     */
    @Transactional
    public InternalAccountDto provisionFundAccount(String fundName, Long managerEmployeeId) {
        Employee supervisor = employeeRepository.findById(managerEmployeeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Employee #" + managerEmployeeId + " not found."));

        Currency rsd = currencyRepository.findByCode("RSD")
                .orElseThrow(() -> new IllegalStateException("RSD currency not found."));

        // Fond accountu pripada NASOJ banci (is_bank=true), ne drzavi
        // (is_state=true) — Celina 2 §73-78 razdvaja banku od drzave.
        Company bankCompany = companyRepository.findByIsBankTrue()
                .or(companyRepository::findByIsStateTrue) // backward-compat dok seed/testovi ne postave is_bank
                .orElseThrow(() -> new IllegalStateException("Bank company not found."));

        String accountNumber;
        do {
            accountNumber = AccountNumberUtils.generate(AccountType.BUSINESS, null, true);
        } while (accountRepository.existsByAccountNumber(accountNumber));

        Account fundAccount = Account.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.BUSINESS)
                .accountSubtype(null)
                .currency(rsd)
                .company(bankCompany)
                .employee(supervisor)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .reservedAmount(BigDecimal.ZERO)
                .accountCategory(AccountCategory.FUND)
                .dailyLimit(BigDecimal.ZERO)
                .monthlyLimit(BigDecimal.ZERO)
                .dailySpending(BigDecimal.ZERO)
                .monthlySpending(BigDecimal.ZERO)
                .maintenanceFee(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .name("Fund: " + fundName)
                .createdAt(LocalDateTime.now())
                .build();
        fundAccount = accountRepository.save(fundAccount);

        return new InternalAccountDto(
                fundAccount.getId(),
                fundAccount.getAccountNumber(),
                bankCompany.getName(),
                fundAccount.getBalance(),
                fundAccount.getAvailableBalance(),
                fundAccount.getReservedAmount(),
                fundAccount.getCurrency().getCode(),
                fundAccount.getStatus().name()
        );
    }
}
