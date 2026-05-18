package rs.raf.banka2_bek.notification.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// ============================================================
// TODO [B1 - Notifikacioni sistem | Nosilac: Mina Kovacevic, Tadija]
//
// DTO koji se vraca klijentu kao odgovor na GET /notifications
// i za paginiranu listu notifikacija.
//
// IMPLEMENTIRATI (sva polja dodati):
//   - id            : Long       — primarni kljuc notifikacije
//   - type          : String     — naziv enum vrednosti NotificationType
//                     (npr. "PAYMENT", "OTC_ACCEPTED")
//   - title         : String     — naslov notifikacije
//   - body          : String     — telo poruke
//   - read          : boolean    — da li je korisnik procitao notifikaciju
//   - createdAt     : LocalDateTime — vreme kreiranja
//   - referenceType : String     — nullable, tip resursa (npr. "ORDER")
//   - referenceId   : Long       — nullable, ID resursa
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B1.
// ============================================================
// Napomena: skeleton je prazan pa nosi samo @NoArgsConstructor. Pri dodavanju
// polja vratiti @AllArgsConstructor i @Builder (videti Lombok napomenu u TODO-u).
@Getter
@Setter
@NoArgsConstructor
public class NotificationDto {
}
