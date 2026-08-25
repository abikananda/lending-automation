package abika.setup;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import abika.selenium.WebDriverWaitManager;

import java.time.Duration;

/**
 * Handles WebDriver initialization and configuration
 */
public class BrowserDriverSetup {

    /**
     * Setup and initialize ChromeDriver with optimized configuration
     */
    public static WebDriver setupWebDriver() {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-default-apps");
        options.addArguments("--disable-preconnect");
        options.addArguments("--disable-background-networking");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        return driver;
    }

    /**
     * Initialize WebDriverWait instances - call after WebDriver creation
     */
    public static void initializeWaitInstances(WebDriver driver) {
        WebDriverWaitManager.initialize(driver);
    }

}

