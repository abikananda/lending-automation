package com.abika.model;

public class Investment {
    private String ruleName;
    private Double walletAmount;
    private Double walletAmountAtRuleStart = 0.0;  // Track wallet at start of rule for per-rule lent calculation
    private Double reservedAmount = 0.0;
    private Integer loanCounts = 0;  // Initialize to 0, not null
    private Integer lendAmtPerLoan = 0;  // Initialize to 0, not null
    private Integer totalBorrowersFinalized = 0;  // Track successful finalized borrowers

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Double getWalletAmount() {
        return walletAmount;
    }

    public void setWalletAmount(Double walletAmount) {
        this.walletAmount = walletAmount;
    }

    public Double getWalletAmountAtRuleStart() {
        return walletAmountAtRuleStart;
    }

    public void setWalletAmountAtRuleStart(Double amount) {
        this.walletAmountAtRuleStart = amount;
    }

    public Double getReservedAmount() {
        return reservedAmount;
    }

    public void setReservedAmount(Double reservedAmount) {
        this.reservedAmount = reservedAmount;
    }

    public Integer getLoanCounts() {
        return loanCounts;
    }

    public void setLoanCounts(Integer loanCounts) {
        this.loanCounts = loanCounts;
    }

    public Integer getLendAmtPerLoan() {
        return lendAmtPerLoan;
    }

    public void setLendAmtPerLoan(Integer lendAmtPerLoan) {
        this.lendAmtPerLoan = lendAmtPerLoan;
    }

    public Integer getTotalBorrowersFinalized() {
        return totalBorrowersFinalized;
    }

    public void setTotalBorrowersFinalized(Integer count) {
        this.totalBorrowersFinalized = count;
    }

    public void incrementBorrowersFinalized() {
        this.totalBorrowersFinalized++;
    }

    public Double getAmountLentInThisRule() {
        if (walletAmountAtRuleStart != null && walletAmount != null) {
            return walletAmountAtRuleStart - walletAmount;
        }
        return 0.0;
    }

    @Override
    public String toString() {
        return "Investment{" +
                "ruleName='" + ruleName + '\'' +
                ", walletAmount=" + walletAmount +
                ", walletAmountAtRuleStart=" + walletAmountAtRuleStart +
                ", reservedAmount=" + reservedAmount +
                ", loanCounts=" + loanCounts +
                ", lendAmtPerLoan=" + lendAmtPerLoan +
                ", totalBorrowersFinalized=" + totalBorrowersFinalized +
                '}';
    }
}

