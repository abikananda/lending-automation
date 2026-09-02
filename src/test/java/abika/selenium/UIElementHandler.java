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
    private static final By BORROWER_POPUP = By.cssSelector("div.sc-dtBdUo.hHvdph");

    /**
     * Scroll to load more cards.
     *
     * Important: this method only performs the scroll. BorrowerScraper already waits for
     * the borrower card count to grow after calling this method, so waiting here as well
     * duplicated the same synchronization and could add up to two seconds per retry.
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
                        ((JavascriptExecutor) driver).executeScript(
                            "window.scrollBy(0, window.innerHeight * 0.8);"
                        );
                        return;
                    }
                }
            }

            if (scrollableContainer != null) {
                Object scrollObj = ((JavascriptExecutor) driver).executeScript(
                    "return arguments[0].scrollTop;",
                    scrollableContainer
                );
                long currentScroll = WebDriverWaitManager.safeCastToLong(scrollObj);

                ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollTop += arguments[0].clientHeight * 0.8;",
                    scrollableContainer
                );

                Object newScrollObj = ((JavascriptExecutor) driver).executeScript(
                    "return arguments[0].scrollTop;",
                    scrollableContainer
                );
                long newScroll = WebDriverWaitManager.safeCastToLong(newScrollObj);

                if (newScroll > currentScroll) {
                    logger.debug("Scrolled borrower container. Previous position: {}, New position: {}", currentScroll, newScroll);
                } else {
                    logger.debug("Scroll position unchanged immediately after scroll request: {}", currentScroll);
                }
            }
        } catch (Exception e) {
            logger.info("Could not scroll to load more cards: {}", e.getMessage());
        }
    }

    /**
     * Click a borrower card arrow only when no previous borrower popup remains visible,
     * then require the new borrower popup to become visible.
     *
     * No retry is performed. If a previous popup is still present, processing aborts
     * before another borrower can be opened or parsed.
     */
    public static void clickCardArrowFast(WebDriver driver, WebElement card) {
        try {
            if (!driver.findElements(BORROWER_POPUP).isEmpty()) {
                throw new IllegalStateException(
                    "Previous borrower popup is still present; refusing to open the next borrower"
                );
            }

            WebElement arrow = card.findElement(By.cssSelector("div[aria-label='View borrower details']"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", arrow);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", arrow);
            WebDriverWaitManager.getShortWait().until(
                ExpectedConditions.visibilityOfElementLocated(BORROWER_POPUP)
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                "Borrower popup could not be opened safely; aborting borrower processing to avoid parsing stale data",
                e
            );
        }
    }

    /**
     * Close the borrower popup and require it to be fully gone before returning.
     *
     * Critical safety rule: the next borrower must never be processed while the previous
     * popup is still present. If the popup does not disappear within the configured waits,
     * fail closed and abort the workflow instead of proceeding with stale UI state.
     */
    public static void closePopupFast(WebDriver driver) {
        try {
            List<WebElement> popups = driver.findElements(BORROWER_POPUP);
            if (popups.isEmpty()) {
                return;
            }

            WebElement closeBtn = driver.findElement(By.cssSelector("div.sc-dtBdUo.hHvdph svg"));
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true, view:window}));",
                closeBtn
            );

            try {
                WebDriverWaitManager.getFastWait().until(
                    ExpectedConditions.invisibilityOfElementLocated(BORROWER_POPUP)
                );
            } catch (TimeoutException fastTimeout) {
                WebDriverWaitManager.getUltraShortWait().until(
                    ExpectedConditions.invisibilityOfElementLocated(BORROWER_POPUP)
                );
            }

            if (!driver.findElements(BORROWER_POPUP).isEmpty()) {
                throw new IllegalStateException(
                    "Borrower popup is still present after close confirmation; aborting before next borrower"
                );
            }
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                "Borrower popup did not fully close; aborting before next borrower to prevent stale-popup reuse",
                e
            );
        } catch (NoSuchElementException e) {
            if (!driver.findElements(BORROWER_POPUP).isEmpty()) {
                throw new IllegalStateException(
                    "Borrower popup is visible but close control was not found; aborting before next borrower",
                    e
                );
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Borrower popup close failed; aborting before next borrower to prevent stale-popup reuse",
                e
            );
        }
    }

    /**
     * Click "Add Loan" exactly once and fail if Selenium cannot perform the click.
     *
     * Critical safety rule: callers reserve wallet and record a borrower as selected only
     * after this method returns. Swallowing a click failure would therefore create a false
     * local investment state. This method intentionally does not retry the financial action.
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
            throw new IllegalStateException(
                "Add Loan could not be clicked safely; no wallet reservation or selection must be recorded",
                e
            );
        }
    }

    /**
     * Expand a panel using fast JavaScript click.
     * Replaces the fixed 150ms sleep with condition-based waiting. On an unusually slow
     * transition, the previous 150ms fallback is retained so reliability is not reduced.
     */
    public static void expandPanelFast(WebDriver driver, String panelHeader) {
        try {
            By buttonLocator = By.xpath("//span[contains(normalize-space(.), '" + panelHeader + "')]/ancestor::button[1]");
            WebElement button = driver.findElement(buttonLocator);

            String ariaExpanded = button.getAttribute("aria-expanded");

            if ("false".equals(ariaExpanded)) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", button);
                try {
                    WebDriverWaitManager.getFastWait().until(
                        ExpectedConditions.attributeToBe(buttonLocator, "aria-expanded", "true")
                    );
                } catch (TimeoutException e) {
                    logger.debug("Panel {} did not report expanded within fast wait; applying compatibility delay", panelHeader);
                    Thread.sleep(150);
                }
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
