package abika;

import abika.orchestration.LendingOrchestrator;
import abika.setup.BrowserDriverSetup;
import com.abika.model.Investment;
import com.abika.reporting.ExecutionMetrics;
import com.abika.reporting.ReportGenerator;
import com.abika.services.DBService;
import com.abika.utils.ConfigReader;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/** Main entry point for P2P lending automation. */
public class P2PAutomation {
    private static final Logger logger = LoggerFactory.getLogger(P2PAutomation.class);

    public static void main(String[] args) {
        WebDriver driver = BrowserDriverSetup.setupWebDriver();
        BrowserDriverSetup.initializeWaitInstances(driver);
        ExecutionMetrics metrics = new ExecutionMetrics();
        Investment investment = null;

        try {
            String activateUser = ConfigReader.get("activateUser") + ".";
            List<String> rules = ConfigReader.getRuleNames();
            double initialWallet = Double.parseDouble(
                    Objects.requireNonNull(ConfigReader.get("walletAmount")));

            double wallet = initialWallet;
            metrics.setInitialWallet(initialWallet);
            metrics.setTotalRulesExecuted(rules.size());

            for (String configuredRule : rules) {
                String rule = configuredRule.trim();
                System.out.println("Started investment with: " + rule);

                ConfigReader.set("repeatedLoan", String.valueOf(rule.contains("Repeated")));
                ConfigReader.set("low_high_risk_filter",
                        String.valueOf(rule.contains("Low") || rule.contains("Medium")));
                ConfigReader.set("businessFilter", String.valueOf(rule.contains("Business")));

                investment = initInvestment(rule, wallet);

                if (wallet < 250) {
                    logger.info("Wallet balance {} is below minimum lending amount. Stopping remaining rules.", wallet);
                    break;
                }

                try {
                    LendingOrchestrator.runForARule(investment, driver, activateUser, metrics);
                    wallet = investment.getWalletAmount();
                    metrics.setTotalRulesPassed(metrics.getTotalRulesPassed() + 1);
                } catch (Exception e) {
                    metrics.setTotalRulesFailed(metrics.getTotalRulesFailed() + 1);
                    metrics.addError(rule + ": " + e.getMessage());

                    double reserved = investment.getReservedAmount() == null
                            ? 0.0 : investment.getReservedAmount();
                    if (reserved > 0.0) {
                        throw new IllegalStateException(
                                "Aborting remaining rules because '" + rule +
                                        "' has ₹" + reserved +
                                        " of staged selections and the financial state is uncertain",
                                e);
                    }

                    logger.error("Rule '{}' failed before any staged financial state remained; continuing next rule: {}",
                            rule, e.getMessage(), e);
                }

                System.out.println("Completed investment with: " + rule + "\n");
            }

            metrics.setFinalWallet(wallet);
            metrics.setTotalDeducted(initialWallet - wallet);

        } catch (Exception e) {
            logger.error("Fatal error in P2PAutomation: {}", e.getMessage(), e);
            metrics.addError("Fatal error: " + e.getMessage());
            if (investment != null) metrics.setFinalWallet(investment.getWalletAmount());
        } finally {
            try {
                DBService.closeConnectionPool();
            } catch (Exception e) {
                logger.warn("Could not close DB pool cleanly: {}", e.getMessage());
            }

            try {
                metrics.endExecution();
                String reportPath = new ReportGenerator().generateReport(metrics);
                logger.info("📊 Execution report available at: {}", reportPath);
            } catch (Exception e) {
                logger.error("Failed to generate report: {}", e.getMessage());
            }

            if (driver != null) {
                try {
                    driver.quit();
                    logger.info("✅ WebDriver closed successfully");
                } catch (Exception e) {
                    logger.error("Error closing WebDriver: {}", e.getMessage());
                }
            }
        }
    }

    private static Investment initInvestment(String ruleName, double walletAmount) {
        Investment investment = new Investment();
        investment.setWalletAmount(walletAmount);
        investment.setRuleName(ruleName);
        investment.setLendAmtPerLoan(0);
        investment.setLoanCounts(0);
        investment.setReservedAmount(0.0);
        logger.info("Initialized investment with walletAmount={} and ruleName={}",
                walletAmount, ruleName);
        return investment;
    }
}
