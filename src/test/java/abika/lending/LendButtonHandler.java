package abika.lending;

import abika.MethodTimer;
import abika.selenium.WebDriverWaitManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Locates and clicks Continue exactly once.
 */
public class LendButtonHandler {
    private static final Logger logger = LoggerFactory.getLogger(LendButtonHandler.class);

    public static boolean findAndClickLendButton(WebDriver driver) {
        MethodTimer timer = new MethodTimer("Find and click Continue button");
        try {
            WebElement continueBtn = WebDriverWaitManager.getShortWait().until(
                    ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[contains(normalize-space(), 'Continue')]")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", continueBtn);
            logger.info("✅ Clicked Continue button");
            return true;
        } catch (Exception e) {
            logger.error("Continue button was not safely clickable: {}", e.getMessage());
            return false;
        } finally {
            timer.end();
        }
    }
}
