package rs.raf.banka2_bek.interbank.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/*
================================================================================
 TODO — AUDIT LOG SVIH PORUKA IZMEDJU BANAKA
 Zaduzen: BE tim
 Spec referenca: Celina 4, linija 424 "Potrebno je definisati format poruka"
--------------------------------------------------------------------------------
 SVRHA:
 Cuvamo svaku outbound i inbound poruku izmedju banaka. Koristi se za:
   - debug (ko je sta poslao i kad)
   - idempotent processing (ako druga banka posalje isti messageId 2 puta,
     ignorisi drugu)
   - retry (ako ne stigne odgovor, znamo sta da posaljemo ponovo)

 POLJA:
  - id                 PK
  - messageId          jedinstveni UUID poruke (unique index)
  - transactionId      referenca na InterbankTransaction.transactionId
  - direction          INBOUND / OUTBOUND (vidi InterbankMessageDirection enum)
  - type               InterbankMessageType: PREPARE / READY / NOT_READY /
                       COMMIT / COMMITTED / ABORT / ABORTED /
                       RESERVE_SHARES / RESERVE_SHARES_CONFIRM /
                       TRANSFER_OWNERSHIP / OWNERSHIP_CONFIRM /
                       FINAL_CONFIRM / CHECK_STATUS
  - peerBankCode       banka s kojom smo razmenili
  - payload            JSON telo poruke (longtext / postgres jsonb)
  - httpStatus         status koda (za outbound: sta je dosao; za inbound:
                       sta smo mi vratili)
  - createdAt          timestamp

 INDEX:
  - message_id (unique)
  - transaction_id

 RETRY:
  Ako outbound ne dobije 200/2xx, InterbankRetryScheduler prepoznaje po
  message-u sa status != 2xx i retry-uje.
================================================================================
*/
@Entity
@Table(name = "interbank_messages", indexes = {
        @Index(name = "idx_ibm_message_id",  columnList = "message_id", unique = true),
        @Index(name = "idx_ibm_transaction", columnList = "transaction_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterbankMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 64)
    private String messageId;

    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InterbankMessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InterbankMessageType type;

    @Column(name = "peer_bank_code", nullable = false, length = 16)
    private String peerBankCode;

    // TODO: razmotri @JdbcTypeCode(SqlTypes.JSON) za native jsonb mapiranje u PG;
    //   za sada plain string je OK, parse/serialize rucno.
    @Column(columnDefinition = "text")
    private String payload;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
