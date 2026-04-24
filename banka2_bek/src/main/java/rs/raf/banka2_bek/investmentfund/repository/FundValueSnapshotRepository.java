package rs.raf.banka2_bek.investmentfund.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.investmentfund.model.FundValueSnapshot;

import java.time.LocalDate;
import java.util.List;

/*
================================================================================
 TODO — REPOSITORY ZA FUNDVALUESNAPSHOT
 Zaduzen: BE tim
--------------------------------------------------------------------------------
  - List<FundValueSnapshot> findByFundIdAndSnapshotDateBetweenOrderByDateAsc(
        Long fundId, LocalDate from, LocalDate to);
    za FE chart po izabranom periodu

  - existsByFundIdAndSnapshotDate(Long fundId, LocalDate date);
    da scheduler ne ubaci duplikat ako se pokrene dvaput istog dana
================================================================================
*/
public interface FundValueSnapshotRepository extends JpaRepository<FundValueSnapshot, Long> {

    List<FundValueSnapshot> findByFundIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long fundId, LocalDate from, LocalDate to);

    boolean existsByFundIdAndSnapshotDate(Long fundId, LocalDate snapshotDate);
}
