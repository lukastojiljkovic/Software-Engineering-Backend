package rs.raf.trading.otc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ============================================================
// TODO [B10 - Istorija OTC pregovora | Nosilac: Aja Timotic]
//
// REST kontroler koji izlaze historiju OTC pregovora.
// Sve rute su za autentifikovane korisnike; admin/supervisor
// rute dodatno proverava servis (AccessDeniedException -> 403
// hvata OtcExceptionHandler koji vec postoji).
//
// IMPLEMENTIRATI — injektovati OtcNegotiationHistoryService
//   kao private final polje, pa dodati sledece endpoint metode:
//
//   1. GET /otc/negotiation-history/{negotiationId}
//          : ResponseEntity<List<OtcNegotiationHistoryDto>>
//       Hronoloski prikaz svih promena za jednu ponudu.
//       Dostupno i kupcu i prodavcu te ponude — servis treba
//       da proveri vlasnistvo; za sada slobodan pristup svim
//       autentifikovanim korisnicima dok tim ne definise pravila.
//       Metoda: getHistoryForNegotiation(@PathVariable Long negotiationId)
//
//   2. GET /otc/negotiation-history
//          : ResponseEntity<Page<OtcNegotiationHistoryDto>>
//       Paginiran pregled sa opcionim query parametrima:
//         @RequestParam(required = false) String status
//         @RequestParam(required = false) Long modifiedById
//         @RequestParam(required = false) String from   (ISO-8601 datum-vreme)
//         @RequestParam(required = false) String to     (ISO-8601 datum-vreme)
//         @RequestParam(defaultValue = "0") int page
//         @RequestParam(defaultValue = "20") int size
//       Parsiraj from/to u LocalDateTime.parse() pre prosledjivanja
//       servisu; vrati 400 ako je format neispravan.
//       SAMO SUPERVISOR i ADMIN (servis baca AccessDeniedException).
//       Metoda: getHistory(...)
//
// Importi koji ce biti potrebni:
//   import org.springframework.data.domain.Page;
//   import org.springframework.http.ResponseEntity;
//   import org.springframework.web.bind.annotation.*;
//   import rs.raf.trading.otc.dto.OtcNegotiationHistoryDto;
//   import rs.raf.trading.otc.service.OtcNegotiationHistoryService;
//   import java.time.LocalDateTime;
//   import java.util.List;
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B10.
// ============================================================

@RestController
@RequestMapping("/otc/negotiation-history")
@RequiredArgsConstructor
public class OtcNegotiationHistoryController {
}
