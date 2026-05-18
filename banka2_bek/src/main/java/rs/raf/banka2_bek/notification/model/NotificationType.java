package rs.raf.banka2_bek.notification.model;

// ============================================================
// TODO [B1 - Notifikacioni sistem | Nosilac: Mina Kovacevic, Tadija]
//
// Enumeracija svih tipova in-app notifikacija.
// Vrednosti su fiksne i ne treba ih menjati.
// Svaki tip odgovara jednom poslovnom dogadjaju koji okida
// slanje notifikacije korisniku.
//
// IMPLEMENTIRATI:
//   - Nema dodatne logike — enum vrednosti su vec deklarisane.
//   - Po potrebi dodati anotaciju @Column ili @Enumerated(EnumType.STRING)
//     tamo gde se enum koristi kao JPA polje u Notification entitetu.
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B1.
// ============================================================
public enum NotificationType {
    PAYMENT,
    TRANSFER,
    LIMIT_CHANGE,
    CARD_BLOCKED,
    LOAN_CREATED,
    LOAN_APPROVED,
    ORDER_PENDING,
    ORDER_APPROVED,
    ORDER_DECLINED,
    ORDER_EXECUTED,
    ORDER_PARTIAL_FILL,
    ORDER_CANCELLED,
    OTC_COUNTER_OFFER,
    OTC_ACCEPTED,
    OTC_DECLINED,
    OTC_CONTRACT_EXPIRING,
    ACCOUNT_LOCKED,
    GENERAL
}
