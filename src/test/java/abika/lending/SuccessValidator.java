package abika.lending;

import abika.selenium.WebDriverWaitManager;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validates lending navigation states. */
public class SuccessValidator {
    private static final Logger logger = LoggerFactory.getLogger(SuccessValidator.class);

    public static boolean isLendingSuccessfulByUrl(WebDriver driver) {
        try {
            WebDriverWaitManager.getShortWait().until(
                    d -> d.getCurrentUrl().contains("manual-lending-success"));
            String currentUrl = driver.getCurrentUrl();
            logger.info("✅ SUCCESS CONFIRMED: Page URL contains 'manual-lending-success'");
            logger.info("Success Current URL: {}", currentUrl);
            return true;
        } catch (Exception e) {
            logger.warn("⚠️ Lending success URL was not confirmed within the safety wait. Current URL: {}",
                    safeCurrentUrl(driver));
            return false;
        }
    }

    public static boolean isLendingConfirmationPageLoaded(WebDriver driver) {
        try {
            WebDriverWaitManager.getUltraShortWait().until(
                    d -> d.getCurrentUrl().contains("confirm-manual-lending"));
            logger.info("✅ CONFIRMATION PAGE LOADED: {}", driver.getCurrentUrl());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void logLendingCompletion(long loanCounts, long totalLendAmount, double walletAmount) {
        logger.info("✅ Lending Successful!");
        logger.info("   - Number of loans: {}", loanCounts);
        logger.info("   - Total Amount: ₹{}", totalLendAmount);
        logger.info("   - Remaining Wallet Balance: ₹{}", walletAmount);
    }

    private static String safeCurrentUrl(WebDriver driver) {
        try {
            return driver.getCurrentUrl();
        } catch (Exception e) {
            return "<unavailable>";
        }
    }
}
