package rs.raf.banka2_bek.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.notification.model.Notification;

// ============================================================
// TODO [B1 - Notifikacioni sistem | Nosilac: Mina Kovacevic, Tadija]
//
// Spring Data JPA repozitorijum za entitet Notification.
// Sluzi za perzistenciju i upite nad tabelom "notifications".
//
// IMPLEMENTIRATI (custom query metode):
//   - Page<Notification> findByRecipientIdAndRecipientType(
//         Long recipientId, String recipientType, Pageable pageable)
//         — vraca paginiranu listu svih notifikacija za datog primaoca
//
//   - Page<Notification> findByRecipientIdAndRecipientTypeAndRead(
//         Long recipientId, String recipientType, boolean read, Pageable pageable)
//         — ista lista filtrovana po statusu citanja (read = true/false)
//
//   - long countByRecipientIdAndRecipientTypeAndRead(
//         Long recipientId, String recipientType, boolean read)
//         — broj neprocitanih notifikacija (read = false) za primaoca
//
//   - @Modifying @Query("UPDATE Notification n SET n.read = true
//         WHERE n.recipientId = :recipientId
//         AND n.recipientType = :recipientType")
//     int markAllReadForRecipient(@Param("recipientId") Long recipientId,
//                                 @Param("recipientType") String recipientType)
//         — oznacava sve notifikacije primaoca kao procitane;
//           zahteva @Transactional na mestu poziva
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B1.
// ============================================================
public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
