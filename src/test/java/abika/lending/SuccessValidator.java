package abika.lending;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates lending success by checking URL and other indicators
 */
public class SuccessValidator {
    private static final Logger logger = LoggerFactory.getLogger(SuccessValidator.class);

    /**
     * Check if lending was successful by verifying the page URL
     *
     * @param driver WebDriver instance
     * @return true if current URL contains "manual-lending-success"
     */
    public static boolean isLendingSuccessfulByUrl(WebDriver driver) {
        try {
            Thread.sleep(3000);
            String currentUrl = driver.getCurrentUrl();
            boolean isSuccess = currentUrl.contains("manual-lending-success");

            if (isSuccess) {
                logger.info("✅ SUCCESS CONFIRMED: Page URL contains 'manual-lending-success'");
                logger.info("Success Current URL: {}", currentUrl);
            } else {
                logger.warn("⚠️ Current URL does not indicate success: {}", currentUrl);
            }

            return isSuccess;
        } catch (Exception e) {
            logger.error("Failed to verify success by URL: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if lending confirmation page is loaded by verifying the page URL
     *
     * @param driver WebDriver instance
     * @return true if current URL contains "confirm-manual-lending"
     */
    public static boolean isLendingConfirmationPageLoaded(WebDriver driver) {
        try {
            Thread.sleep(200);
            String currentUrl = driver.getCurrentUrl();
            boolean isConfirmPage = currentUrl.contains("confirm-manual-lending");

            if (isConfirmPage) {
                logger.info("✅ CONFIRMATION PAGE LOADED: Page URL contains 'confirm-manual-lending'");
                logger.info("Confirm Current URL: {}", currentUrl);
            } else {
                logger.warn("⚠️ Current URL does not show confirmation page: {}", currentUrl);
            }

            return isConfirmPage;
        } catch (Exception e) {
            logger.error("Failed to verify confirmation page by URL: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate lending completion with loan details
     *
     * @param loanCounts Number of loans that were lent
     * @param totalLendAmount Total amount lent
     * @param walletAmount Remaining wallet amount
     */
    public static void logLendingCompletion(long loanCounts, long totalLendAmount, double walletAmount) {
        logger.info("✅ Lending Successful!");
        logger.info("   - Number of loans: {}", loanCounts);
        logger.info("   - Total Amount: ₹{}", totalLendAmount);
        logger.info("   - Remaining Wallet Balance: ₹{}", walletAmount);
    }
}

