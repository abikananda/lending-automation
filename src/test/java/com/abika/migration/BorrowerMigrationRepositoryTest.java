package com.abika.migration;

import com.abika.services.DBService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BorrowerMigrationRepositoryTest {
    private final DBService database = mock(DBService.class);
    private final BorrowerMigrationRepository repository = new BorrowerMigrationRepository(database);

    @Test
    void returnsCursorPageOfBorrowerIds() {
        when(database.executeSelect(BorrowerMigrationRepository.IDS_SQL, List.of(10L, 2)))
                .thenReturn(List.of(Map.of("id", 11L), Map.of("id", 15L)));

        assertEquals(List.of(11L, 15L), repository.findIdsAfter(10L, 2));
    }

    @Test
    void returnsOneCompleteBorrowerBySourceId() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 11L);
        row.put("loan_id", "LN-1001");
        row.put("credit_score", 701);
        row.put("lenden_score", 800);
        row.put("income", new BigDecimal("50000"));
        row.put("loan_amount", new BigDecimal("12000"));
        row.put("borrower_type", "SALARIED");
        row.put("interest_rate", new BigDecimal("36.48"));
        row.put("name", "Jane Doe");
        row.put("age", 35);
        row.put("lending_amount", new BigDecimal("1000"));
        row.put("tenure", 4);
        row.put("user", "abikananda");
        row.put("created_date", Timestamp.from(Instant.parse("2026-01-01T10:00:00Z")));
        when(database.executeSelect(BorrowerMigrationRepository.BORROWER_SQL, List.of(11L)))
                .thenReturn(List.of(row));

        BorrowerMigrationRecord borrower = repository.findById(11L).orElseThrow();

        assertEquals("LN-1001", borrower.loanId());
        assertEquals("Jane Doe", borrower.name());
        assertEquals(new BigDecimal("1000"), borrower.lendingAmount());
        assertEquals(Instant.parse("2026-01-01T10:00:00Z"), borrower.createdDate());
    }
}
