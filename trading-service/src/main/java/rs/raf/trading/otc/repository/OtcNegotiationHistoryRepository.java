package rs.raf.trading.otc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.trading.otc.model.OtcNegotiationHistory;

// ============================================================
// TODO [B10 - Istorija OTC pregovora | Nosilac: Aja Timotic]
//
// JPA repozitorijum za citanje i pisanje historije OTC pregovora.
//
// IMPLEMENTIRATI — dodati sledece metode:
//
//   - findByNegotiationIdOrderByCreatedAtAsc(Long negotiationId)
//       : List<OtcNegotiationHistory>
//       Vraca sve zapise za jednu ponudu sortirane od najstarijeg
//       do najnovijeg (hronoloski tok pregovora).
//
//   - findByModifiedByIdOrderByCreatedAtDesc(Long modifiedById)
//       : List<OtcNegotiationHistory>
//       Vraca sve izmene koje je napravio odredjeni korisnik.
//
//   - findByStatusOrderByCreatedAtDesc(String status)
//       : List<OtcNegotiationHistory>
//       Vraca historijske zapise filtrirane po statusu ponude
//       (npr. sve izmene koje su dovele do "ACCEPTED").
//
//   - findByCreatedAtBetweenOrderByCreatedAtDesc(
//         LocalDateTime from, LocalDateTime to)
//       : List<OtcNegotiationHistory>
//       Vraca sve zapise u zadatom vremenskom intervalu —
//       koristi se za filter po datumu u kontroleru.
//       Import: import java.time.LocalDateTime;
//
//   - @Query JPQL metoda za kombinovani filter (status + modifiedById
//       + vremenski interval) sa @Param anotacijama i IS NULL OR
//       obrascem za opcionalne parametre (prati adminFindAll u
//       SavingsDepositRepository kao sablon). Predlog potpisa:
//         Page<OtcNegotiationHistory> findWithFilters(
//             @Param("status") String status,
//             @Param("modifiedById") Long modifiedById,
//             @Param("from") LocalDateTime from,
//             @Param("to") LocalDateTime to,
//             Pageable pageable);
//       Importi: Page, Pageable, @Query, @Param
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B10.
// ============================================================

public interface OtcNegotiationHistoryRepository
        extends JpaRepository<OtcNegotiationHistory, Long> {
}
