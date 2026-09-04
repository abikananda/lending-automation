package abika.orchestration;

import abika.MethodTimer;
import abika.auth.LoginService;
import abika.filtering.FilterAndSortService;
import abika.lending.LendingFinalizer;
import abika.scraping.BorrowerScraper;
import abika.selenium.UIElementHandler;
import abika.selenium.WebDriverWaitManager;
import com.abika.model.Borrower;
import com.abika.model.Investment;
import com.abika.reporting.ExecutionMetrics;
import com.abika.services.DBService;
import com.abika.utils.ConfigReader;
import com.abika.utils.DroolsEngine;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs one lending rule as a deterministic state machine:
 * DASHBOARD -> RULE LIST -> SCRAPE -> FINALIZE(optional) -> DASHBOARD.
 */
public class LendingOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(LendingOrchestrator.class);
    private static final By BORROWER_CARD = By.cssSelector("div.MuiBox-root.css-79elbk");
    private static boolean isLoggedIn = false;

    public static void runForARule(
            Investment investment,
            WebDriver driver,
            String activateUser,
            ExecutionMetrics metrics) throws Exception {

        MethodTimer ruleTimer = new MethodTimer("runForARule [" + investment.getRuleName() + "]");
        ExecutionMetrics.RuleMetrics ruleMetrics = new ExecutionMetrics.RuleMetrics(investment.getRuleName());
        DBService dbService = new DBService();
        DroolsEngine droolsEngine = new DroolsEngine();
        List<Borrower> borrowerList = new ArrayList<>();
        List<String> npaBorrowersInCurrentRun = new ArrayList<>();

        investment.setWalletAmountAtRuleStart(investment.getWalletAmount());
        investment.setTotalBorrowersFinalized(0);

        try {
            // Every rule starts from a known state. Never assume the previous rule left the right page.
            if (!isLoggedIn) {
                LoginService.loginUser(driver, activateUser);
                isLoggedIn = true;
            }
            LoginService.ensureDashboard(driver);

            openAndFilterRule(driver, investment);

            // A slow/empty list gets one bounded non-financial reopen before being treated as empty.
            if (!waitForBorrowerCards(driver)) {
                logger.warn("Borrower cards did not appear for '{}'; reopening rule list once",
                        investment.getRuleName());
                LoginService.ensureDashboard(driver);
                openAndFilterRule(driver, investment);
            }

            if (!waitForBorrowerCards(driver)) {
                logger.info("No borrower cards available for rule '{}'. Continuing to next rule.",
                        investment.getRuleName());
                completeZeroSelectionRule(investment, ruleMetrics, metrics, ruleTimer, driver);
                return;
            }

            BorrowerScraper.scrapeAndProcessBorrowers(
                    driver, dbService, droolsEngine, investment, borrowerList,
                    npaBorrowersInCurrentRun, activateUser, metrics);

            // No popup is normal; a close failure is not normal and must stay fatal.
            UIElementHandler.closePopupFast(driver);

            if (investment.getLoanCounts() == 0 || borrowerList.isEmpty()) {
                logger.info("No eligible loans selected for '{}'. Skipping finalization.",
                        investment.getRuleName());
                completeZeroSelectionRule(investment, ruleMetrics, metrics, ruleTimer, driver);
                return;
            }

            LendingFinalizer.finalizeLending(driver, investment, metrics);

            // Only persist selected borrowers after success was confirmed and wallet was deducted.
            dbService.storeBorrowerList(
                    borrowerList, ConfigReader.get(activateUser + "mobileNumber"));

            Double amountLent = investment.getAmountLentInThisRule();
            Integer finalized = investment.getTotalBorrowersFinalized();
            ruleMetrics.setPassed(true);
            ruleMetrics.setBorrowersSelected(borrowerList.size());
            ruleMetrics.setBorrowersFinalized(finalized == null ? 0 : finalized);
            ruleMetrics.setAmountLent(amountLent == null ? 0.0 : amountLent);
            ruleMetrics.setExecutionTimeMs(ruleTimer.getElapsedMillis());
            metrics.addRuleMetrics(ruleMetrics);
            metrics.setTotalBorrowersSelected(metrics.getTotalBorrowersSelected() + borrowerList.size());

            logger.info("📊 Rule Metrics Summary - Selected: {}, Finalized: {}, Amount Lent: ₹{}",
                    borrowerList.size(), finalized == null ? 0 : finalized,
                    String.format("%.0f", amountLent == null ? 0.0 : amountLent));

            // Success page -> dashboard before the next rule.
            LoginService.ensureDashboard(driver);

        } catch (Exception e) {
            logger.error("❌ Rule '{}' failed: {}", investment.getRuleName(), e.getMessage(), e);
            ruleMetrics.setPassed(false);
            ruleMetrics.setFailureReason(e.getMessage());
            ruleMetrics.setExecutionTimeMs(ruleTimer.getElapsedMillis());
            metrics.addRuleMetrics(ruleMetrics);

            // If Add Loan selections are still reserved, do not navigate or start another rule.
            // The UI may contain staged/uncertain financial state.
            double reserved = investment.getReservedAmount() == null ? 0.0 : investment.getReservedAmount();
            if (reserved <= 0.0) {
                try {
                    LoginService.ensureDashboard(driver);
                } catch (Exception navigationError) {
                    e.addSuppressed(navigationError);
                }
            }
            throw e;
        } finally {
            try {
                droolsEngine.dispose();
            } catch (Exception e) {
                logger.debug("Drools dispose failed: {}", e.getMessage());
            }
            ruleTimer.end();
        }
    }

    private static void openAndFilterRule(WebDriver driver, Investment investment) {
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

        FilterAndSortService.applyFiltersAndSort(driver, WebDriverWaitManager.getStandardWait());
    }

    private static boolean waitForBorrowerCards(WebDriver driver) {
        try {
            return WebDriverWaitManager.getShortWait().until(
                    d -> !d.findElements(BORROWER_CARD).isEmpty());
        } catch (Exception e) {
            return false;
        }
    }

    private static void completeZeroSelectionRule(
            Investment investment,
            ExecutionMetrics.RuleMetrics ruleMetrics,
            ExecutionMetrics metrics,
            MethodTimer ruleTimer,
            WebDriver driver) {

        ruleMetrics.setPassed(true);
        ruleMetrics.setBorrowersSelected(0);
        ruleMetrics.setBorrowersFinalized(0);
        ruleMetrics.setAmountLent(0.0);
        ruleMetrics.setExecutionTimeMs(ruleTimer.getElapsedMillis());
        metrics.addRuleMetrics(ruleMetrics);
        investment.setReservedAmount(0.0);
        LoginService.ensureDashboard(driver);
    }
}
