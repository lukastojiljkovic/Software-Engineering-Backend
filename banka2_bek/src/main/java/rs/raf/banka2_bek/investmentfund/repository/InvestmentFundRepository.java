package rs.raf.banka2_bek.investmentfund.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;

import java.util.List;
import java.util.Optional;

/*
================================================================================
 TODO — REPOSITORY ZA INVESTMENTFUND
 Zaduzen: BE tim
--------------------------------------------------------------------------------
 POTREBNE METODE:
  - Optional<InvestmentFund> findByName(String name);
    za unique check pri kreiranju

  - List<InvestmentFund> findByManagerEmployeeId(Long managerId);
    za "Moji fondovi" tab supervizora

  - List<InvestmentFund> findByActiveTrueOrderByNameAsc();
    za Discovery page

  - @Query("update InvestmentFund f set f.managerEmployeeId = :newManagerId where f.managerEmployeeId = :oldManagerId")
    int reassignManager(Long oldManagerId, Long newManagerId);
    kad admin ukloni isSupervisor permisiju supervizoru
================================================================================
*/
public interface InvestmentFundRepository extends JpaRepository<InvestmentFund, Long> {

    Optional<InvestmentFund> findByName(String name);

    List<InvestmentFund> findByManagerEmployeeId(Long managerEmployeeId);

    List<InvestmentFund> findByActiveTrueOrderByNameAsc();

    @Modifying
    @Transactional
    @Query("update InvestmentFund f set f.managerEmployeeId = :newManagerId " +
           "where f.managerEmployeeId = :oldManagerId")
    int reassignManager(Long oldManagerId, Long newManagerId);
}
