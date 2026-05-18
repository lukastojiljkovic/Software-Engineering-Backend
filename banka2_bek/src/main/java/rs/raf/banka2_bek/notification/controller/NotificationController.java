package rs.raf.banka2_bek.notification.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.raf.banka2_bek.notification.service.NotificationService;

// ============================================================
// TODO [B1 - Notifikacioni sistem | Nosilac: Mina Kovacevic, Tadija]
//
// REST kontroler za upravljanje in-app notifikacijama.
// Bazna putanja: /notifications
// Injektovati: NotificationService.
// Svi endpoint-i zahtevaju autentifikaciju (JWT Bearer token).
// Korisnik se identifikuje iz SecurityContextHolder.getContext()
// .getAuthentication().getName() (email iz JWT sub claim-a).
// Tip primaoca ("CLIENT" ili "EMPLOYEE") se odredjuje na osnovu
// JWT role claim-a (getAuthorities()) ili dodavanjem posebnog
// @RequestParam — odluka implementatora, biti konzistentan.
//
// IMPLEMENTIRATI (svi endpoint-i):
//
//   1. GET /notifications
//      — parametri: @RequestParam int page (default 0),
//                   @RequestParam int size (default 20),
//                   @RequestParam(required = false) Boolean onlyUnread
//      — poziva NotificationService.getMyNotifications
//      — vraca ResponseEntity<Page<NotificationDto>>
//      — HTTP 200 OK
//
//   2. GET /notifications/unread-count
//      — nema parametara osim autentifikacije
//      — poziva NotificationService.getUnreadCount
//      — vraca ResponseEntity<Map<String, Long>> sa kljucem "count"
//      — HTTP 200 OK
//
//   3. PATCH /notifications/{id}/read
//      — @PathVariable Long id
//      — poziva NotificationService.markOneRead(id, email, recipientType)
//      — vraca ResponseEntity<NotificationDto> sa azuriranom notifikacijom
//      — HTTP 200 OK; ako notifikacija ne pripada korisniku -> HTTP 403
//
//   4. PATCH /notifications/read-all
//      — nema request body-ja
//      — poziva NotificationService.markAllRead(email, recipientType)
//      — vraca ResponseEntity<Void> (HTTP 204 No Content)
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B1.
// ============================================================
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
}
