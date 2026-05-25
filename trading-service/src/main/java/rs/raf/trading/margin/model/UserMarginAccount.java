package rs.raf.trading.margin.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * BE-STK-06: Margin racun ciji je vlasnik klijent (USER).
 * <p>Marzni_Racuni.txt §21-24: "UserMarginAccount potklasa ce biti racun za korisnike,
 * userId - id korisnika kome pripada".
 * <p>{@code userId} se cuva u natklasnom polju (kolona {@code user_id})
 * radi backwards-compat sa postojecim seed redovima.
 */
@Entity
@DiscriminatorValue("USER")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class UserMarginAccount extends MarginAccount {

    @Override
    public Long getOwnerId() {
        return getUserId();
    }

    @Override
    public String getOwnerType() {
        return "USER";
    }
}
