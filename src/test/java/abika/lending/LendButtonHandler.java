package abika.lending;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import abika.MethodTimer;
import abika.selenium.WebDriverWaitManager;


/**
 * Handles finding and clicking the Lend button with various fallback strategies
 */
public class LendButtonHandler {
    private static final Logger logger = LoggerFactory.getLogger(LendButtonHandler.class);

    /**
     * Find and click the Lend button, matching by total amount if possible
     *
     * @param driver WebDriver instance
     * @return true if Lend button was clicked successfully
     */
    public static boolean findAndClickLendButton(WebDriver driver) {
        MethodTimer lendSearchTimer = new MethodTimer("Find and click Lend button");
        try {
            // Click Continue button first
            try {
                Thread.sleep(2000);
                WebElement continueBtn = WebDriverWaitManager.getStandardWait().until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(normalize-space(), 'Continue')]"))
                );
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", continueBtn);
                logger.info("✅ Clicked Continue button");
                return true;
            } catch (Exception e) {
                logger.info("Continue button not found or already passed: {}", e.getMessage());
            }

            logger.warn("Could not locate Continue button");
            lendSearchTimer.end();
            return false;

        } catch (Exception e) {
            logger.error("Error finding Continue button: {}", e.getMessage());
            lendSearchTimer.end();
            return false;
        }
    }
}

