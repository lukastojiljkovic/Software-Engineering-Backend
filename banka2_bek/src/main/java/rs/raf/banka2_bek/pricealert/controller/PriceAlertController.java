package rs.raf.banka2_bek.pricealert.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

// ============================================================
// TODO [B5 - Cenovni alarmi (Price Alert) | Nosilac: Aleksa Vucinic]
//
// REST kontroler koji izlaze CRUD operacije nad cenovnim alarmima.
// Sve rute su autentifikovane (JWT) — autorizacija se oslanja na
// Spring Security + GlobalSecurityConfig koji mora biti azuriran
// od strane koordinatora da dozvoli /price-alerts/** za authenticated
// korisnike (klijenti + zaposleni).
//
// IMPLEMENTIRATI (dodati endpoint metode i zavisnost na PriceAlertService):
//
//   POST /price-alerts
//       Kreira novi alarm za tekuceg korisnika.
//       Body:  @Valid CreatePriceAlertDto
//       Vraca: ResponseEntity<PriceAlertDto> sa statusom 201 Created.
//       Delegira: priceAlertService.createAlert(dto)
//
//   GET /price-alerts
//       Lista svih alarma tekuceg korisnika (aktivni + ugasli).
//       Vraca: ResponseEntity<List<PriceAlertDto>> sa statusom 200 OK.
//       Delegira: priceAlertService.listMyAlerts()
//
//   DELETE /price-alerts/{id}
//       Brise alarm sa datim ID-em ako pripada tekucem korisniku.
//       @PathVariable Long id
//       Vraca: ResponseEntity<Void> sa statusom 204 No Content.
//       Delegira: priceAlertService.deleteAlert(id)
//       Napomena: servis baca AccessDeniedException (-> 403) ako
//       alarm ne pripada tekucem korisniku; GlobalExceptionHandler
//       to vec obradjuje.
//
// Imports koji ce biti potrebni:
//   import jakarta.validation.Valid;
//   import org.springframework.http.ResponseEntity;
//   import rs.raf.banka2_bek.pricealert.dto.CreatePriceAlertDto;
//   import rs.raf.banka2_bek.pricealert.dto.PriceAlertDto;
//   import rs.raf.banka2_bek.pricealert.service.PriceAlertService;
//   import java.util.List;
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B5.
// ============================================================
@RestController
@RequestMapping("/price-alerts")
@RequiredArgsConstructor
public class PriceAlertController {
}
