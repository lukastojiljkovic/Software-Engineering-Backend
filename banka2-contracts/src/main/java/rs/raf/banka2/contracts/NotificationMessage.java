package rs.raf.banka2.contracts;

import java.util.Map;

/**
 * Inter-servisna poruka za email notifikaciju. `data` nosi sva polja kao stringove
 * (BigDecimal/LocalDate/int se serijalizuju preko toString(), parsiraju u consumer-u).
 */
public record NotificationMessage(NotificationKind kind, Map<String, String> data) {

    /** Defanzivna kopija — `data` je nepromenljiva mapa (odbija i null kljuceve/vrednosti). */
    public NotificationMessage {
        data = Map.copyOf(data);
    }
}
