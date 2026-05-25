package rs.raf.trading.margin.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * BE-STK-06: Margin racun ciji je vlasnik kompanija (COMPANY).
 * <p>Marzni_Racuni.txt §25-27: "CompanyMarginAccount ce biti marzni racun za kompanije,
 * companyId - id kompanije kojoj racun pripada".
 * <p>Novi nullable {@code company_id} kolona (Hibernate ddl-auto=update auto-doda).
 */
@Entity
@DiscriminatorValue("COMPANY")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CompanyMarginAccount extends MarginAccount {

    /** ID kompanije koja je vlasnik margin racuna. */
    @Column(name = "company_id")
    private Long companyId;

    @Override
    public Long getOwnerId() {
        return companyId;
    }

    @Override
    public String getOwnerType() {
        return "COMPANY";
    }
}
