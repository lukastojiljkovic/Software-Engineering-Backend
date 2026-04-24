package rs.raf.banka2_bek.profitbank.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/*
================================================================================
 TODO — DTO-OVI ZA PORTAL PROFIT BANKE (OPCIONO ZA GEN 2024/25)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 353-364
--------------------------------------------------------------------------------
 1. ActuaryProfitDto
    - employeeId, name, position (SUPERVISOR/AGENT)
    - totalProfitRsd (sum profita svih ordera aktuara)
    - ordersDone (broj)

 2. BankFundPositionDto
    - fundId, fundName, managerName
    - percentShare (0-100)
    - rsdValue (Banka * fundValue / 100)
    - profitRsd
================================================================================
*/
public final class ProfitBankDtos {

    private ProfitBankDtos() {}

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ActuaryProfitDto {
        private Long employeeId;
        private String name;
        private String position; // "SUPERVISOR" ili "AGENT"
        private BigDecimal totalProfitRsd;
        private Integer ordersDone;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BankFundPositionDto {
        private Long fundId;
        private String fundName;
        private String managerName;
        private BigDecimal percentShare;
        private BigDecimal rsdValue;
        private BigDecimal profitRsd;
    }
}
