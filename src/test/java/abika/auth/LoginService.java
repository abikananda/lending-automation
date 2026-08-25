package abika.auth;

import com.abika.services.LendenClubOtpReader;
import com.abika.utils.ConfigReader;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import abika.MethodTimer;
import abika.selenium.UIElementHandler;
import abika.selenium.WebDriverWaitManager;

/**
 * Handles user authentication and login flow
 */
public class LoginService {
    private static final Logger logger = LoggerFactory.getLogger(LoginService.class);

    /**
     * Perform complete login flow: phone entry, OTP request, OTP entry, and verification
     */
    public static void loginUser(WebDriver driver, String activateUser) throws Exception {
        MethodTimer overallTimer = new MethodTimer("loginUser");

        MethodTimer navTimer = new MethodTimer("Navigate to login page");
        driver.get("https://app.lendenclub.com/login");
        navTimer.end();

        MethodTimer phoneTimer = new MethodTimer("Enter phone number");
        driver.findElement(By.name("phone")).sendKeys(ConfigReader.get(activateUser + "mobileNumber"));
        phoneTimer.end();

        MethodTimer otpTimer = new MethodTimer("Click Send OTP");
        UIElementHandler.safeClick(driver, driver.findElement(By.xpath("//div[text()='Send OTP']")));
        otpTimer.end();

        logger.info("Enter the OTP: ");
        MethodTimer emailTimer = new MethodTimer("Read OTP from email");
        String otp = LendenClubOtpReader.getOtpFromEmail(
            ConfigReader.get(activateUser + "email.username"),
            ConfigReader.get(activateUser + "email.password")
        );
        emailTimer.end();

        MethodTimer otpInputTimer = new MethodTimer("Enter OTP");
        driver.findElement(By.id("otp")).sendKeys(otp);
        otpInputTimer.end();

        MethodTimer verifyTimer = new MethodTimer("Verify OTP");
        UIElementHandler.safeClick(driver, driver.findElement(By.xpath("//button[@type='submit' and normalize-space()='Verify OTP']")));
        WebDriverWaitManager.getStandardWait().until(d -> d.getCurrentUrl().contains("manual-lending"));
        verifyTimer.end();

        logger.info("✅ Login Successful!");
        overallTimer.end();
    }

    /**
     * Navigate to dashboard
     */
    public static void clickDashboard(WebDriver driver) {
        //By dashboardLink = By.xpath("//a[contains(@class, 'nav-link') and contains(normalize-space(.), 'Dashboard')]");
        UIElementHandler.safeClick(driver, driver.findElement(By.id("home")));
    }

    /**
     * Navigate to live loans section
     */
    public static void openLiveLoans(WebDriver driver) {
        //By liveLoanLink = By.xpath("//a[contains(@class, 'nav-link') and contains(normalize-space(.), 'Live Loans')]");
        UIElementHandler.safeClick(driver, driver.findElement(By.id("live-loans")));
    }

    /**
     * Navigate to repeated borrowers section
     */
    public static void openRepeatedBorrowers(WebDriver driver) {
        UIElementHandler.safeClick(driver, driver.findElement(By.id("home")));
        org.openqa.selenium.WebElement img = driver.findElement(By.xpath("//img[@src='https://ldc-prod-cms.lendenclub.com/repeat-loans-icon.png']"));
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", img);
    }

    /**
     * Navigate to fast loans section
     */
    public static void fillingFastLoans(WebDriver driver) {
        UIElementHandler.safeClick(driver, driver.findElement(By.id("home")));
        org.openqa.selenium.WebElement img = driver.findElement(By.xpath("//img[@src='https://ldc-prod-cms.lendenclub.com/filling-fast-icon.png']"));
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", img);
    }

    /**
     * Navigate to daily repayment loans section
     */
    public static void openDailyRepaymentLoans(WebDriver driver) {
        UIElementHandler.safeClick(driver, driver.findElement(By.id("home")));
        org.openqa.selenium.WebElement img = driver.findElement(By.xpath("//img[@src='https://ldc-prod-cms.lendenclub.com/daily-repayment-icon.png']"));
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", img);
    }

    /**
     * Navigate to monthly repayment loans section
     */
    public static void openMonthlyRepaymentLoans(WebDriver driver) {
        UIElementHandler.safeClick(driver, driver.findElement(By.id("home")));
        org.openqa.selenium.WebElement img = driver.findElement(By.xpath("//img[@src='https://ldc-prod-cms.lendenclub.com/monthly-repayment-icon.png']"));
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", img);
    }
}

