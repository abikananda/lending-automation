package com.abika.migration;

import java.math.BigDecimal;
import java.time.Instant;

public record BorrowerMigrationRecord(
        long id, String loanId, Integer creditScore, Integer lendenScore,
        BigDecimal income, BigDecimal loanAmount, String borrowerType,
        BigDecimal interestRate, String name, Integer age, BigDecimal lendingAmount,
        Integer tenure, String user, Instant createdDate) { }
