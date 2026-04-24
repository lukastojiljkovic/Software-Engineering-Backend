package rs.raf.banka2_bek.investmentfund.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/*
================================================================================
 TODO — INVESTICIONI FOND ENTITET
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 160-219 (Logika + Entitet fond)
--------------------------------------------------------------------------------
 SVRHA:
 Fond ima 1 RSD racun u banci (kreira se automatski pri kreiranju fonda),
 menadzera (supervizor), listu hartija (prati se kroz postojeci Portfolio sa
 userRole=FUND, userId=fund.id), i listu pozicija klijenata koji su ulozili
 u fond.

 POLJA:
  - id                 PK
  - name               Jedinstveno ime (dodaj unique constraint)
  - description        Kratak opis strategije
  - minimumContribution Minimalni ulog u RSD (BigDecimal)
  - managerEmployeeId  Id supervizora koji upravlja (FK na Employee)
  - accountId          Id bankinog RSD racuna koji fond koristi (FK na Account)
  - createdAt          Datum osnivanja

 IZVEDENA POLJA (ne cuvaju se, racunaju se):
  - fundValue = account.balance + sum(portfolio.quantity * listing.price) konvertovano u RSD
  - profit = fundValue - sum(ClientFundPosition.totalInvested)

 VEZE:
  - Employee (manager) — nema direktno @ManyToOne, samo cuvaj managerEmployeeId
  - Account (fund's RSD) — slicno, samo accountId
  - Portfolio — NE kroz relaciju, koristi postojeci Portfolio entitet sa
    userRole="FUND" (ili "INVESTMENT_FUND") i userId=fund.id

 PROMENA VLASNISTVA:
  Ako admin ukloni isSupervisor permisiju, svi fondovi ciji je managerEmployeeId
  taj zaposleni automatski prebace na managerEmployeeId=admina koji je uklonio.
  Ovo radi ActuaryServiceImpl.removePermission() kroz InvestmentFundService.
  (Celina 4 linija 326)

 TABELA:
  investment_funds
================================================================================
*/
@Entity
@Table(name = "investment_funds", uniqueConstraints = {
        @UniqueConstraint(name = "uk_fund_name", columnNames = "name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentFund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 1024)
    private String description;

    @Column(name = "minimum_contribution", nullable = false, precision = 19, scale = 4)
    private BigDecimal minimumContribution;

    @Column(name = "manager_employee_id", nullable = false)
    private Long managerEmployeeId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "active", nullable = false)
    @org.hibernate.annotations.ColumnDefault("1")
    private boolean active = true;

    // TODO: razmotri da li treba `inception_date` (LocalDate) razlicito od createdAt-a
    //   — spec primer prikazuje samo "Datum kreiranja". Nije obavezno.
    @Column(name = "inception_date")
    private LocalDate inceptionDate;
}
