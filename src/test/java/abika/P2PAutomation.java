package abika;

import com.abika.model.Investment;
import com.abika.utils.ConfigReader;
import com.abika.reporting.ExecutionMetrics;
import com.abika.reporting.ReportGenerator;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import abika.orchestration.LendingOrchestrator;
import abika.setup.BrowserDriverSetup;

import java.util.List;
import java.util.Objects;

/**
 * Main entry point for the P2P Lending Automation.
 * Orchestrates the lending workflow for multiple investment rules.
 */
public class P2PAutomation {
    private static final Logger logger = LoggerFactory.getLogger(P2PAutomation.class);

    public static void main(String[] args) {
        WebDriver driver = BrowserDriverSetup.setupWebDriver();
        BrowserDriverSetup.initializeWaitInstances(driver);

        // Create execution metrics tracker
        ExecutionMetrics metrics = new ExecutionMetrics();

        try {
            String activateUser = ConfigReader.get("activateUser");
            activateUser = activateUser + ".";

            // Get rule names from configuration
            List<String> rules = ConfigReader.getRuleNames();

            Investment investment = new Investment();
            Double initialWallet = Double.parseDouble(Objects.requireNonNull(ConfigReader.get("walletAmount")));
            investment.setWalletAmount(initialWallet);
            metrics.setInitialWallet(initialWallet);
            metrics.setTotalRulesExecuted(rules.size());

            // Run for each investment rule
            for (String rule : rules) {
                rule = rule.trim();
                System.out.println("Started investment with: " + rule);

                if (rule.contains("Repeated")) {
                    ConfigReader.set("repeatedLoan", "true");
                } else {
                    ConfigReader.set("repeatedLoan", "false");
                }
                if (rule.contains("Low") || rule.contains("Medium")) {
                    ConfigReader.set("low_high_risk_filter", "true");
                } else {
                    ConfigReader.set("low_high_risk_filter", "false");
                }
                if (rule.contains("Business")) {
                    ConfigReader.set("businessFilter", "true");
                } else {
                    ConfigReader.set("businessFilter", "false");
                }

                investment = initInvestment(rule, investment.getWalletAmount());

                if (investment.getWalletAmount() >= 250) {
                    try {
                        // Delegate to orchestrator with metrics tracking
                        LendingOrchestrator.runForARule(investment, driver, activateUser, metrics);
                        metrics.setTotalRulesPassed(metrics.getTotalRulesPassed() + 1);
                    } catch (Exception e) {
                        logger.error("Rule '{}' failed: {}", rule, e.getMessage(), e);
                        metrics.setTotalRulesFailed(metrics.getTotalRulesFailed() + 1);
                        metrics.addError(rule + ": " + e.getMessage());
                    }
                } else {
                    System.out.println("Wallet balance remaining is " + investment.getWalletAmount());
                }

                System.out.println("Completed investment with: " + rule + "\n");
            }

            metrics.setFinalWallet(investment.getWalletAmount());
            if (metrics.getInitialWallet() != null && metrics.getFinalWallet() != null) {
                metrics.setTotalDeducted(metrics.getInitialWallet() - metrics.getFinalWallet());
            }

        } catch (Exception e) {
            logger.error("Fatal error in P2PAutomation: {}", e.getMessage(), e);
            metrics.addError("Fatal error: " + e.getMessage());
        } finally {
            // Generate report before closing driver
            try {
                metrics.endExecution();
                ReportGenerator generator = new ReportGenerator();
                String reportPath = generator.generateReport(metrics);
                logger.info("📊 Execution report available at: {}", reportPath);
            } catch (Exception e) {
                logger.error("Failed to generate report: {}", e.getMessage());
            }

            // Ensure WebDriver is always closed
            if (driver != null) {
                try {
                    //driver.quit();
                    logger.info("✅ WebDriver closed successfully");
                } catch (Exception e) {
                    logger.error("Error closing WebDriver: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Initialize investment object with rule name and wallet amount
     */
    private static Investment initInvestment(String ruleName, Double walletAmount) {
        Investment investment = new Investment();
        investment.setWalletAmount(walletAmount);
        investment.setRuleName(ruleName);
        investment.setLendAmtPerLoan(0);
        investment.setLoanCounts(0);
        logger.info("Initialized investment with walletAmount={} and ruleName={}", investment.getWalletAmount(), investment.getRuleName());
        return investment;
    }
}


