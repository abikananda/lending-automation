package abika.selenium;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handles all UI element interactions: clicking, scrolling, expanding panels, etc.
 */
public class UIElementHandler {
    private static final Logger logger = LoggerFactory.getLogger(UIElementHandler.class);

    /**
     * Scroll to load more cards with explicit wait instead of hard-coded sleep
     * Waits for new content to appear after scroll (more reliable than fixed delays)
     */
    public static void scrollToLoadMoreCards(WebDriver driver) {
        try {
            WebElement scrollableContainer = null;

            try {
                scrollableContainer = driver.findElement(By.cssSelector("div[class*='MuiBox-root'][style*='overflow']"));
            } catch (Exception e1) {
                try {
                    scrollableContainer = driver.findElement(By.cssSelector("div.MuiBox-root.css-rvjv84"));
                } catch (Exception e2) {
                    try {
                        scrollableContainer = driver.findElement(By.cssSelector("main, [role='main'], .MuiContainer-root"));
                    } catch (Exception e3) {
                        // Last resort: scroll window with explicit wait for content
                        ((JavascriptExecutor) driver).executeScript(
                            "window.scrollBy(0, window.innerHeight * 0.8);"
                        );
                        
                        // Wait for new content to load (explicit wait instead of sleep)
                        try {
                            WebDriverWaitManager.getShortWait().until(
                                d -> {
                                    Long newHeight = (Long) ((JavascriptExecutor) d).executeScript("return document.body.scrollHeight;");
                                    return newHeight != null && newHeight > 0;
                                }
                            );
                        } catch (TimeoutException e) {
                            logger.debug("Timeout waiting for content after scroll");
                        }
                        return;
                    }
                }
            }

            if (scrollableContainer != null) {
                final WebElement container = scrollableContainer;  // Make final for lambda
                
                // Get current scroll position - safely handle Double/Long casting
                Object scrollObj = ((JavascriptExecutor) driver).executeScript(
                    "return arguments[0].scrollTop;",
                    container
                );
                long currentScroll = WebDriverWaitManager.safeCastToLong(scrollObj);

                // Scroll down
                ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollTop += arguments[0].clientHeight * 0.8;",
                    container
                );

                // Wait for scroll animation to complete and new content to appear (explicit wait instead of sleep)
                try {
                    final long previousScroll = currentScroll;  // Make final for lambda
                    
                    WebDriverWaitManager.getShortWait().until(
                        d -> {
                            Object newScrollObj = ((JavascriptExecutor) d).executeScript(
                                "return arguments[0].scrollTop;",
                                container
                            );
                            long newScroll = WebDriverWaitManager.safeCastToLong(newScrollObj);
                            return newScroll > previousScroll;  // Wait for scroll to actually move
                        }
                    );
                } catch (TimeoutException e) {
                    logger.debug("Timeout waiting for scroll to complete");
                }

                // Final verification
                Object newScrollObj = ((JavascriptExecutor) driver).executeScript(
                    "return arguments[0].scrollTop;",
                    container
                );
                long newScroll = WebDriverWaitManager.safeCastToLong(newScrollObj);

                if (newScroll > currentScroll) {
                    logger.info("✅ Scrolled successfully. Previous position: {}, New position: {}", currentScroll, newScroll);
                } else {
                    logger.info("⚠️ Scroll may not have worked. Position unchanged: {}", currentScroll);
                }
            }
        } catch (Exception e) {
            logger.info("Could not scroll to load more cards: {}", e.getMessage());
        }
    }

    /**
     * Click a card arrow and wait for popup
     * Optimized: Removed unnecessary sleep before click
     */
    public static void clickCardArrowFast(WebDriver driver, WebElement card) {
        try {
            WebElement arrow = card.findElement(By.cssSelector("div[aria-label='View borrower details']"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", arrow);
            // Removed Thread.sleep(20) - JS click is instant, scroll animation handles timing
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", arrow);
            WebDriverWaitManager.getShortWait().until(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.sc-dtBdUo.jipznm"))
            );
        } catch (Exception e) {
            logger.info("Card arrow click/wait failed: {}", e.getMessage());
        }
    }

    /**
     * Close popup fast - optimized for speed
     * Uses UltraShortWait and moves on quickly if timeout (popup likely already closing)
     */
    public static void closePopupFast(WebDriver driver) {
        try {
            WebElement closeBtn = driver.findElement(By.cssSelector("div.sc-dtBdUo.jipznm svg"));
            // Use JavaScript click for instant execution
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", closeBtn);
            
            // Use ultra-short wait with shorter timeout for faster throughput
            // If popup doesn't close quickly, still proceed (it's closing in background)
            try {
                WebDriverWaitManager.getUltraShortWait().until(
                    ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("div.sc-dtBdUo.jipznm"))
                );
            } catch (TimeoutException e) {
                // Popup is likely closing in background, don't wait for it
                logger.debug("Popup close timeout - proceeding anyway (popup closing in background)");
            }
        } catch (NoSuchElementException e) {
            logger.debug("Close button not found - popup likely already closed");
        } catch (Exception e) {
            logger.debug("Error closing popup: {}", e.getMessage());
        }
    }

    /**
     * Click "Add Loan" button with minimal wait
     */
    public static void clickAddLoanButton(WebDriver driver) {
        try {
            WebElement addLoanBtn = WebDriverWaitManager.getUltraShortWait().until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath(".//button[normalize-space()='Add Loan']")
                )
            );
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", addLoanBtn);
        } catch (Exception e) {
            logger.info("Failed to click Add Loan button: {}", e.getMessage());
        }
    }

    /**
     * Expand a panel using fast JavaScript click
     */
    public static void expandPanelFast(WebDriver driver, String panelHeader) {
        try {
            By buttonLocator = By.xpath("//span[contains(normalize-space(.), '" + panelHeader + "')]/ancestor::button[1]");
            WebElement button = driver.findElement(buttonLocator);

            String ariaExpanded = button.getAttribute("aria-expanded");

            if ("false".equals(ariaExpanded)) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", button);
                Thread.sleep(150);
            }
        } catch (Exception e) {
            logger.debug("Could not expand panel {}: {}", panelHeader, e.getMessage());
        }
    }

    /**
     * Expand panel with wait condition
     */
    public static void expandPanel(WebDriver driver, WebDriverWait wait, String panelHeader) {
        try {
            By accordionHeader = By.xpath("//span[contains(normalize-space(.), '" + panelHeader + "')]");
            WebElement header = wait.until(ExpectedConditions.elementToBeClickable(accordionHeader));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", header);

            WebElement parentButton = header.findElement(By.xpath("ancestor::button[1]"));
            String ariaExpanded = parentButton.getAttribute("aria-expanded");

            if ("false".equals(ariaExpanded)) {
                header.click();
                WebDriverWaitManager.getUltraShortWait().until(
                    ExpectedConditions.invisibilityOfElementLocated(
                        By.xpath("//span[contains(normalize-space(.), '" + panelHeader + "')]/ancestor::button[1][not(@aria-expanded='true')]")
                    )
                );
            }
        } catch (Exception e) {
            logger.info("Could not expand {}: {}", panelHeader, e.getMessage());
        }
    }

    /**
     * Click an element safely with JavaScript
     */
    public static void clickElement(WebDriver driver, WebDriverWait wait, By locator) {
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }

    /**
     * Toggle checkbox with proper state checking
     */
    public static void toggleCheckbox(WebDriver driver, String xpath, boolean shouldBeSelected) {
        WebElement checkbox = driver.findElement(By.xpath(xpath));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", checkbox);
        if (checkbox.isSelected() != shouldBeSelected) {
            checkbox.click();
        }
    }

    /**
     * Safe click with JavaScript and fallback to regular click
     */
    public static void safeClick(WebDriver driver, WebElement element) {
        try {
            handleInterstitialIfPresent(driver);
            WebDriverWaitManager.getStandardWait().until(ExpectedConditions.elementToBeClickable(element));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            return;
        } catch (Exception ignored) {
        }

        for (int i = 0; i < 2; i++) {
            try {
                handleInterstitialIfPresent(driver);
                WebDriverWaitManager.getStandardWait().until(ExpectedConditions.elementToBeClickable(element)).click();
                return;
            } catch (ElementClickInterceptedException | StaleElementReferenceException e) {
                if (i < 1) {
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
        throw new RuntimeException("Unable to click element");
    }

    /**
     * Handle interstitial ads if present
     */
    public static void handleInterstitialIfPresent(WebDriver driver) {
        try {
            List<WebElement> iframes = driver.findElements(By.tagName("iframe"));

            for (WebElement frame : iframes) {
                driver.switchTo().frame(frame);

                List<WebElement> close = driver.findElements(By.className("CT_InterstitialClose"));
                if (!close.isEmpty()) {
                    close.get(0).click();
                    driver.switchTo().defaultContent();
                    Thread.sleep(100);
                    return;
                }
                driver.switchTo().defaultContent();
            }

        } catch (Exception ignored) {
            driver.switchTo().defaultContent();
        }
    }
}

