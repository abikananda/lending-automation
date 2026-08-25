package com.abika.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class Borrower {
    private String loanId;
    private double creditScore;
    private double lendenScore;
    private double income;
    private double loanAmount;
    private String borrowerType;
    private double interestRate;
    private String name;
    private int age;
    private double lendingAmount;
    private int tenure;
    private boolean trusted;
    private boolean repeated;

    public double getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(double creditScore) {
        this.creditScore = creditScore;
    }

    public double getLendenScore() {
        return lendenScore;
    }

    public void setLendenScore(double lendenScore) {
        this.lendenScore = lendenScore;
    }

    public double getIncome() {
        return income;
    }

    public void setIncome(double income) {
        this.income = income;
    }

    public double getLoanAmount() {
        return loanAmount;
    }

    public void setLoanAmount(double loanAmount) {
        this.loanAmount = loanAmount;
    }

    public String getBorrowerType() {
        return borrowerType;
    }

    public void setBorrowerType(String borrowerType) {
        this.borrowerType = borrowerType;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(double interestRate) {
        this.interestRate = interestRate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public double getLendingAmount() {
        return lendingAmount;
    }

    public void setLendingAmount(double lendingAmount) {
        this.lendingAmount = lendingAmount;
    }

    public int getTenure() {
        return tenure;
    }

    public void setTenure(int tenure) {
        this.tenure = tenure;
    }

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }

    public boolean isTrusted() {
        return trusted;
    }

    public void setTrusted(boolean trusted) {
        this.trusted = trusted;
    }

    public boolean isRepeated() {
        return repeated;
    }

    public void setRepeated(boolean repeated) {
        this.repeated = repeated;
    }

    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Unable to serialize Borrower\"}";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Borrower borrower = (Borrower) o;
        return name != null && name.equals(borrower.name) &&
                loanId != null && loanId.equals(borrower.loanId);
    }

    @Override
    public int hashCode() {
        int result = loanId != null ? loanId.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}

