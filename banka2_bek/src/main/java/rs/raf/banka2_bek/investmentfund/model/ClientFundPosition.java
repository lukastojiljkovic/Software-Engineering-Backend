package rs.raf.banka2_bek.investmentfund.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
================================================================================
 TODO — POZICIJA JEDNOG KLIJENTA/BANKE U JEDNOM FONDU
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 263-295 (ClientFundPosition)
--------------------------------------------------------------------------------
 SVRHA:
 Kolika je ukupna uplata jednog klijenta u jedan fond. Posle svake
 ClientFundTransaction-e se azurira (investicija -> uplata += x,
 povlacenje -> uplata -= x, ako je vecinsko povlacenje moze doci do 0 i
 obrisati se pozicija).

 POLJA:
  - id
  - fundId          FK na InvestmentFund.id
  - userId          klijent (ili bankin "ownerClientId" ako fond drzi banka)
  - userRole        "CLIENT" — banka se tretira kao klijent preko ownerClientId
                    (spec napomena 2 u liniji 350)
  - totalInvested   suma svih uplata minus povlacenja, u RSD
  - lastModifiedAt

 IZVEDENI PODACI (ne u bazi):
  - procenatFonda = totalInvested / sum(all positions) * 100
                    (ili bolje: koristi currentValueOfFund i shareValue)
  - trenutnaVrednostPozicije = (totalInvested / sum_all_invested) * fundValue

 UNIQUE:
  (fundId, userId, userRole) — jedan klijent ima najvise 1 poziciju po fondu

 BRISANJE:
  Kad totalInvested padne na 0 (potpuno povlacenje), pozicija se brise
  (ili setuje active=false — vidi ClientFundTransaction).
================================================================================
*/
@Entity
@Table(name = "client_fund_positions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cfp_user_fund",
                columnNames = {"fund_id", "user_id", "user_role"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientFundPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fund_id", nullable = false)
    private Long fundId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_role", nullable = false, length = 16)
    private String userRole; // "CLIENT" — banka je klijent sa ownerClientId

    @Column(name = "total_invested", nullable = false, precision = 19, scale = 4)
    @org.hibernate.annotations.ColumnDefault("0")
    private BigDecimal totalInvested;

    @Column(name = "last_modified_at", nullable = false)
    private LocalDateTime lastModifiedAt;
}
