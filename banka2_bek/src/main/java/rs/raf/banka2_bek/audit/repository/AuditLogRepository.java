package rs.raf.banka2_bek.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.audit.model.AuditLog;

// ============================================================
// TODO [B7 - Audit log | Nosilac: Stasa Draskovic]
//
// Spring Data JPA repozitorijum za entitet AuditLog.
// Sluzi za upis i pretragu zapisa u dnevniku revizije.
//
// IMPLEMENTIRATI:
//   - findByActorId(Long actorId, Pageable pageable) -> Page<AuditLog>
//       Svi zapisi za datog actora (zaposleni/klijent), sortirani po createdAt DESC.
//
//   - @Query JPQL metoda za filtrirani pregled (preporuka: nazvati je findFiltered ili audit):
//       Parametri: actionType (nullable AuditActionType), actorId (nullable Long),
//                  from (nullable LocalDateTime), to (nullable LocalDateTime)
//       Koristiti "(:param IS NULL OR d.field = :param)" pattern kao u SavingsDepositRepository.
//       Vracati Page<AuditLog> sa Pageable argumentom.
//
//   - findByTargetTypeAndTargetId(String targetType, Long targetId) -> List<AuditLog>
//       Svi zapisi vezani za konkretan resurs (npr. sve akcije nad ORDER id=42).
//
// Konvencija: pratiti paket `savings` kao sablon (SavingsDepositRepository).
// Spec: Zadaci_Backend.pdf, zadatak B7.
// ============================================================
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
