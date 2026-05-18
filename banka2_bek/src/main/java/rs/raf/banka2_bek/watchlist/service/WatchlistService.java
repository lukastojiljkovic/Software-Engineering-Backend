package rs.raf.banka2_bek.watchlist.service;

// ============================================================
// TODO [B6 - Watchlist | Nosilac: L. Draskovic]
//
// Servis koji implementira svu biznis logiku za liste pracenih hartija.
// Koristiti @RequiredArgsConstructor za injekciju zavisnosti i
// @Slf4j za logovanje, kao sto radi SavingsDepositService.
//
// ZAVISNOSTI (polja, @RequiredArgsConstructor):
//   - WatchlistRepository watchlistRepo
//   - WatchlistItemRepository itemRepo
//   - UserResolver userResolver        -- za identifikaciju tekuceg korisnika (JWT)
//   - ListingRepository listingRepo    -- za dohvatanje aktuelnih podataka o hartiji
//     (paket: rs.raf.banka2_bek.securities.repository ili slicno -- proveriti aktuelni paket)
//
// IMPLEMENTIRATI (metode, sve @Transactional osim read-only):
//
//   WatchlistDto createWatchlist(CreateWatchlistDto dto)
//     -- resolvuje tekuceg korisnika (CLIENT/EMPLOYEE), odredjuje ownerType,
//        proverava duplikat naziva (existsByOwnerIdAndOwnerTypeAndName),
//        kreira i snima novi Watchlist, vraca WatchlistDto.
//
//   List<WatchlistDto> listMyWatchlists()
//     -- @Transactional(readOnly=true)
//        vraca sve liste tekuceg korisnika (findByOwnerIdAndOwnerTypeOrderByCreatedAtAsc),
//        mapira na WatchlistDto sa itemCount iz itemRepo.countByWatchlistId(id).
//
//   WatchlistDto renameWatchlist(Long watchlistId, CreateWatchlistDto dto)
//     -- pronalazi listu i proverava vlasnistvo (findByIdAndOwnerIdAndOwnerType),
//        proverava da novi naziv nije duplikat,
//        setuje novo ime, snima, vraca WatchlistDto.
//
//   void deleteWatchlist(Long watchlistId)
//     -- proverava vlasnistvo, poziva itemRepo.deleteAllByWatchlistId,
//        zatim watchlistRepo.delete.
//
//   WatchlistItemDto addItem(Long watchlistId, Long listingId)
//     -- proverava vlasnistvo liste,
//        proverava da hartija postoji u listings tabeli (listingRepo.findById),
//        proverava duplikat (existsByWatchlistIdAndListingId -- baca IllegalArgumentException ako vec postoji),
//        kreira WatchlistItem, dohvata live podatke (cena, dnevna promena, obim)
//        i vraca WatchlistItemDto sa svim poljima popunjenim.
//
//   void removeItem(Long watchlistId, Long listingId)
//     -- proverava vlasnistvo liste,
//        proverava da stavka postoji (findByWatchlistIdAndListingId -- baca 404 ako ne postoji),
//        poziva itemRepo.deleteByWatchlistIdAndListingId.
//
//   List<WatchlistItemDto> listItems(Long watchlistId, String securityTypeFilter)
//     -- @Transactional(readOnly=true)
//        proverava vlasnistvo liste,
//        dohvata sve stavke (findByWatchlistIdOrderByAddedAtAsc),
//        za svaku stavku dohvata listing iz listingRepo i puni live podatke,
//        ako securityTypeFilter nije null, filtrira po listing.type (STOCK/FOREX/FUTURE/OPTION),
//        vraca List<WatchlistItemDto>.
//
// POMOCNE METODE (private):
//   - Watchlist resolveOwnedWatchlist(Long watchlistId, Long ownerId, WatchlistOwnerType ownerType)
//       -- findByIdAndOwnerIdAndOwnerType, baca IllegalArgumentException("Lista ne postoji ili nije vasa.") ako nije pronadjena
//   - WatchlistOwnerType resolveOwnerType(UserContext me)
//       -- vraca WatchlistOwnerType.CLIENT ako me.isClient(), inace WatchlistOwnerType.EMPLOYEE
//   - WatchlistItemDto toItemDto(WatchlistItem item, Listing listing)
//       -- mapira WatchlistItem + Listing na WatchlistItemDto sa live podacima
//
// Konvencija: pratiti paket `savings` kao sablon (SavingsDepositService).
// Spec: Zadaci_Backend.pdf, zadatak B6.
// ============================================================

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistService {
}
