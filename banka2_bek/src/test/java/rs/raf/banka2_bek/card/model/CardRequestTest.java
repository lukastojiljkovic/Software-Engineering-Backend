package rs.raf.banka2_bek.card.model;

import org.junit.jupiter.api.Test;
import rs.raf.banka2_bek.account.model.Account;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CardRequestTest {

    @Test
    void noArgsConstructor_nullDefaults() {
        CardRequest r = new CardRequest();
        assertThat(r.getId()).isNull();
        assertThat(r.getStatus()).isNull();
        assertThat(r.getCreatedAt()).isNull();
    }

    @Test
    void builder_setsFields() {
        Account acc = Account.builder().accountNumber("111111111111111111").build();
        LocalDateTime now = LocalDateTime.now();
        CardRequest r = CardRequest.builder()
                .id(1L)
                .account(acc)
                .cardLimit(new BigDecimal("5000"))
                .cardType(CardType.VISA)
                .clientEmail("a@b.com")
                .clientName("Ime Prezime")
                .status("PENDING")
                .rejectionReason(null)
                .createdAt(now)
                .processedAt(now)
                .processedBy("emp@banka.rs")
                .build();

        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getAccount()).isSameAs(acc);
        assertThat(r.getCardLimit()).isEqualByComparingTo("5000");
        assertThat(r.getCardType()).isEqualTo(CardType.VISA);
        assertThat(r.getClientEmail()).isEqualTo("a@b.com");
        assertThat(r.getClientName()).isEqualTo("Ime Prezime");
        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(r.getRejectionReason()).isNull();
        assertThat(r.getCreatedAt()).isEqualTo(now);
        assertThat(r.getProcessedAt()).isEqualTo(now);
        assertThat(r.getProcessedBy()).isEqualTo("emp@banka.rs");
    }

    @Test
    void settersViaLombok() {
        CardRequest r = new CardRequest();
        r.setStatus("REJECTED");
        r.setRejectionReason("limit previsok");
        r.setClientEmail("x@y.z");
        r.setClientName("X Y");
        r.setCardLimit(new BigDecimal("1"));
        r.setCardType(CardType.MASTERCARD);
        assertThat(r.getStatus()).isEqualTo("REJECTED");
        assertThat(r.getRejectionReason()).isEqualTo("limit previsok");
        assertThat(r.getCardType()).isEqualTo(CardType.MASTERCARD);
    }

    @Test
    void onCreate_setsCreatedAtAndDefaultStatus() {
        CardRequest r = new CardRequest();
        r.onCreate();
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void onCreate_preservesExistingStatus() {
        CardRequest r = new CardRequest();
        r.setStatus("APPROVED");
        r.onCreate();
        assertThat(r.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void allArgsConstructor_direct() {
        // Konstrukor prosiren 14.05.2026 vece-5: dodato cardCategory + creditLimit
        // izmedju cardType i clientEmail (entitet polje order).
        CardRequest r = new CardRequest(
                3L, null, new BigDecimal("100"), CardType.DINACARD,
                rs.raf.banka2_bek.card.model.CardCategory.DEBIT, BigDecimal.ZERO,
                "e@e.e", "N N", "PENDING", null, LocalDateTime.now(), null, null);
        assertThat(r.getId()).isEqualTo(3L);
        assertThat(r.getCardType()).isEqualTo(CardType.DINACARD);
        assertThat(r.getCardCategory()).isEqualTo(rs.raf.banka2_bek.card.model.CardCategory.DEBIT);
    }
}
