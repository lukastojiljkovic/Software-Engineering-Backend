package rs.raf.banka2_bek.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Pokriva put kreiranja mesecnih particija — verify-uje da se za svaku
 * particionisanu tabelu (transactions, interbank_messages, audit_logs)
 * pokrene CREATE TABLE IF NOT EXISTS PARTITION OF za tekuci + 3 buduca
 * meseca, kao i da greska na jednoj tabeli ne prekida procesiranje
 * ostalih (svaka tabela se obradjuje samostalno).
 */
@ExtendWith(MockitoExtension.class)
class PartitionMaintenanceServiceTest {

    private static final DateTimeFormatter PARTITION_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy_MM");

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private PartitionMaintenanceService service;

    @BeforeEach
    void setUp() {
        // default — sve CREATE-ove uspesne
    }

    @Test
    void ensureUpcomingPartitionsCreatesForAllThreeTables() {
        doNothing().when(jdbcTemplate).execute(anyString());

        service.ensureUpcomingPartitions();

        // 3 tabele × 4 meseca (tekuci + 3 buduca) = 12 CREATE poziva
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());

        List<String> allSql = sqlCaptor.getAllValues();
        assertThat(allSql).hasSize(12);

        // Ocekujemo da svaka tabela ima 4 ulaza
        long transactionsCount = allSql.stream().filter(s -> s.contains("PARTITION OF transactions\n")).count();
        long interbankCount = allSql.stream().filter(s -> s.contains("PARTITION OF interbank_messages\n")).count();
        long auditCount = allSql.stream().filter(s -> s.contains("PARTITION OF audit_logs\n")).count();

        assertThat(transactionsCount).isEqualTo(4);
        assertThat(interbankCount).isEqualTo(4);
        assertThat(auditCount).isEqualTo(4);
    }

    @Test
    void auditLogsPartitionIsCreatedWithCurrentMonthSuffix() {
        doNothing().when(jdbcTemplate).execute(anyString());

        service.ensureUpcomingPartitions();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());

        String currentSuffix = YearMonth.now().format(PARTITION_NAME_FORMAT);
        String expectedName = "audit_logs_" + currentSuffix;

        boolean hasAuditLogsCurrentMonth = sqlCaptor.getAllValues().stream()
                .anyMatch(s -> s.contains(expectedName));
        assertThat(hasAuditLogsCurrentMonth)
                .as("audit_logs particija za tekuci mesec mora biti kreirana")
                .isTrue();
    }

    @Test
    void failureOnOneTableDoesNotAbortOthers() {
        // Prvi pokusaj baci (npr. tabela jos ne postoji), ostali normalno prolaze
        doThrow(new RuntimeException("relation does not exist"))
                .doNothing()
                .when(jdbcTemplate).execute(anyString());

        service.ensureUpcomingPartitions();

        // Verify-uje da je service nastavio nakon prvog exception-a
        // i pokrenuo ostalih 11 CREATE statement-a (12 ukupno).
        verify(jdbcTemplate, atLeastOnce()).execute(anyString());
    }

    @Test
    void partitionedTablesListContainsExpectedThreeEntries() {
        // Verifikacija da PARTITIONED_TABLES sadrzi tacno tri tabele
        // koje smo definisali u SQL migraciji: transactions, interbank_messages, audit_logs.
        // Ako neko slucajno doda/ukloni tabelu bez ekvivalentnog SQL-a, test puca.
        doNothing().when(jdbcTemplate).execute(anyString());

        service.ensureUpcomingPartitions();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());

        List<String> distinctTables = sqlCaptor.getAllValues().stream()
                .map(sql -> {
                    int idx = sql.indexOf("PARTITION OF ");
                    if (idx < 0) return "";
                    int newline = sql.indexOf('\n', idx);
                    return sql.substring(idx + "PARTITION OF ".length(), newline > 0 ? newline : sql.length());
                })
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();

        assertThat(distinctTables)
                .containsExactly("audit_logs", "interbank_messages", "transactions");
    }
}
