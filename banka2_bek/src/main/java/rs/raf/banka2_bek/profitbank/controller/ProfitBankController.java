package rs.raf.banka2_bek.profitbank.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.ClientFundPositionDto;
import rs.raf.banka2_bek.profitbank.dto.ProfitBankDtos.ActuaryProfitDto;

import java.util.List;

/*
================================================================================
 TODO — REST ENDPOINTI ZA PORTAL "PROFIT BANKE" (OPCIONO)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 353-364
--------------------------------------------------------------------------------
 SECURITY:
   sve samo za supervizore (ADMIN ili EMPLOYEE sa SUPERVISOR permisijom)

 ENDPOINTI:
   GET /profit-bank/actuary-performance   — spisak aktuara + profit
   GET /profit-bank/fund-positions        — fondovi u kojima banka ima udele
     (reuse ClientFundPositionDto sa userRole=BANK, userId=bank-owner-clientId)

 Kada je investmentfund paket gotov, ovaj controller moze se implementirati
 u par dana.
================================================================================
*/
@RestController
@RequestMapping("/profit-bank")
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
public class ProfitBankController {

    // TODO: injectovati ActuaryProfitService + InvestmentFundService.listBankPositions

    @GetMapping("/actuary-performance")
    public ResponseEntity<List<ActuaryProfitDto>> actuaryPerformance() {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/fund-positions")
    public ResponseEntity<List<ClientFundPositionDto>> fundPositions() {
        // TODO: vrati sve pozicije koje su vlasnistvo banke (InvestmentFundService.listBankPositions)
        throw new UnsupportedOperationException("TODO");
    }

    // TODO (opciono): POST /profit-bank/fund-positions/{fundId}/invest
    //   (supervizor uplacuje u fond u ime banke, sa bankinog racuna)
    //   — ali InvestmentFundService.invest vec pokriva ovo; dodati ako treba
    //     poseban endpoint bez FX komisije.

    // TODO (opciono): POST /profit-bank/fund-positions/{fundId}/withdraw
    //   — analogno za povlacenje.
}
