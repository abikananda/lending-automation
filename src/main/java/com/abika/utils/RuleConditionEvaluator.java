package com.abika.utils;

import com.abika.model.Borrower;

public class RuleConditionEvaluator {
    /**
     * Evaluate the rule conditions for a borrower without firing Drools.
     * Returns null if all conditions pass; otherwise returns a short explanation of the first failing condition.
     */
    public static String evaluate(Borrower b, String ruleName) {
        if (b == null || ruleName == null) return "Invalid input";

        switch (ruleName.trim()) {
            case "Normal Lenders":
                return checkNormalLenders(b);
            case "Good Lenders":
                return checkGoodLenders(b);
            case "Trusted Lenders - Low Risk":
            case "Trusted Lenders - Medium Risk":
            case "Trusted Lenders - High Risk":
                return checkTrustedLenders(b, ruleName);
            case "Repeated Lenders - Low Risk":
            case "Repeated Lenders - Medium Risk":
            case "Repeated Lenders - High Risk":
                return checkRepeatedLenders(b, ruleName);
            case "Bulk Lenders":
                return checkBulkLenders(b);
            case "Filling Fast Lenders":
                return checkFillingFast(b);
            case "Daily Repayment Lenders":
                return checkDailyRepayment(b);
            case "Monthly Repayment - High Risk":
                return checkMonthlyRepayment(b);
            case "Repeated Business Lenders":
                return checkRepeatedBusinessLenders(b);
            case "Good Business Lenders":
                return checkGoodBusinessLenders(b);
            case "Bulk Business Lenders":
                return checkBulkBusinessLenders(b);
            default:
                // unknown rule - no pre-check available
                return null;
        }
    }

    private static String checkNormalLenders(Borrower b) {
        if (!"SALARIED".equalsIgnoreCase(b.getBorrowerType())) return "borrowerType != SALARIED";
        if (b.getCreditScore() < 600) return "creditScore < 600 (" + b.getCreditScore() + ")";
        if (b.getLendenScore() < 720) return "lendenScore < 720 (" + b.getLendenScore() + ")";
        if (b.getIncome() < 25000) return "income < 25000 (" + b.getIncome() + ")";
        if (b.getInterestRate() < 18) return "interestRate < 18 (" + b.getInterestRate() + ")";
        if (b.getAge() < 20 || b.getAge() > 60) return "age not in [20,60] (" + b.getAge() + ")";
        if (b.getLoanAmount() > 50000) return "loanAmount > 50000 (" + b.getLoanAmount() + ")";
        if (b.getTenure() < 1 || b.getTenure() > 6) return "tenure not in [1,6] (" + b.getTenure() + ")";
        if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.50) return "loanAmount/income > 0.50";
        if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.30) return "(loanAmount/tenure) > income*0.30";
        return null;
    }

    private static String checkGoodLenders(Borrower b) {
        if (!"SALARIED".equalsIgnoreCase(b.getBorrowerType())) return "borrowerType != SALARIED";
        if (b.getCreditScore() < 680) return "creditScore < 680 (" + b.getCreditScore() + ")";
        if (b.getLendenScore() < 750) return "lendenScore < 750 (" + b.getLendenScore() + ")";
        if (b.getIncome() < 30000) return "income < 30000 (" + b.getIncome() + ")";
        if (b.getInterestRate() < 18) return "interestRate < 18 (" + b.getInterestRate() + ")";
        if (b.getAge() < 23 || b.getAge() > 55) return "age not in [23,55] (" + b.getAge() + ")";
        if (b.getLoanAmount() > 30000) return "loanAmount > 30000 (" + b.getLoanAmount() + ")";
        if (b.getTenure() < 1 || b.getTenure() > 6) return "tenure not in [1,6] (" + b.getTenure() + ")";
        if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.30) return "loanAmount/income > 0.30";
        if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.20) return "(loanAmount/tenure) > income*0.20";
        return null;
    }

    private static String checkFillingFast(Borrower b) {
        if (!"SALARIED".equalsIgnoreCase(b.getBorrowerType())) return "borrowerType != SALARIED";
        if (b.getCreditScore() < 600) return "creditScore < 600 (" + b.getCreditScore() + ")";
        if (b.getLendenScore() < 700) return "lendenScore < 700 (" + b.getLendenScore() + ")";
        if (b.getIncome() < 25000) return "income < 25000 (" + b.getIncome() + ")";
        if (b.getInterestRate() < 18) return "interestRate < 18 (" + b.getInterestRate() + ")";
        if (b.getAge() < 18 || b.getAge() > 60) return "age not in [18,60] (" + b.getAge() + ")";
        if (b.getLoanAmount() > 50000) return "loanAmount > 50000 (" + b.getLoanAmount() + ")";
        if (b.getTenure() < 1 || b.getTenure() > 12) return "tenure not in [1,12] (" + b.getTenure() + ")";
        if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.40) return "loanAmount/income > 0.40";
        if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.30) return "(loanAmount/tenure) > income*0.30";
        if ((b.getIncome() - (b.getLoanAmount() * 1.0 / b.getTenure())) < 15000) return "income - (loanAmount/tenure) < 15000";
        return null;
    }

    private static String checkTrustedLenders(Borrower b, String ruleName) {
        if (!b.isTrusted()) return "not marked trusted";
        // Use shared logic then adjust thresholds by ruleName
        if (b.getBorrowerType() == null || !"SALARIED".equalsIgnoreCase(b.getBorrowerType())) return "borrowerType != SALARIED";
        if (ruleName.contains("Low Risk")) {
            if (b.getCreditScore() < 730) return "creditScore < 730";
            if (b.getLendenScore() < 750) return "lendenScore < 750";
            if (b.getIncome() < 80000) return "income < 80000";
            if (b.getAge() < 25 || b.getAge() > 55) return "age not in [25,55]";
            if (b.getLoanAmount() > 30000) return "loanAmount > 30000";
            if (b.getTenure() < 1 || b.getTenure() > 4) return "tenure not in [1,4]";
            if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.30) return "loanAmount/income > 0.30";
            if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.20) return "(loanAmount/tenure) > income*0.20";
        } else if (ruleName.contains("Medium Risk")) {
            if (b.getCreditScore() < 650) return "creditScore < 650";
            if (b.getLendenScore() < 750) return "lendenScore < 750";
            if (b.getIncome() < 50000) return "income < 50000";
            if (b.getAge() < 23 || b.getAge() > 55) return "age not in [23,55]";
            if (b.getLoanAmount() > 50000) return "loanAmount > 50000";
            if (b.getTenure() < 1 || b.getTenure() > 6) return "tenure not in [1,6]";
            if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.40) return "loanAmount/income > 0.40";
            if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.30) return "(loanAmount/tenure) > income*0.30";
        } else if (ruleName.contains("High Risk")) {
            if (b.getCreditScore() < 600) return "creditScore < 600";
            if (b.getLendenScore() < 700) return "lendenScore < 700";
            if (b.getIncome() < 25000) return "income < 25000";
            if (b.getAge() < 20 || b.getAge() > 60) return "age not in [20,60]";
            if (b.getLoanAmount() > 100000) return "loanAmount > 100000";
            if (b.getTenure() < 1 || b.getTenure() > 12) return "tenure not in [1,12]";
            if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.50) return "loanAmount/income > 0.50";
            if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.50) return "(loanAmount/tenure) > income*0.50";
        }
        return null;
    }

    private static String checkRepeatedLenders(Borrower b, String ruleName) {
        if (!b.isRepeated()) return "not marked repeated";
        if (b.getBorrowerType() == null || !"SALARIED".equalsIgnoreCase(b.getBorrowerType())) return "borrowerType != SALARIED";
        if (ruleName.contains("Low Risk")) {
            if (b.getCreditScore() < 730) return "creditScore < 730";
            if (b.getLendenScore() < 770) return "lendenScore < 770";
            if (b.getIncome() < 80000) return "income < 80000";
            if (b.getAge() < 25 || b.getAge() > 55) return "age not in [25,55]";
            if (b.getLoanAmount() > 30000) return "loanAmount > 30000";
            if (b.getTenure() < 1 || b.getTenure() > 4) return "tenure not in [1,4]";
            if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.30) return "loanAmount/income > 0.30";
            if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.20) return "(loanAmount/tenure) > income*0.20";
        } else if (ruleName.contains("Medium Risk")) {
            if (b.getCreditScore() < 700) return "creditScore < 700";
            if (b.getLendenScore() < 750) return "lendenScore < 750";
            if (b.getIncome() < 50000) return "income < 50000";
            if (b.getAge() < 20 || b.getAge() > 55) return "age not in [20,55]";
            if (b.getLoanAmount() > 50000) return "loanAmount > 50000";
            if (b.getTenure() < 1 || b.getTenure() > 6) return "tenure not in [1,6]";
            if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.40) return "loanAmount/income > 0.40";
            if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.20) return "(loanAmount/tenure) > income*0.20";
        } else if (ruleName.contains("High Risk")) {
            if (b.getCreditScore() < 600) return "creditScore < 600";
            if (b.getLendenScore() < 700) return "lendenScore < 700";
            if (b.getIncome() < 25000) return "income < 25000";
            if (b.getAge() < 18 || b.getAge() > 60) return "age not in [18,60]";
            if (b.getLoanAmount() > 100000) return "loanAmount > 100000";
            if (b.getTenure() < 1 || b.getTenure() > 12) return "tenure not in [1,12]";
            if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.50) return "loanAmount/income > 0.50";
            if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.40) return "(loanAmount/tenure) > income*0.40";
        }
        return null;
    }

    private static String checkBulkLenders(Borrower b) {
        if (!"SALARIED".equalsIgnoreCase(b.getBorrowerType())) return "borrowerType != SALARIED";
        if (b.getCreditScore() < 550) return "creditScore < 550";
        if (b.getLendenScore() < 700) return "lendenScore < 700";
        if (b.getIncome() < 25000) return "income < 25000";
        if (b.getInterestRate() < 18) return "interestRate < 18";
        if (b.getAge() < 20 || b.getAge() > 60) return "age not in [20,60]";
        if (b.getLoanAmount() > 100000) return "loanAmount > 100000";
        if (b.getTenure() < 1 || b.getTenure() > 12) return "tenure not in [1,12]";
        if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.90) return "loanAmount/income > 0.90";
        if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.50) return "(loanAmount/tenure) > income*0.50";
        return null;
    }

    private static String checkDailyRepayment(Borrower b) {
        if (b.getCreditScore() < 730) return "creditScore < 730";
        if (b.getLendenScore() < 770) return "lendenScore < 770";
        if (b.getIncome() < 50000) return "income < 50000";
        if (b.getInterestRate() < 15 || b.getInterestRate() > 40) return "interestRate not in [15,40]";
        if (b.getAge() < 25 || b.getAge() > 60) return "age not in [25,60]";
        if (b.getLoanAmount() > 100000) return "loanAmount > 100000";
        if (b.getTenure() < 1 || b.getTenure() > 12) return "tenure not in [1,12]";
        if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 1.0) return "loanAmount/income > 1.0";
        if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.20) return "(loanAmount/tenure) > income*0.20";
        return null;
    }

    private static String checkMonthlyRepayment(Borrower b) {
        if (!"SALARIED".equalsIgnoreCase(b.getBorrowerType())) return "borrowerType != SALARIED";
        if (b.getCreditScore() < 600) return "creditScore < 600";
        if (b.getLendenScore() < 720) return "lendenScore < 720";
        if (b.getIncome() < 30000) return "income < 30000";
        if (b.getInterestRate() < 18) return "interestRate < 18";
        if (b.getAge() < 20 || b.getAge() > 60) return "age not in [20,60]";
        if (b.getLoanAmount() > 100000) return "loanAmount > 100000";
        if (b.getTenure() < 1 || b.getTenure() > 12) return "tenure not in [1,12]";
        if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.60) return "loanAmount/income > 0.60";
        if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.40) return "(loanAmount/tenure) > income*0.40";
        return null;
    }

    private static String checkRepeatedBusinessLenders(Borrower b) {
        if (!b.isRepeated()) return "not marked repeated";
        if (!"SELF-EMPLOYED".equalsIgnoreCase(b.getBorrowerType())) return "borrowerType != SELF-EMPLOYED";
        if (b.getCreditScore() < 700) return "creditScore < 700 (" + b.getCreditScore() + ")";
        if (b.getLendenScore() < 750) return "lendenScore < 750 (" + b.getLendenScore() + ")";
        if (b.getIncome() < 50000) return "income < 50000 (" + b.getIncome() + ")";
        if (b.getInterestRate() < 18) return "interestRate < 18 (" + b.getInterestRate() + ")";
        if (b.getAge() < 23 || b.getAge() > 60) return "age not in [23,60] (" + b.getAge() + ")";
        if (b.getLoanAmount() > 100000) return "loanAmount > 100000 (" + b.getLoanAmount() + ")";
        if (b.getTenure() < 1 || b.getTenure() > 12) return "tenure not in [1,12] (" + b.getTenure() + ")";
        if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.40) return "loanAmount/income > 0.40";
        if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.30) return "(loanAmount/tenure) > income*0.30";
        return null;
    }

    private static String checkGoodBusinessLenders(Borrower b) {
        if (!"SELF-EMPLOYED".equalsIgnoreCase(b.getBorrowerType())) return "borrowerType != SELF-EMPLOYED";
        if (b.getCreditScore() < 730) return "creditScore < 730 (" + b.getCreditScore() + ")";
        if (b.getLendenScore() < 770) return "lendenScore < 770 (" + b.getLendenScore() + ")";
        if (b.getIncome() < 50000) return "income < 50000 (" + b.getIncome() + ")";
        if (b.getInterestRate() < 18) return "interestRate < 18 (" + b.getInterestRate() + ")";
        if (b.getAge() < 23 || b.getAge() > 60) return "age not in [23,60] (" + b.getAge() + ")";
        if (b.getLoanAmount() > 100000) return "loanAmount > 100000 (" + b.getLoanAmount() + ")";
        if (b.getTenure() < 1 || b.getTenure() > 12) return "tenure not in [1,12] (" + b.getTenure() + ")";
        if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.35) return "loanAmount/income > 0.35";
        if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.25) return "(loanAmount/tenure) > income*0.25";
        return null;
    }

    private static String checkBulkBusinessLenders(Borrower b) {
        if (!"SELF-EMPLOYED".equalsIgnoreCase(b.getBorrowerType())) return "borrowerType != SELF-EMPLOYED";
        if (b.getCreditScore() < 680) return "creditScore < 680 (" + b.getCreditScore() + ")";
        if (b.getLendenScore() < 720) return "lendenScore < 720 (" + b.getLendenScore() + ")";
        if (b.getIncome() < 30000) return "income < 30000 (" + b.getIncome() + ")";
        if (b.getInterestRate() < 18) return "interestRate < 18 (" + b.getInterestRate() + ")";
        if (b.getAge() < 23 || b.getAge() > 60) return "age not in [23,60] (" + b.getAge() + ")";
        if (b.getLoanAmount() > 100000) return "loanAmount > 100000 (" + b.getLoanAmount() + ")";
        if (b.getTenure() < 1 || b.getTenure() > 12) return "tenure not in [1,12] (" + b.getTenure() + ")";
        if ((b.getLoanAmount() * 1.0 / b.getIncome()) > 0.40) return "loanAmount/income > 0.40";
        if ((b.getLoanAmount() * 1.0 / b.getTenure()) > b.getIncome() * 0.30) return "(loanAmount/tenure) > income*0.30";
        return null;
    }
}
