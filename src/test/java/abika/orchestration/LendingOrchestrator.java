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

        // Capture wallet amount at start of this rule for accurate tracking
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

            // Step 2: Open the appropriate borrower list
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

            // Step 3: Apply filters and sorting
            MethodTimer filterTimer = new MethodTimer("Apply filters and sort");
            FilterAndSortService.applyFiltersAndSort(driver, WebDriverWaitManager.getStandardWait());
            filterTimer.end();

            // Step 4: Scrape borrowers and apply rules
            MethodTimer scrapeTimer = new MethodTimer("Scrape and process borrowers");
            BorrowerScraper.scrapeAndProcessBorrowers(
                driver, dbService, droolsEngine, investment, borrowerList, npaBorrowersInCurrentRun, activateUser, metrics
            );
            scrapeTimer.end();

            // Step 5: Close any open popups before finalizing
            MethodTimer closeBeforeFinalizeTimer = new MethodTimer("Close popup before finalize");
            try {
                UIElementHandler.closePopupFast(driver);
                logger.info("✅ Popup closed before finalizing lending");
            } catch (Exception e) {
                logger.info("ℹ️  No popup to close before finalize: {}", e.getMessage());
            }
            closeBeforeFinalizeTimer.end();

            // Step 6: Finalize lending (adjust slider and click lend button)
            MethodTimer finalizeTimer = new MethodTimer("Finalize lending");
            LendingFinalizer.finalizeLending(driver, investment, metrics);
            finalizeTimer.end();

            // Step 7: Store borrower data in database
            MethodTimer storeTimer = new MethodTimer("Store borrower data");
            dbService.storeBorrowerList(borrowerList, ConfigReader.get(activateUser + "mobileNumber"));
            
            // DEBUG: Log borrower list details
            logger.info("📊 DEBUG: borrowerList size = {}", borrowerList.size());
            if (borrowerList.isEmpty()) {
                logger.warn("⚠️ WARNING: borrowerList is empty! No borrowers were processed in scraping.");
            }
            
            List<String> nonNpaBorrowerNames = dbService.getNonNPABorrowers();
            // DEBUG: Log non-NPA borrowers from database
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
            
            // Better logging with context
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
            
            // Better logging for NPA borrowers
            if (npaBorrowersInCurrentRun.isEmpty()) {
                logger.warn("⚠️ No NPA borrowers encountered during this lending run");
            } else {
                logger.info("ℹ️  Existing NPA Borrowers encountered: {}", npaBorrowersInCurrentRun);
            }
            
            storeTimer.end();

            // Update metrics for this rule
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
            
            // Update metrics on failure
            ruleMetrics.setPassed(false);
            ruleMetrics.setFailureReason(e.getMessage());
            ruleMetrics.setExecutionTimeMs(ruleTimer.getElapsedMillis());
            metrics.addRuleMetrics(ruleMetrics);
            
            // Dispose resources on error
            try {
                droolsEngine.dispose();
            } catch (Exception ex) {
                logger.warn("Failed to dispose Drools engine: {}", ex.getMessage());
            }
            // Rethrow exception so main process knows this rule failed
            throw e;
        } finally {
            ruleTimer.end();
        }
    }
}

