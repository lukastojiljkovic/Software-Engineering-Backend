package rs.raf.banka2_bek.otc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// ============================================================
// TODO [B10 - Istorija OTC pregovora | Nosilac: Aja Timotic]
//
// Servis koji snima i preuzima historiju OTC pregovora.
// Pozivati ga iz OtcService pri svakom counter-offer, accept
// i decline dogadjaju (integraciju radi koordinator).
//
// IMPLEMENTIRATI — injektovati OtcNegotiationHistoryRepository
//   kao private final polje, pa dodati sledece metode:
//
//   1. recordEntry(Long negotiationId, Integer quantity,
//                  BigDecimal pricePerShare, BigDecimal premium,
//                  LocalDate settlementDate, String status,
//                  Long modifiedById, String modifiedByName)
//          : void
//       Kreira i cuva novi OtcNegotiationHistory zapis.
//       Pozivati unutar @Transactional konteksta pozivaca —
//       metoda sama ne otvara novu transakciju.
//       Loguj na DEBUG nivou: "OTC history recorded: negotiation={} status={}".
//
//   2. getHistoryForNegotiation(Long negotiationId)
//          : List<OtcNegotiationHistoryDto>
//       Vraca hronolosku listu svih promena za jednu ponudu.
//       Baca IllegalArgumentException("Pregovor nije pronadjen")
//       ako ne postoje zapisi — proveriti pre mapiranja.
//       Mapira OtcNegotiationHistory -> OtcNegotiationHistoryDto
//       (kreirati privatnu pomocnu metodu toDto(entity)).
//
//   3. findWithFilters(String status, Long modifiedById,
//                      LocalDateTime from, LocalDateTime to,
//                      int page, int size)
//          : Page<OtcNegotiationHistoryDto>
//       Paginiran pregled historije svih pregovora sa opcionim
//       filterima. Prosledi PageRequest.of(page, size) u
//       repozitorijumsku @Query metodu. Pristup dozvoliti samo
//       SUPERVISOR i ADMIN rolama — baciti AccessDeniedException
//       ako caller nije u tim rolama (citaj iz
//       SecurityContextHolder.getContext().getAuthentication()).
//
// Importi koji ce biti potrebni:
//   import java.math.BigDecimal;
//   import java.time.LocalDate;
//   import java.time.LocalDateTime;
//   import java.util.List;
//   import org.springframework.data.domain.Page;
//   import org.springframework.data.domain.PageRequest;
//   import org.springframework.security.access.AccessDeniedException;
//   import org.springframework.security.core.context.SecurityContextHolder;
//   import rs.raf.banka2_bek.otc.dto.OtcNegotiationHistoryDto;
//   import rs.raf.banka2_bek.otc.model.OtcNegotiationHistory;
//   import rs.raf.banka2_bek.otc.repository.OtcNegotiationHistoryRepository;
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B10.
// ============================================================

@Service
@RequiredArgsConstructor
@Slf4j
public class OtcNegotiationHistoryService {
}
