package rs.raf.banka2_bek.investmentfund.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.*;

import java.time.LocalDate;
import java.util.List;

/*
================================================================================
 TODO — CORE SERVICE ZA INVESTICIONE FONDOVE
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 160-351
--------------------------------------------------------------------------------
 API:
  1. createFund(CreateFundDto, Long supervisorId)
     - Validacija: supervizor (po permisiji); name unique
     - Kreiraj RSD bankin racun (AccountService.createFundAccount)
     - Upise InvestmentFund sa managerEmployeeId=supervisorId, accountId=novi
     - Inicijalni FundValueSnapshot sa vrednoscu=0
     - Vrati InvestmentFundDetailDto

  2. listDiscovery(String searchQuery, String sortField, String sortDirection)
     - Vraca sve aktivne fondove + računa fundValue/profit za svaki
     - Sortiranje/filter kako spec zahteva (Celina 4 linija 302)

  3. getFundDetails(Long fundId)
     - fundValue = account.balance + sum(portfolio.quantity * listing.price konvertovano u RSD)
     - profit = fundValue - sum(positions.totalInvested)
     - Holdings iz Portfolio sa userRole=FUND, userId=fundId
     - Performance iz FundValueSnapshot (poslednjih 30 dana default)

  4. invest(Long fundId, InvestFundDto dto, Long userId, String userRole)
     - Validacija: amount >= fund.minimumContribution
     - Ako klijent: FX komisija 1% ako konverzija; ako supervizor (banka): 0%
     - Transfer sa sourceAccountId na fund.accountId
     - Kreiraj ClientFundTransaction sa status=PENDING, potom COMPLETED
     - Upsert ClientFundPosition (ili kreiraj novu)
     - Vrati ClientFundPositionDto

  5. withdraw(Long fundId, WithdrawFundDto dto, Long userId, String userRole)
     - Ako amount null: povuci punu poziciju
     - Validacija: position.totalInvested >= amount
     - Ako fund.account.balance >= amount: odmah isplata
     - Ako fund.account.balance < amount: scheduler ce prodati hartije,
       ostaje status=PENDING, klijent dobija notifikaciju
     - Kreiraj ClientFundTransaction
     - Smanji position.totalInvested, ako <=0 obrisi ili active=false
     - Vrati ClientFundTransactionDto

  6. listMyPositions(Long userId, String userRole)
     - Vrati ClientFundPositionDto za svaku poziciju koju ima taj korisnik
     - Ukljucuje derived fields (currentValue, percentOfFund, profit)

  7. reassignFundManager(Long oldSupervisorId, Long newAdminId)
     - Poziva se iz ActuaryService.removeIsSupervisorPermission
     - InvestmentFundRepository.reassignManager(oldId, newId)
     - Audit log: "Fund X reassigned from supervisor A to admin B"

 KORISTI:
  FundValueCalculator (za derived vrednosti)
  FundLiquidationService (za auto-sell kad je likvidnost nedovoljna)
  CurrencyConversionService (za konverziju u RSD)
  AccountRepository, PortfolioRepository, ListingRepository
  ClientFundPositionRepository, ClientFundTransactionRepository
================================================================================
*/
@Service
public class InvestmentFundService {

    // TODO: injectovati sve potrebne repoze + FundValueCalculator + CurrencyConversionService

    @Transactional
    public InvestmentFundDetailDto createFund(CreateFundDto dto, Long supervisorId) {
        throw new UnsupportedOperationException("TODO");
    }

    public List<InvestmentFundSummaryDto> listDiscovery(String searchQuery, String sortField, String sortDirection) {
        throw new UnsupportedOperationException("TODO");
    }

    public InvestmentFundDetailDto getFundDetails(Long fundId) {
        throw new UnsupportedOperationException("TODO");
    }

    public List<FundPerformancePointDto> getPerformance(Long fundId, LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException("TODO");
    }

    @Transactional
    public ClientFundPositionDto invest(Long fundId, InvestFundDto dto, Long userId, String userRole) {
        throw new UnsupportedOperationException("TODO");
    }

    @Transactional
    public ClientFundTransactionDto withdraw(Long fundId, WithdrawFundDto dto, Long userId, String userRole) {
        throw new UnsupportedOperationException("TODO");
    }

    public List<ClientFundPositionDto> listMyPositions(Long userId, String userRole) {
        throw new UnsupportedOperationException("TODO");
    }

    public List<ClientFundPositionDto> listBankPositions() {
        // Za Profit Banke portal — vrati sve pozicije koje su vlasnistvo banke
        // (userId = bank.ownerClientId).
        throw new UnsupportedOperationException("TODO");
    }

    @Transactional
    public int reassignFundManager(Long oldSupervisorId, Long newAdminId) {
        throw new UnsupportedOperationException("TODO");
    }
}
