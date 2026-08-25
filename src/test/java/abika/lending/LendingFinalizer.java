package abika.lending;

import com.abika.model.Investment;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import abika.MethodTimer;
import abika.selenium.UIElementHandler;
import abika.selenium.WebDriverWaitManager;

/**
 * Orchestrates the lending finalization process
 * Delegates specific tasks to specialized handlers:
 * - SliderHandler: Adjusts lending amount slider
 * - LendButtonHandler: Finds and clicks the Lend button
 * - SuccessValidator: Validates lending success
 */
public class LendingFinalizer {
    private static final Logger logger = LoggerFactory.getLogger(LendingFinalizer.class);

    /**
     * Finalize lending by adjusting slider and clicking Lend button
     *
     * @param driver     WebDriver instance
     * @param investment Investment details including lending amount and count
     */
    public static void finalizeLending(WebDriver driver, Investment investment, com.abika.reporting.ExecutionMetrics metrics) {
        MethodTimer overallTimer = new MethodTimer("finalizeLending");

        logger.info("Finalizing lending with amount: {}", investment.getLendAmtPerLoan());
        if (investment.getLendAmtPerLoan() <= 0 || investment.getLoanCounts() == 0) {
            logger.info("No loans to finalize.");
            // Click back arrow to return to dashboard
            clickBackArrowToDashboard(driver);
            overallTimer.end();
            throw new RuntimeException("No loans to finalize");
        }

        try {

            // Step 2: Adjust slider to target lending amount
            int targetAmount = investment.getLendAmtPerLoan();
            boolean sliderAdjusted = SliderHandler.adjustSlider(driver, targetAmount);
            if (!sliderAdjusted) {
                logger.error("❌ Slider adjustment failed - target value not reached");
                clickBackArrowToDashboard(driver);
                overallTimer.end();
                throw new RuntimeException("Slider adjustment failed: could not set lending amount to " + targetAmount);
            }

            // Step 6: Find and click the Lend button
            boolean lendButtonFound = LendButtonHandler.findAndClickLendButton(driver);
            if (!lendButtonFound) {
                logger.info("No lend button found to finalize lending.");
                // Click back arrow to return to dashboard
                clickBackArrowToDashboard(driver);
                overallTimer.end();
                throw new RuntimeException("No lend button found to finalize lending.");
            }
            // Step 4: Calculate total lending amount
            long totalLendAmount = (long) investment.getLendAmtPerLoan() * investment.getLoanCounts();
            // Step 7: Validate success
            validateAndLogResult(driver, investment, lendButtonFound, totalLendAmount, metrics);

            overallTimer.end();
        } catch (Exception e) {
            logger.error("Error while finalizing lending: {}", e.getMessage(), e);
            // Ensure reserved amount is cleared on unexpected failure to avoid blocking future runs
            try {
                if (investment != null) investment.setReservedAmount(0.0);
            } catch (Exception ex) {
                logger.warn("Failed to clear reserved amount: {}", ex.getMessage());
            }
            overallTimer.end();
            throw e; // rethrow to propagate failure to orchestrator
        }
    }

    /**
     * Validate lending success and log results
     *
     * @param driver          WebDriver instance
     * @param investment      Investment details
     * @param lendButtonFound Whether Lend button was successfully clicked
     * @param totalLendAmount Total amount lent
     */
    private static void validateAndLogResult(WebDriver driver, Investment investment,
                                             boolean lendButtonFound, long totalLendAmount, com.abika.reporting.ExecutionMetrics metrics) {
        if (!lendButtonFound) {
            logger.warn("❌ Could not locate Continue button after multiple attempts");
            return;
        }

        // Verify success by checking URL
        if (SuccessValidator.isLendingSuccessfulByUrl(driver)) {
            // Deduct actual amount from wallet only after successful lending
            double previousWallet = investment.getWalletAmount();
            double deducted = (double) totalLendAmount;
            investment.setWalletAmount(previousWallet - deducted);
            
            // Track successful finalization - set to actual count of loans finalized
            investment.setTotalBorrowersFinalized(investment.getLoanCounts());
            
            // Clear reserved amount since loans are finalized
            investment.setReservedAmount(0.0);

            SuccessValidator.logLendingCompletion(investment.getLoanCounts(), totalLendAmount,
                    investment.getWalletAmount());

            // Update metrics: mark the last N selected borrowers for this rule as FINALIZED
            if (metrics != null) {
                int toFinalize = investment.getLoanCounts();
                int finalizedSoFar = 0;
                for (int idx = metrics.getBorrowerRecords().size() - 1; idx >= 0 && finalizedSoFar < toFinalize; idx--) {
                    com.abika.reporting.ExecutionMetrics.BorrowerRecord r = metrics.getBorrowerRecords().get(idx);
                    if (r.getRuleName().equals(investment.getRuleName()) && "SELECTED".equals(r.getStatus())) {
                        r.setStatus("FINALIZED");
                        r.setFinalizationTimeMs(System.currentTimeMillis());
                        finalizedSoFar++;
                    }
                }
                metrics.setTotalBorrowersFinalized(metrics.getTotalBorrowersFinalized() + finalizedSoFar);
            }
        } else {
            logger.warn("⚠️ Lend button clicked, but URL does not confirm success");
            logger.error("❌ Lending finalization failed - Total Amount Attempted: ₹{}, Loan Count: {}", totalLendAmount, investment.getLoanCounts());
            // Release reserved amounts so future runs are not blocked
            investment.setReservedAmount(0.0);
            throw new RuntimeException("Lending finalization failed: URL validation did not confirm success");
        }
    }

    /**
     * Click the back arrow icon to return to dashboard
     */
    private static void clickBackArrowToDashboard(WebDriver driver) {
        MethodTimer backArrowTimer = new MethodTimer("Click back arrow to dashboard");
        try {
            WebElement backArrow = WebDriverWaitManager.getStandardWait().until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//img[@alt='Arrow-icon']"))
            );
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", backArrow);
            logger.info("✅ Clicked back arrow icon to return to dashboard");
        } catch (Exception e) {
            logger.warn("⚠️ Could not click back arrow: {}", e.getMessage());
        } finally {
            backArrowTimer.end();
        }
    }
}

