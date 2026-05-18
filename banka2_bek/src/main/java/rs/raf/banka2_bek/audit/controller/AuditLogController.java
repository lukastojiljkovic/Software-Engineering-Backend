package rs.raf.banka2_bek.audit.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ============================================================
// TODO [B7 - Audit log | Nosilac: Stasa Draskovic]
//
// REST kontroler koji izlaze audit log ADMIN i SUPERVISOR korisnicima.
// Bazna putanja: /audit
// Pristup: ogranicen na ADMIN i SUPERVISOR — dodati u GlobalSecurityConfig:
//   .requestMatchers(GET, "/audit/**").hasAnyAuthority("ROLE_ADMIN","ADMIN","SUPERVISOR")
// (pratiti pattern iz GlobalSecurityConfig za /actuaries/** i /profit-bank/**)
//
// IMPLEMENTIRATI:
//
//   1. GET /audit
//       Paginiran pregled svih zapisa sa filterima.
//       Query parametri (svi optional):
//         - actionType (String, mapirati u AuditActionType enum, ignorisati ako null)
//         - actorId    (Long)
//         - from       (String ISO-8601 LocalDateTime, parsirati u LocalDateTime)
//         - to         (String ISO-8601 LocalDateTime)
//         - page       (int, default 0)
//         - size       (int, default 20)
//       Vraca: ResponseEntity<Page<AuditLogDto>>
//       Delegira: auditLogService.query(...)
//
//   2. GET /audit/{id}
//       Jedan zapis po ID-u.
//       Vraca: ResponseEntity<AuditLogDto>
//       Baca: 404 ResponseStatusException ako zapis ne postoji.
//
//   3. (opciono) GET /audit/resource/{targetType}/{targetId}
//       Svi zapisi za konkretan resurs (npr. /audit/resource/ORDER/42).
//       Vraca: ResponseEntity<List<AuditLogDto>>
//       Delegira: auditLogService.findByResource(targetType, targetId)
//
// Napomene:
//   - Injektovati samo AuditLogService (nije potreban direktan pristup repozitorijumu).
//   - Koristiti @RequiredArgsConstructor (Lombok) i final polja.
//   - Ne dodavati @PreAuthorize — security se konfigurise centralno u GlobalSecurityConfig.
//
// Konvencija: pratiti paket `savings` kao sablon (SavingsAdminController).
// Spec: Zadaci_Backend.pdf, zadatak B7.
// ============================================================
@RestController
@RequestMapping("/audit")
public class AuditLogController {
}
