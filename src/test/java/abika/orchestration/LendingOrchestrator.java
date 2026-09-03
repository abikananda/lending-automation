package abika.orchestration;

import abika.selenium.WebDriverWaitManager;
import com.abika.model.Borrower;
import com.abika.model.Investment;
import com.abika.reporting.ExecutionMetrics;
import com.abika.services.DBService;
import com.abika.utils.ConfigReader;
import com.abika.utils.DroolsEngine;
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
            MethodTimer loginCheckTimer = new MethodTimer("Login check and navigation");
            if (!isLoggedIn) {
                LoginService.loginUser(driver, activateUser);
                isLoggedIn = true;
            } else {
                LoginService.clickDashboard(driver);
            }
            loginCheckTimer.end();

            MethodTimer openLoansTimer = new MethodTimer("Open borrowers list");
            openBorrowerList(driver, investment);
            openLoansTimer.end();

            MethodTimer filterTimer = new MethodTimer("Apply filters and sort");
            FilterAndSortService.applyFiltersAndSort(driver, WebDriverWaitManager.getStandardWait());
            filterTimer.end();

            // A missing initial card list is a non-financial page-readiness failure. Reopen the
            // list and reapply filters once. This retry happens before any Add Loan action because
            // BorrowerScraper raises this condition before processing its first borrower.
            MethodTimer scrapeTimer = new MethodTimer("Scrape and process borrowers");
            try {
                BorrowerScraper.scrapeAndProcessBorrowers(
                    driver, dbService, droolsEngine, investment, borrowerList, npaBorrowersInCurrentRun, activateUser, metrics
                );
            } catch (IllegalStateException listLoadFailure) {
                if (!borrowerList.isEmpty() || !isBorrowerListLoadFailure(listLoadFailure)) {
                    throw listLoadFailure;
                }

                logger.warn("Borrower list was not ready for rule '{}'; reopening list and retrying non-financial load once: {}",
                        investment.getRuleName(), listLoadFailure.getMessage());

                LoginService.clickDashboard(driver);
                openBorrowerList(driver, investment);
                FilterAndSortService.applyFiltersAndSort(driver, WebDriverWaitManager.getStandardWait());

                BorrowerScraper.scrapeAndProcessBorrowers(
                    driver, dbService, droolsEngine, investment, borrowerList, npaBorrowersInCurrentRun, activateUser, metrics
                );
            }
            scrapeTimer.end();

            // No eligible/available loans is a normal rule outcome, not a financial failure.
            // Never call the finalizer with amount 0; simply record the rule and continue.
            if (borrowerList.isEmpty()) {
                UIElementHandler.closePopupFast(driver);
                logger.info("No loans selected for rule '{}'; skipping finalization and continuing to the next rule",
                        investment.getRuleName());

                ruleMetrics.setPassed(true);
                ruleMetrics.setBorrowersSelected(0);
                ruleMetrics.setBorrowersFinalized(0);
                ruleMetrics.setAmountLent(0.0);
                ruleMetrics.setExecutionTimeMs(ruleTimer.getElapsedMillis());
                metrics.addRuleMetrics(ruleMetrics);
                return;
            }

            MethodTimer closeBeforeFinalizeTimer = new MethodTimer("Close popup before finalize");
            // closePopupFast already returns normally when no popup exists. Any exception here
            // therefore represents an uncertain popup state and must remain fatal before lending.
            UIElementHandler.closePopupFast(driver);
            logger.info("✅ Popup closed before finalizing lending");
            closeBeforeFinalizeTimer.end();

            MethodTimer finalizeTimer = new MethodTimer("Finalize lending");
            LendingFinalizer.finalizeLending(driver, investment, metrics);
            finalizeTimer.end();

            MethodTimer storeTimer = new MethodTimer("Store borrower data");
            dbService.storeBorrowerList(borrowerList, ConfigReader.get(activateUser + "mobileNumber"));

            logger.info("📊 DEBUG: borrowerList size = {}", borrowerList.size());
            if (borrowerList.isEmpty()) {
                logger.warn("⚠️ WARNING: borrowerList is empty! No borrowers were processed in scraping.");
            }

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
                logger.info("ℹ️  Existing NPA Borrowers encountered: {}", npaBorrowersInCurrentRun);
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
                borrowerList.size(), borrowersFinalized, String.format("%.0f", amountLentInThisRule));

            metrics.addRuleMetrics(ruleMetrics);
            metrics.setTotalBorrowersSelected(metrics.getTotalBorrowersSelected() + borrowerList.size());
            metrics.setTotalBorrowersFinalized(metrics.getTotalBorrowersFinalized() + (borrowersFinalized != null ? borrowersFinalized : 0));

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

    private static void openBorrowerList(WebDriver driver, Investment investment) throws Exception {
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
    }

    private static boolean isBorrowerListLoadFailure(IllegalStateException error) {
        String message = error.getMessage();
        return message != null && (
                message.startsWith("Borrower list did not load") ||
                message.startsWith("Borrower list became empty before any borrower was processed")
        );
    }
}
