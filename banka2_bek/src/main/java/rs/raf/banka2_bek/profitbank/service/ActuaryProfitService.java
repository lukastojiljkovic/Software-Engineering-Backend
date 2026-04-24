package rs.raf.banka2_bek.profitbank.service;

import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.profitbank.dto.ProfitBankDtos.ActuaryProfitDto;

import java.util.List;

/*
================================================================================
 TODO — SERVIS KOJI RACUNA PROFIT PO AKTUARU
 Zaduzen: BE tim
 Spec referenca: Celina 4, linija 358 "spisak svih aktuara, profit u RSD"
--------------------------------------------------------------------------------
 LOGIKA "profit aktuara":
  Za svaki aktuar (supervizor + agent):
    sum preko svih DONE ordera njegovih:
      profit_order = (trenutna_cena * portfolio.quantity) - (prosecna_buy * portfolio.quantity)
  Ukratko: isto kao portfolio profit sa userId=aktuar.
  Plus realized profit iz SELL ordera.
  Konvertovati u RSD bez komisije.

 METODE:
  List<ActuaryProfitDto> listAllActuariesProfit();
    - Preko svih Employee sa permisijama AGENT ili SUPERVISOR
    - za svakog: computeProfit()
    - sortirano descending po totalProfitRsd

 CACHE:
  Kalkulacija je teska (skroz svi orderi + portfolio). Cache 5 min
  ili invalidiraj pri svakom order fill-u.
================================================================================
*/
@Service
public class ActuaryProfitService {

    // TODO: injectovati OrderRepository, PortfolioRepository, ListingRepository,
    //   EmployeeRepository, ActuaryInfoRepository, CurrencyConversionService

    public List<ActuaryProfitDto> listAllActuariesProfit() {
        throw new UnsupportedOperationException("TODO");
    }
}
