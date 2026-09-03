package abika.orchestration;

import abika.selenium.WebDriverWaitManager;
import com.abika.model.Borrower;
import com.abika.model.Investment;
import com.abika.reporting.ExecutionMetrics;
import com.abika.services.DBService;
import com.abika.utils.ConfigReader;
import com.abika.utils.DroolsEngine;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import abika.MethodTimer;
import abika.auth.LoginService;
import abika.filtering.FilterAndSortService;
import abika.lending.LendingFinalizer;
import abika.scraping.BorrowerScraper;
import abika.selenium.UIElementHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Orchestrates the complete lending workflow for a single rule
 * Coordinates: login, borrower opening, filtering, scraping, and finalization
 */
public class LendingOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(LendingOrchestrator.class);
    private static final By BORROWER_CARD = By.cssSelector("div.MuiBox-root.css-79elbk");

    private static boolean isLoggedIn = false;

    /**
     * Execute complete lending flow for a given investment rule
     */
    public static void runForARule(Investment investment, WebDriver driver, String activateUser, ExecutionMetrics metrics) throws Exception {
        MethodTimer ruleTimer = new MethodTimer("runForARule [" + investment.getRuleName() + "]");
        ExecutionMetrics.RuleMetrics ruleMetrics = new ExecutionMetrics.RuleMetrics(investment.getRuleName());

        investment.setWalletAmountAtRuleStart(investment.getWalletAmount());
        investment.setTotalBorrowersFinalized(0);

        DBService dbService = new DBService();
        DroolsEngine droolsEngine = new DroolsEngine();
        List<Borrower> borrowerList = new ArrayList<>();
        List<Borrower> trustedBorrowerList = new ArrayList<>();
        List<String> npaBorrowersInCurrentRun = new ArrayList<>();

        try {
            // Step 1: Login if not already logged in
            MethodTimer loginCheckTimer = new MethodTimer("Login check and navigation");
            if (!isLoggedIn) {
                LoginService.loginUser(driver, activateUser);
                isLoggedIn = true;
            } else {
                LoginService.clickDashboard(driver);
            }
            loginCheckTimer.end();

            // Step 2 + 3: Open borrower list, apply filters, and verify that cards actually loaded.
            openBorrowerList(driver, investment);
            FilterAndSortService.applyFiltersAndSort(driver, WebDriverWaitManager.getStandardWait());

            if (!waitForBorrowerCards(driver)) {
                // This is a non-financial navigation retry. No borrower popup or Add Loan action
                // has occurred yet, so it is safe to return to dashboard and reopen the list once.
                logger.warn("⚠️ Borrower cards did not load for '{}'; reopening the list once before skipping the rule",
                        investment.getRuleName());

                LoginService.clickDashboard(driver);
                openBorrowerList(driver, investment);
                FilterAndSortService.applyFiltersAndSort(driver, WebDriverWaitManager.getStandardWait());

                if (!waitForBorrowerCards(driver)) {
                    String reason = "Borrower list did not load after one safe navigation retry";
                    logger.error("❌ {} for rule '{}'. Skipping this rule so later rules can still run.",
                            reason, investment.getRuleName());
                    recordSkippedRule(ruleMetrics, metrics, ruleTimer, reason);
                    return;
                }
            }

            // Step 4: Scrape borrowers and apply rules
            MethodTimer scrapeTimer = new MethodTimer("Scrape and process borrowers");
            BorrowerScraper.scrapeAndProcessBorrowers(
                    driver, dbService, droolsEngine, investment, borrowerList,
                    npaBorrowersInCurrentRun, activateUser, metrics
            );
            scrapeTimer.end();

            // Step 5: close any open popup before finalization.
            // closePopupFast already returns normally when no popup exists. Any exception here is
            // therefore a real uncertain popup state and must remain fatal rather than be swallowed.
            MethodTimer closeBeforeFinalizeTimer = new MethodTimer("Close popup before finalize");
            UIElementHandler.closePopupFast(driver);
            logger.info("✅ Popup state confirmed safe before finalizing lending");
            closeBeforeFinalizeTimer.end();

            // A rule that selected no loans is a normal outcome, not a finalization failure.
            // Do not enter slider/Continue flow and do not convert it into a fatal all-rules abort.
            if (borrowerList.isEmpty() || investment.getLoanCounts() == 0) {
                logger.info("ℹ️ No loans selected for rule '{}'. Skipping finalization and continuing to the next rule.",
                        investment.getRuleName());

                ruleMetrics.setPassed(true);
                ruleMetrics.setBorrowersSelected(0);
                ruleMetrics.setBorrowersFinalized(0);
                ruleMetrics.setAmountLent(0.0);
                ruleMetrics.setExecutionTimeMs(ruleTimer.getElapsedMillis());
                metrics.addRuleMetrics(ruleMetrics);
                return;
            }

            // Step 6: Finalize lending only when at least one Add Loan selection was recorded.
            MethodTimer finalizeTimer = new MethodTimer("Finalize lending");
            LendingFinalizer.finalizeLending(driver, investment, metrics);
            finalizeTimer.end();

            // Step 7: Store borrower data in database
            MethodTimer storeTimer = new MethodTimer("Store borrower data");
            dbService.storeBorrowerList(borrowerList, ConfigReader.get(activateUser + "mobileNumber"));

            logger.info("📊 DEBUG: borrowerList size = {}", borrowerList.size());

            List<String> nonNpaBorrowerNames = dbService.getNonNPABorrowers();
            logger.info("📊 DEBUG: nonNpaBorrowerNames from DB size = {}", nonNpaBorrowerNames.size());
            if (nonNpaBorrowerNames.isEmpty()) {
                logger.warn("⚠️ WARNING: nonNpaBorrowerNames is empty! Check DB query criteria or data.");
            } else {
                logger.debug("📊 DEBUG: Sample non-NPA borrowers: {}", nonNpaBorrowerNames.stream().limit(5).toList());
            }

            StringJoiner joiner = new StringJoiner(",", "[", "]");
            for (Borrower borrower : borrowerList) {
                String name = borrower.getName();
                if (name != null && nonNpaBorrowerNames.contains(name.trim())) {
                    joiner.add(name);
                    trustedBorrowerList.add(borrower);
                }
            }

            if (trustedBorrowerList.isEmpty()) {
                logger.warn("⚠️ No matched non-NPA borrowers. Possible reasons:");
                logger.warn("   - borrowerList size: {}", borrowerList.size());
                logger.warn("   - nonNpaBorrowerNames size: {}", nonNpaBorrowerNames.size());
                if (!borrowerList.isEmpty() && !nonNpaBorrowerNames.isEmpty()) {
                    logger.warn("   - Name mismatch detected between scraped and DB borrowers");
                    logger.debug("   - Scraped names: {}", borrowerList.stream().map(Borrower::getName).toList());
                    logger.debug("   - DB names: {}", nonNpaBorrowerNames);
                }
            } else {
                logger.info("✅ Matched non-NPA borrowers: {}", joiner);
            }

            dbService.storeTrustedBorrowerList(trustedBorrowerList);

            if (npaBorrowersInCurrentRun.isEmpty()) {
                logger.warn("⚠️ No NPA borrowers encountered during this lending run");
            } else {
                logger.info("ℹ️ Existing NPA Borrowers encountered: {}", npaBorrowersInCurrentRun);
            }

            storeTimer.end();

            Double amountLentInThisRule = investment.getAmountLentInThisRule();
            Integer borrowersFinalized = investment.getTotalBorrowersFinalized();

            ruleMetrics.setPassed(true);
            ruleMetrics.setBorrowersSelected(borrowerList.size());
            ruleMetrics.setBorrowersFinalized(borrowersFinalized != null ? borrowersFinalized : 0);
            ruleMetrics.setAmountLent(amountLentInThisRule != null ? amountLentInThisRule : 0.0);
            ruleMetrics.setExecutionTimeMs(ruleTimer.getElapsedMillis());

            logger.info("📊 Rule Metrics Summary - Selected: {}, Finalized: {}, Amount Lent: ₹{}",
                    borrowerList.size(), borrowersFinalized,
                    String.format("%.0f", amountLentInThisRule != null ? amountLentInThisRule : 0.0));

            metrics.addRuleMetrics(ruleMetrics);
            metrics.setTotalBorrowersSelected(metrics.getTotalBorrowersSelected() + borrowerList.size());
            metrics.setTotalBorrowersFinalized(metrics.getTotalBorrowersFinalized()
                    + (borrowersFinalized != null ? borrowersFinalized : 0));

        } catch (Exception e) {
            logger.error("❌ Exception occurred during lending rule execution: {}", e.getMessage(), e);

            ruleMetrics.setPassed(false);
            ruleMetrics.setFailureReason(e.getMessage());
            ruleMetrics.setExecutionTimeMs(ruleTimer.getElapsedMillis());
            metrics.addRuleMetrics(ruleMetrics);

            try {
                droolsEngine.dispose();
            } catch (Exception ex) {
                logger.warn("Failed to dispose Drools engine: {}", ex.getMessage());
            }
            throw e;
        } finally {
            ruleTimer.end();
        }
    }

    private static void openBorrowerList(WebDriver driver, Investment investment) {
        MethodTimer openLoansTimer = new MethodTimer("Open borrowers list");
        if (Boolean.parseBoolean(ConfigReader.get("repeatedLoan"))) {
            LoginService.openRepeatedBorrowers(driver);
        } else if (investment.getRuleName().contains("Filling Fast")) {
            LoginService.fillingFastLoans(driver);
        } else if (investment.getRuleName().contains("Daily Repayment")) {
            LoginService.openDailyRepaymentLoans(driver);
            ConfigReader.set("businessFilter", "true");
        } else if (investment.getRuleName().contains("Monthly Repayment")) {
            LoginService.openMonthlyRepaymentLoans(driver);
        } else {
            LoginService.openLiveLoans(driver);
        }
        openLoansTimer.end();
    }

    private static boolean waitForBorrowerCards(WebDriver driver) {
        try {
            return WebDriverWaitManager.getStandardWait().until(
                    currentDriver -> !currentDriver.findElements(BORROWER_CARD).isEmpty()
            );
        } catch (TimeoutException timeout) {
            return false;
        }
    }

    private static void recordSkippedRule(
            ExecutionMetrics.RuleMetrics ruleMetrics,
            ExecutionMetrics metrics,
            MethodTimer ruleTimer,
            String reason) {
        ruleMetrics.setPassed(false);
        ruleMetrics.setFailureReason(reason);
        ruleMetrics.setBorrowersSelected(0);
        ruleMetrics.setBorrowersFinalized(0);
        ruleMetrics.setAmountLent(0.0);
        ruleMetrics.setExecutionTimeMs(ruleTimer.getElapsedMillis());
        metrics.addRuleMetrics(ruleMetrics);
    }
}
