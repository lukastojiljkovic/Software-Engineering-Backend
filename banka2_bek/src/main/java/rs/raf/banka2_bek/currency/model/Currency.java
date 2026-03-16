package rs.raf.banka2_bek.currency.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "currencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 3)
    private String code;           // ISO 4217 (npr. EUR, USD)

    @Column(nullable = false, length = 64)
    private String name;           // Pun naziv (Euro, US Dollar…)

    @Column(nullable = false, length = 8)
    private String symbol;         // Simbol (€, $, £…)

    @Column(nullable = false, length = 64)
    private String country;        // Politički entitet / država

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
