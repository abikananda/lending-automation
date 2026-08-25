package com.abika.reporting;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExecutionMetrics {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Double initialWallet;
    private Double finalWallet;
    private Double totalDeducted;
    private Double totalReserved;
    private int totalRulesExecuted;
    private int totalRulesPassed;
    private int totalRulesFailed;
    private int totalBorrowersSelected;
    private int totalBorrowersFinalized;
    private List<RuleMetrics> ruleMetrics = new ArrayList<>();
    private List<BorrowerRecord> borrowerRecords = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public ExecutionMetrics() {
        this.startTime = LocalDateTime.now();
    }

    public void endExecution() {
        this.endTime = LocalDateTime.now();
    }

    public long getTotalExecutionTimeMs() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }

    public String getFormattedDuration() {
        long ms = getTotalExecutionTimeMs();
        long seconds = (ms / 1000) % 60;
        long minutes = (ms / 60000) % 60;
        long hours = (ms / 3600000);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    // Getters and setters
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Double getInitialWallet() { return initialWallet; }
    public void setInitialWallet(Double wallet) { this.initialWallet = wallet; }
    public Double getFinalWallet() { return finalWallet; }
    public void setFinalWallet(Double wallet) { this.finalWallet = wallet; }
    public Double getTotalDeducted() { return totalDeducted; }
    public void setTotalDeducted(Double amount) { this.totalDeducted = amount; }
    public Double getTotalReserved() { return totalReserved; }
    public void setTotalReserved(Double amount) { this.totalReserved = amount; }
    public int getTotalRulesExecuted() { return totalRulesExecuted; }
    public void setTotalRulesExecuted(int count) { this.totalRulesExecuted = count; }
    public int getTotalRulesPassed() { return totalRulesPassed; }
    public void setTotalRulesPassed(int count) { this.totalRulesPassed = count; }
    public int getTotalRulesFailed() { return totalRulesFailed; }
    public void setTotalRulesFailed(int count) { this.totalRulesFailed = count; }
    public int getTotalBorrowersSelected() { return totalBorrowersSelected; }
    public void setTotalBorrowersSelected(int count) { this.totalBorrowersSelected = count; }
    public int getTotalBorrowersFinalized() { return totalBorrowersFinalized; }
    public void setTotalBorrowersFinalized(int count) { this.totalBorrowersFinalized = count; }
    public List<RuleMetrics> getRuleMetrics() { return ruleMetrics; }
    public void addRuleMetrics(RuleMetrics metrics) { this.ruleMetrics.add(metrics); }
    public List<BorrowerRecord> getBorrowerRecords() { return borrowerRecords; }
    public void addBorrowerRecord(BorrowerRecord record) { this.borrowerRecords.add(record); }
    public List<String> getErrors() { return errors; }
    public void addError(String error) { this.errors.add(error); }

    public static class RuleMetrics {
        private String ruleName;
        private boolean passed;
        private int borrowersSelected;
        private int borrowersFinalized;
        private Double amountLent;
        private Long executionTimeMs;
        private String failureReason;

        public RuleMetrics(String ruleName) {
            this.ruleName = ruleName;
            this.amountLent = 0.0;
        }

        public String getRuleName() { return ruleName; }
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public int getBorrowersSelected() { return borrowersSelected; }
        public void setBorrowersSelected(int count) { this.borrowersSelected = count; }
        public int getBorrowersFinalized() { return borrowersFinalized; }
        public void setBorrowersFinalized(int count) { this.borrowersFinalized = count; }
        public Double getAmountLent() { return amountLent; }
        public void setAmountLent(Double amount) { this.amountLent = amount; }
        public Long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(Long timeMs) { this.executionTimeMs = timeMs; }
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String reason) { this.failureReason = reason; }

        public String getFormattedDuration() {
            if (executionTimeMs == null) return "N/A";
            long seconds = (executionTimeMs / 1000) % 60;
            long minutes = (executionTimeMs / 60000);
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    public static class BorrowerRecord {
        private String ruleName;
        private String borrowerName;
        private Double borrowingAmount;
        private Double riskRating;
        private String status; // SELECTED, FINALIZED, FAILED
        private String failureReason;
        private Long selectionTimeMs;
        private Long finalizationTimeMs;

        public BorrowerRecord(String ruleName, String borrowerName, Double borrowingAmount) {
            this.ruleName = ruleName;
            this.borrowerName = borrowerName;
            this.borrowingAmount = borrowingAmount;
            this.status = "SELECTED";
        }

        // Getters and setters
        public String getRuleName() { return ruleName; }
        public String getBorrowerName() { return borrowerName; }
        public Double getBorrowingAmount() { return borrowingAmount; }
        public Double getRiskRating() { return riskRating; }
        public void setRiskRating(Double rating) { this.riskRating = rating; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String reason) { this.failureReason = reason; }
        public Long getSelectionTimeMs() { return selectionTimeMs; }
        public void setSelectionTimeMs(Long timeMs) { this.selectionTimeMs = timeMs; }
        public Long getFinalizationTimeMs() { return finalizationTimeMs; }
        public void setFinalizationTimeMs(Long timeMs) { this.finalizationTimeMs = timeMs; }
    }
}
