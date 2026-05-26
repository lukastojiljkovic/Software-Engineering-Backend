-- ============================================================================
-- PostgreSQL particionisanje za visoko-volumne tabele.
-- ============================================================================
-- Pokrece se rucno (DBA operacija) jer migrira postojece tabele:
--   psql -h db -U banka2user -d banka2 -f db-partitioning.sql
--
-- Strategija: rename original tabele na *_legacy, kreiraj particionisanu sa
-- istom strukturom (PARTITION BY RANGE), kreiraj 3 mesecne particije
-- (proslost + tekuci + buducnost), prebaci podatke INSERT-om, drop legacy.
--
-- Hibernate ne razlikuje particionisanu od obicne tabele — radi normalno
-- kroz @Entity. Particije se odrzavaju iz `PartitionMaintenanceService`-a.
--
-- NAPOMENA O `orders` TABELI:
-- `orders` zivi u trading-service-u (zasebna baza `trading_db`), NE u banka2.
-- Prethodna `orders_p` mirror putanja je bila dead code (orders nikad ne
-- postoji u banka2 bazi). Uklonjena 26.05.2026. Particionisanje orders se
-- moze uraditi kasnije kao zaseban task na trading-service SQL fajlu.
-- ============================================================================

BEGIN;

-- ─── transactions ────────────────────────────────────────────────────────
-- Particionisemo po `created_at` koloni (Hibernate mapira `createdAt` polje
-- iz `Transaction` entity-ja na `created_at` po default naming strategiji).
-- Particionisanje bezbedno zahteva da je particionisuca kolona u PRIMARY KEY-u.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
               WHERE c.relname = 'transactions' AND n.nspname = 'public'
               AND c.relkind = 'r')  -- 'r' = regular table; 'p' = partitioned
       AND NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                       WHERE c.relname = 'transactions' AND n.nspname = 'public'
                       AND c.relkind = 'p')
    THEN
        ALTER TABLE transactions RENAME TO transactions_legacy;

        EXECUTE format($f$
            CREATE TABLE transactions (LIKE transactions_legacy INCLUDING DEFAULTS INCLUDING CONSTRAINTS)
            PARTITION BY RANGE (created_at);
        $f$);

        -- Pocetna particija pokriva sve podatke iz legacy tabele.
        EXECUTE format($f$
            CREATE TABLE transactions_legacy_archive
            PARTITION OF transactions
            FOR VALUES FROM (MINVALUE) TO ('%s-01');
        $f$, to_char(date_trunc('month', NOW()), 'YYYY-MM'));

        INSERT INTO transactions SELECT * FROM transactions_legacy;
        DROP TABLE transactions_legacy;
    END IF;
END $$;

-- ─── interbank_messages ──────────────────────────────────────────────────
-- Audit log za inter-bank protokol. Particionisemo po created_at — high-volume
-- tabela u produkciji (svaki 2PC ciklus = 3 message-a).
--
-- VAZNO: PostgreSQL zahteva da UNIQUE indeks na particionisanoj tabeli mora
-- ukljuciti particijski kljuc. Stari `idx_ibm_idempotence` je bio na samo
-- (sender_routing_number, locally_generated_key) — recreate-ujemo sa
-- created_at posle migracije.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
               WHERE c.relname = 'interbank_messages' AND n.nspname = 'public'
               AND c.relkind = 'r')
    THEN
        ALTER TABLE interbank_messages RENAME TO interbank_messages_legacy;

        EXECUTE format($f$
            CREATE TABLE interbank_messages (LIKE interbank_messages_legacy INCLUDING DEFAULTS INCLUDING CONSTRAINTS)
            PARTITION BY RANGE (created_at);
        $f$);

        EXECUTE format($f$
            CREATE TABLE interbank_messages_archive
            PARTITION OF interbank_messages
            FOR VALUES FROM (MINVALUE) TO ('%s-01');
        $f$, to_char(date_trunc('month', NOW()), 'YYYY-MM'));

        INSERT INTO interbank_messages SELECT * FROM interbank_messages_legacy;
        DROP TABLE interbank_messages_legacy;
    END IF;
END $$;

-- UNIQUE indeks za §2.2 idempotency — MORA ukljuciti particijski kljuc.
-- Drop stari (ako je Hibernate napravio pre particionisanja) i kreiraj novi
-- koji sadrzi created_at.
DROP INDEX IF EXISTS idx_ibm_idempotence;
CREATE UNIQUE INDEX IF NOT EXISTS idx_ibm_idempotence
    ON interbank_messages (sender_routing_number, locally_generated_key, created_at);

-- ─── audit_logs ──────────────────────────────────────────────────────────
-- B7 audit log — particionisemo po created_at (append-only, immutable zapisi,
-- mesecno raste sa svakom administrativnom akcijom).

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE c.relname = 'audit_logs' AND n.nspname = 'public'
             AND c.relkind = 'r')
     AND NOT EXISTS (
         SELECT 1 FROM pg_partitioned_table pt
         JOIN pg_class c ON c.oid = pt.partrelid
         WHERE c.relname = 'audit_logs'
     ) THEN
    CREATE TABLE audit_logs_backup AS SELECT * FROM audit_logs;
    CREATE TABLE audit_logs_partitioned (LIKE audit_logs INCLUDING ALL)
      PARTITION BY RANGE (created_at);
    INSERT INTO audit_logs_partitioned SELECT * FROM audit_logs_backup;
    DROP TABLE audit_logs CASCADE;
    ALTER TABLE audit_logs_partitioned RENAME TO audit_logs;
    DROP TABLE audit_logs_backup;

    -- Pocetna particija pokriva sve podatke iz backup-a (do tekuceg meseca).
    EXECUTE format($f$
        CREATE TABLE audit_logs_archive
        PARTITION OF audit_logs
        FOR VALUES FROM (MINVALUE) TO ('%s-01');
    $f$, to_char(date_trunc('month', NOW()), 'YYYY-MM'));
  END IF;
END $$;

-- ─── Kreiraj mesecne particije za tekuci + sledeca 3 meseca ──────────────
-- (PartitionMaintenanceService ovo radi automatski na startup-u i kroz cron,
-- ali ovde ih kreiramo eksplicitno za inicijalni setup.)

DO $$
DECLARE
    target_month date;
    partition_suffix text;
    partition_from date;
    partition_to date;
    base_table text;
BEGIN
    FOR offset_months IN 0..3 LOOP
        target_month := date_trunc('month', NOW())::date + (offset_months || ' months')::interval;
        partition_from := target_month;
        partition_to := target_month + INTERVAL '1 month';
        partition_suffix := to_char(target_month, 'YYYY_MM');

        FOR base_table IN SELECT unnest(ARRAY['transactions', 'interbank_messages', 'audit_logs']) LOOP
            IF EXISTS (SELECT 1 FROM pg_class WHERE relname = base_table AND relkind = 'p') THEN
                EXECUTE format(
                    'CREATE TABLE IF NOT EXISTS %I_%s PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
                    base_table, partition_suffix, base_table, partition_from, partition_to
                );
            END IF;
        END LOOP;
    END LOOP;
END $$;

COMMIT;

-- ─── Provera: lista particija ────────────────────────────────────────────
-- SELECT
--     parent.relname AS parent_table,
--     child.relname AS partition,
--     pg_get_expr(child.relpartbound, child.oid) AS bound
-- FROM pg_inherits
-- JOIN pg_class parent ON parent.oid = pg_inherits.inhparent
-- JOIN pg_class child  ON child.oid  = pg_inherits.inhrelid
-- WHERE parent.relname IN ('transactions', 'interbank_messages', 'audit_logs')
-- ORDER BY parent.relname, child.relname;
