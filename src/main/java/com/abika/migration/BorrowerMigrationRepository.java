package com.abika.migration;

import com.abika.services.DBService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BorrowerMigrationRepository {
    static final String IDS_SQL = "SELECT id FROM borrower_loans WHERE id > ? AND name IS NOT NULL "
            + "AND TRIM(name) <> '' ORDER BY id LIMIT ?";
    static final String BORROWER_SQL = "SELECT id, loanId AS loan_id, creditScore AS credit_score, "
            + "lendenScore AS lenden_score, income, loanAmount AS loan_amount, borrowerType AS borrower_type, "
            + "interestRate AS interest_rate, name, age, lendingAmount AS lending_amount, tenure, user, "
            + "created_date FROM borrower_loans WHERE id = ?";

    private final DBService database;

    public BorrowerMigrationRepository(DBService database) {
        this.database = database;
    }

    public List<Long> findIdsAfter(long afterId, int limit) {
        return database.executeSelect(IDS_SQL, List.of(afterId, limit)).stream()
                .map(row -> number(row, "id").longValue()).toList();
    }

    public Optional<BorrowerMigrationRecord> findById(long id) {
        return database.executeSelect(BORROWER_SQL, List.of(id)).stream().findFirst().map(this::map);
    }

    private BorrowerMigrationRecord map(Map<String, Object> row) {
        return new BorrowerMigrationRecord(number(row, "id").longValue(), text(row, "loan_id"),
                integer(row, "credit_score"), integer(row, "lenden_score"), decimal(row, "income"),
                decimal(row, "loan_amount"), text(row, "borrower_type"), decimal(row, "interest_rate"),
                text(row, "name"), integer(row, "age"), decimal(row, "lending_amount"),
                integer(row, "tenure"), text(row, "user"), instant(row, "created_date"));
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) return number;
        throw new IllegalStateException("Missing numeric column " + key);
    }

    private Integer integer(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof BigDecimal decimal) return decimal;
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.util.Date date) return date.toInstant();
        return null;
    }
}
