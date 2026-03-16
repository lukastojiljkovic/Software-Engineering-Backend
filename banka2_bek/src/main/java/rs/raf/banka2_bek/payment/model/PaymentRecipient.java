package rs.raf.banka2_bek.payment.model;

import jakarta.persistence.*;
import lombok.*;

import rs.raf.banka2_bek.client.model.Client;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_recipients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Korisnik čija je ovo lista omiljenih primaoca
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false, length = 100)
    private String name;            // Naziv / ime i prezime primaoca

    @Column(nullable = false, length = 18)
    private String accountNumber;   // Broj računa primaoca

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
