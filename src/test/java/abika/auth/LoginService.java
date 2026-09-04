package abika.auth;

import com.abika.services.LendenClubOtpReader;
import com.abika.utils.ConfigReader;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import abika.MethodTimer;
import abika.selenium.UIElementHandler;
import abika.selenium.WebDriverWaitManager;

import java.util.List;

/**
 * Handles authentication and navigation between lending rule screens.
 */
public class LoginService {
    private static final Logger logger = LoggerFactory.getLogger(LoginService.class);
    private static final By HOME = By.id("home");
    private static final By BACK_ARROW = By.xpath("//img[@alt='Arrow-icon']");

    public static void loginUser(WebDriver driver, String activateUser) throws Exception {
        MethodTimer overallTimer = new MethodTimer("loginUser");
        driver.get("https://app.lendenclub.com/login");
        driver.findElement(By.name("phone")).sendKeys(ConfigReader.get(activateUser + "mobileNumber"));
        UIElementHandler.safeClick(driver, driver.findElement(By.xpath("//div[text()='Send OTP']")));

        logger.info("Enter the OTP: ");
        String otp = LendenClubOtpReader.getOtpFromEmail(
                ConfigReader.get(activateUser + "email.username"),
                ConfigReader.get(activateUser + "email.password"));
        driver.findElement(By.id("otp")).sendKeys(otp);
        UIElementHandler.safeClick(driver,
                driver.findElement(By.xpath("//button[@type='submit' and normalize-space()='Verify OTP']")));
        WebDriverWaitManager.getStandardWait().until(d -> d.getCurrentUrl().contains("manual-lending"));
        logger.info("✅ Login Successful!");
        overallTimer.end();
    }

    /**
     * Put the browser into a known dashboard state. This is a bounded, non-financial recovery:
     * click Home when available; otherwise use the list/success-page back arrow; otherwise use
     * one browser back navigation. The method never loops indefinitely and fails if Home still
     * cannot be observed.
     */
    public static void ensureDashboard(WebDriver driver) {
        if (isHomeAvailable(driver)) {
            clickHomeIfNeeded(driver);
            waitForHome(driver);
            return;
        }

        List<WebElement> backArrows = driver.findElements(BACK_ARROW);
        if (!backArrows.isEmpty() && backArrows.get(0).isDisplayed()) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", backArrows.get(0));
            if (waitForHomeQuietly(driver)) return;
        }

        driver.navigate().back();
        if (waitForHomeQuietly(driver)) return;

        throw new IllegalStateException("Could not restore dashboard state before starting the next lending rule");
    }

    public static void clickDashboard(WebDriver driver) {
        ensureDashboard(driver);
    }

    public static void openLiveLoans(WebDriver driver) {
        ensureDashboard(driver);
        UIElementHandler.safeClick(driver, driver.findElement(By.id("live-loans")));
    }

    public static void openRepeatedBorrowers(WebDriver driver) {
        ensureDashboard(driver);
        clickRuleImage(driver, "https://ldc-prod-cms.lendenclub.com/repeat-loans-icon.png");
    }

    public static void fillingFastLoans(WebDriver driver) {
        ensureDashboard(driver);
        clickRuleImage(driver, "https://ldc-prod-cms.lendenclub.com/filling-fast-icon.png");
    }

    public static void openDailyRepaymentLoans(WebDriver driver) {
        ensureDashboard(driver);
        clickRuleImage(driver, "https://ldc-prod-cms.lendenclub.com/daily-repayment-icon.png");
    }

    public static void openMonthlyRepaymentLoans(WebDriver driver) {
        ensureDashboard(driver);
        clickRuleImage(driver, "https://ldc-prod-cms.lendenclub.com/monthly-repayment-icon.png");
    }

    private static void clickRuleImage(WebDriver driver, String src) {
        WebElement img = WebDriverWaitManager.getStandardWait().until(
                d -> d.findElement(By.xpath("//img[@src='" + src + "']")));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", img);
    }

    private static boolean isHomeAvailable(WebDriver driver) {
        try {
            for (WebElement element : driver.findElements(HOME)) {
                if (element.isDisplayed()) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void clickHomeIfNeeded(WebDriver driver) {
        List<WebElement> homes = driver.findElements(HOME);
        if (!homes.isEmpty() && homes.get(0).isDisplayed()) {
            UIElementHandler.safeClick(driver, homes.get(0));
        }
    }

    private static void waitForHome(WebDriver driver) {
        WebDriverWaitManager.getStandardWait().until(d -> isHomeAvailable(d));
    }

    private static boolean waitForHomeQuietly(WebDriver driver) {
        try {
            WebDriverWaitManager.getStandardWait().until(d -> isHomeAvailable(d));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
