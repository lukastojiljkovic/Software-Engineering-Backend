package rs.raf.banka2_bek.audit.service;

import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.audit.model.AuditActionType;

// ============================================================
// TODO [B7 - Audit log | Nosilac: Stasa Draskovic]
//
// Servis za kreiranje i pretragu zapisa u dnevniku revizije.
// Injektovati: AuditLogRepository (upis i citanje).
// Opciono: EmployeeRepository + ClientRepository za resolve actorName u DTO-u.
//
// IMPLEMENTIRATI:
//
//   1. record(actorId: Long, actorType: String, action: AuditActionType,
//             description: String, targetType: String, targetId: Long,
//             oldValue: String, newValue: String) -> void
//       Kreira i cuva novi AuditLog red u bazi.
//       @Transactional (writable).
//       Koristiti ovu metodu kao tacku za poziv iz ActuaryManagement, OrderService, itd.
//       Preporuka: napraviti i overload metodu bez oldValue/newValue za akcije bez "pre/posle"
//       semantike (npr. TAX_RUN_TRIGGERED).
//
//   2. query(actionType: AuditActionType, actorId: Long,
//            from: LocalDateTime, to: LocalDateTime,
//            pageable: Pageable) -> Page<AuditLogDto>
//       Filtrirani pregled audit log-a za ADMIN/SUPERVISOR.
//       @Transactional(readOnly = true).
//       Mapira AuditLog entitete u AuditLogDto (popuniti actorName lookup).
//       Svi parametri su nullable — ako je null, filter se ignorise (kao u SavingsAdminService).
//
//   3. (opciono) findByResource(targetType: String, targetId: Long) -> List<AuditLogDto>
//       Sve akcije vezane za konkretan resurs (ORDER, EMPLOYEE, ACTUARY, ...).
//       Korisno za detalj prikaz entiteta na FE-u.
//
// Napomene:
//   - Metoda record() treba da bude @Transactional(propagation = REQUIRES_NEW) ako zelimo
//     da upis u audit log uspe cak i ako pozivajuca transakcija bude rollback-ovana.
//     Ovo je bitno za logovanje neuspelih akcija — razmotriti sa nosiocem.
//   - Ne brisati AuditLog zapise — audit log je append-only.
//
// Konvencija: pratiti paket `savings` kao sablon (SavingsDepositService, SavingsAdminService).
// Spec: Zadaci_Backend.pdf, zadatak B7.
// ============================================================
@Service
public class AuditLogService {
}
