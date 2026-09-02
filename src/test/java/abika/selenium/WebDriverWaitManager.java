package abika.selenium;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Centralized management of WebDriverWait instances and timeout constants
 */
public class WebDriverWaitManager {
    public static final int LONG_TIMEOUT = 10;
    public static final int SHORT_TIMEOUT = 5;
    public static final int ULTRA_SHORT_TIMEOUT = 1;
    public static final int MAX_RETRIES = 12;
    public static final long FAST_TIMEOUT_MS = 500;
    public static final long FAST_POLL_MS = 50;

    private static WebDriverWait standardWait;
    private static WebDriverWait shortWait;
    private static WebDriverWait ultraShortWait;
    private static WebDriverWait fastWait;

    /**
     * Initialize wait instances - call once after WebDriver is created
     */
    public static void initialize(WebDriver driver) {
        standardWait = new WebDriverWait(driver, Duration.ofSeconds(LONG_TIMEOUT));
        shortWait = new WebDriverWait(driver, Duration.ofSeconds(SHORT_TIMEOUT));
        ultraShortWait = new WebDriverWait(driver, Duration.ofSeconds(ULTRA_SHORT_TIMEOUT));
        fastWait = new WebDriverWait(driver, Duration.ofMillis(FAST_TIMEOUT_MS));
        fastWait.pollingEvery(Duration.ofMillis(FAST_POLL_MS));
    }

    public static WebDriverWait getStandardWait() {
        return standardWait;
    }

    public static WebDriverWait getShortWait() {
        return shortWait;
    }

    public static WebDriverWait getUltraShortWait() {
        return ultraShortWait;
    }

    public static WebDriverWait getFastWait() {
        return fastWait;
    }

    /**
     * Safe casting utility for JavaScript returns that may be Double or Long
     * Fixes the issue: "class java.lang.Double cannot be cast to class java.lang.Long"
     */
    public static long safeCastToLong(Object value) {
        if (value instanceof Double) {
            return ((Double) value).longValue();
        } else if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        return 0L;
    }

    /**
     * Safe casting utility for JavaScript returns to double
     */
    public static double safeCastToDouble(Object value) {
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Long) {
            return ((Long) value).doubleValue();
        } else if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        }
        return 0.0;
    }
}
