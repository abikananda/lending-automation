package abika.parsing;

import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import abika.selenium.WebDriverWaitManager;

import java.util.List;
import java.util.Optional;

/**
 * Utility class for safe text extraction and parsing from WebElements
 */
public class TextParsingUtils {
    private static final Logger logger = LoggerFactory.getLogger(TextParsingUtils.class);

    /**
     * Safely retrieve text from an element without throwing exceptions
     */
    public static Optional<String> safelyGetText(By locator, WebDriver driver) {
        int retries = 2;
        for (int i = 0; i < retries; i++) {
            try {
                List<WebElement> elements = driver.findElements(locator);
                if (elements.isEmpty()) {
                    return Optional.empty();
                }
                WebElement element = elements.get(0);
                // Use ultraShortWait for faster execution
                WebDriverWaitManager.getUltraShortWait().until(ExpectedConditions.visibilityOf(element));
                String text = element.getText().trim();
                if (!text.isEmpty()) {
                    return Optional.of(text);
                }
            } catch (StaleElementReferenceException e) {
                if (i < retries - 1) {
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException ignored) {
                    }
                }
            } catch (TimeoutException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Safely parse a double value from an element
     */
    public static double safelyParseDouble(By locator, WebDriver driver) {
        Optional<String> textOpt = safelyGetText(locator, driver);
        return textOpt.map(text -> {
            try {
                // Preprocess: Remove any characters except digits, dot, or minus sign
                String cleaned = text.replaceAll("[^\\d.\\-]", "");
                // Handle Indian comma separators (e.g., 31,000)
                cleaned = cleaned.replaceAll(",", "");
                return Double.parseDouble(cleaned);
            } catch (NumberFormatException e) {
                logger.info("⚠️ Invalid data format: {}", text);
                return 0.0;
            }
        }).orElse(0.0);
    }

    /**
     * Safely parse a double value with percentage suffix
     */
    public static double safelyParseDoubleWithPercent(By locator, WebDriver driver) {
        Optional<String> textOpt = safelyGetText(locator, driver);
        return textOpt.map(text -> {
            try {
                return Double.parseDouble(text.replace("%", ""));
            } catch (NumberFormatException e) {
                logger.info("⚠️ Invalid data format: {}", text);
                return 0.0;
            }
        }).orElse(0.0);
    }

    /**
     * Safely parse an integer value from an element
     */
    public static int safelyParseInt(By locator, WebDriver driver) {
        Optional<String> textOpt = safelyGetText(locator, driver);
        return textOpt.map(text -> {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                logger.info("⚠️ Invalid data format: {}", text);
                return 0;
            }
        }).orElse(0);
    }

    /**
     * Safely parse an integer value with months suffix (e.g., "3 Month(s)" → 3)
     */
    public static int safelyParseIntWithMonths(By locator, WebDriver driver) {
        Optional<String> textOpt = safelyGetText(locator, driver);
        return textOpt.map(text -> {
            try {
                return Integer.parseInt(text.split(" ")[0]);
            } catch (NumberFormatException e) {
                logger.info("⚠️ Invalid data format: {}", text);
                return 0;
            }
        }).orElse(0);
    }

    /**
     * Safely retrieve a string value from an element
     */
    public static String safelyParseString(By locator, WebDriver driver) {
        Optional<String> textOpt = safelyGetText(locator, driver);
        return textOpt.orElse("");
    }

    // --- Helper methods for JavaScript extracted text ---

    /**
     * Parse double value from JavaScript extracted text
     */
    public static double parseDouble(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }
        try {
            String cleaned = text.replaceAll("[^\\d.\\-]", "").replaceAll(",", "");
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Parse integer value from JavaScript extracted text
     */
    public static int parseInt(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.replaceAll("\\D", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Parse integer with months suffix (e.g., "3 Month(s)" → 3)
     */
    public static int parseIntWithMonths(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.split(" ")[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Parse double with percent suffix (e.g., "35.88%" → 35.88)
     */
    public static double parseDoubleWithPercent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(text.replace("%", "").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}

