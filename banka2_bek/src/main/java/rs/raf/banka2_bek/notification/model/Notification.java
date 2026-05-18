package rs.raf.banka2_bek.notification.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// ============================================================
// TODO [B1 - Notifikacioni sistem | Nosilac: Mina Kovacevic, Tadija]
//
// JPA entitet koji predstavlja jednu in-app notifikaciju
// persistiranu u tabeli "notifications".
//
// IMPLEMENTIRATI (sva polja dodati uz odgovarajuce JPA anotacije):
//   - recipientId     : Long,          @Column(nullable = false)
//   - recipientType   : String,        @Column(nullable = false)
//                       vrednosti: "CLIENT" ili "EMPLOYEE"
//   - type            : NotificationType, @Enumerated(EnumType.STRING),
//                       @Column(nullable = false)
//   - title           : String,        @Column(nullable = false)
//   - body            : String,        @Column(nullable = false, length = 2000)
//   - read            : boolean,       @Column(nullable = false),
//                       @ColumnDefault("0"), default vrednost false
//   - createdAt       : LocalDateTime, @Column(nullable = false)
//                       postaviti u @PrePersist ako nije eksplicitno setovano
//   - referenceType   : String,        @Column nullable (opciono — tip resursa,
//                       npr. "ORDER", "PAYMENT", "OTC_CONTRACT")
//   - referenceId     : Long,          @Column nullable (opciono — ID resursa)
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B1.
// ============================================================
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
