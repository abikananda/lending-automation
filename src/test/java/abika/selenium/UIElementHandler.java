package abika.selenium;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handles UI element interactions.
 */
public class UIElementHandler {
    private static final Logger logger = LoggerFactory.getLogger(UIElementHandler.class);
    private static final String BORROWER_POPUP_SELECTOR = "div.sc-dtBdUo.hHvdph";

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
                        ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, window.innerHeight * 0.8);");
                        return;
                    }
                }
            }

            Object before = ((JavascriptExecutor) driver).executeScript("return arguments[0].scrollTop;", scrollableContainer);
            long currentScroll = WebDriverWaitManager.safeCastToLong(before);
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollTop += arguments[0].clientHeight * 0.8;", scrollableContainer);
            Object after = ((JavascriptExecutor) driver).executeScript("return arguments[0].scrollTop;", scrollableContainer);
            long newScroll = WebDriverWaitManager.safeCastToLong(after);
            logger.debug("Borrower scroll position {} -> {}", currentScroll, newScroll);
        } catch (Exception e) {
            logger.debug("Could not scroll borrower list: {}", e.getMessage());
        }
    }

    /**
     * Non-financial borrower-details click. No retry is performed here.
     */
    public static void clickCardArrowFast(WebDriver driver, WebElement card) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                    "const card=arguments[0];" +
                    "const sel=arguments[1];" +
                    "if(document.querySelector(sel)) return 'POPUP_PRESENT';" +
                    "const arrow=card.querySelector(\"div[aria-label='View borrower details']\");" +
                    "if(!arrow) return 'ARROW_MISSING';" +
                    "arrow.scrollIntoView({block:'center'});" +
                    "arrow.click();" +
                    "return 'CLICKED';",
                    card, BORROWER_POPUP_SELECTOR);

            String status = String.valueOf(result);
            if ("POPUP_PRESENT".equals(status)) {
                throw new IllegalStateException("Previous borrower popup is still present; refusing to open next borrower");
            }
            if (!"CLICKED".equals(status)) {
                throw new NoSuchElementException("Borrower details arrow was not found");
            }
            WebDriverWaitManager.getShortWait().until(UIElementHandler::isBorrowerPopupVisibleFast);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Borrower popup could not be opened safely; aborting borrower processing", e);
        }
    }

    /**
     * Close borrower popup and require removal before processing another borrower.
     */
    public static void closePopupFast(WebDriver driver) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                    "const popup=document.querySelector(arguments[0]);" +
                    "if(!popup) return 'NO_POPUP';" +
                    "const closeBtn=popup.querySelector('svg');" +
                    "if(!closeBtn) return 'CLOSE_MISSING';" +
                    "closeBtn.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));" +
                    "return 'CLICKED';",
                    BORROWER_POPUP_SELECTOR);

            String status = String.valueOf(result);
            if ("NO_POPUP".equals(status)) return;
            if ("CLOSE_MISSING".equals(status)) {
                throw new IllegalStateException("Borrower popup close control was not found");
            }
            if (!"CLICKED".equals(status)) {
                throw new IllegalStateException("Unexpected borrower popup close state: " + status);
            }

            try {
                WebDriverWaitManager.getFastWait().until(d -> !borrowerPopupExistsFast(d));
            } catch (TimeoutException e) {
                WebDriverWaitManager.getUltraShortWait().until(d -> !borrowerPopupExistsFast(d));
            }

            if (borrowerPopupExistsFast(driver)) {
                throw new IllegalStateException("Borrower popup is still present after close confirmation");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Borrower popup close failed; aborting before next borrower to prevent stale-popup reuse", e);
        }
    }

    private static boolean borrowerPopupExistsFast(WebDriver driver) {
        Object result = ((JavascriptExecutor) driver).executeScript(
                "return document.querySelector(arguments[0]) !== null;", BORROWER_POPUP_SELECTOR);
        return Boolean.TRUE.equals(result);
    }

    private static boolean isBorrowerPopupVisibleFast(WebDriver driver) {
        Object result = ((JavascriptExecutor) driver).executeScript(
                "const el=document.querySelector(arguments[0]);" +
                "if(!el) return false;" +
                "const s=getComputedStyle(el);" +
                "if(s.display==='none'||s.visibility==='hidden'||Number(s.opacity)===0) return false;" +
                "const r=el.getBoundingClientRect();" +
                "return r.width>0&&r.height>0;",
                BORROWER_POPUP_SELECTOR);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Financial action: click Add Loan exactly once. Never retry here.
     */
    public static void clickAddLoanButton(WebDriver driver) {
        try {
            WebElement addLoanBtn = WebDriverWaitManager.getUltraShortWait().until(
                    ExpectedConditions.elementToBeClickable(By.xpath(".//button[normalize-space()='Add Loan']")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", addLoanBtn);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Add Loan could not be clicked safely; no wallet reservation or selection must be recorded", e);
        }
    }

    public static void expandPanelFast(WebDriver driver, String panelHeader) {
        try {
            By buttonLocator = By.xpath("//span[contains(normalize-space(.), '" + panelHeader + "')]/ancestor::button[1]");
            WebElement button = driver.findElement(buttonLocator);
            if ("false".equals(button.getAttribute("aria-expanded"))) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", button);
                try {
                    WebDriverWaitManager.getFastWait().until(
                            ExpectedConditions.attributeToBe(buttonLocator, "aria-expanded", "true"));
                } catch (TimeoutException e) {
                    Thread.sleep(150);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not expand panel {}: {}", panelHeader, e.getMessage());
        }
    }

    public static void expandPanel(WebDriver driver, WebDriverWait wait, String panelHeader) {
        try {
            By accordionHeader = By.xpath("//span[contains(normalize-space(.), '" + panelHeader + "')]");
            WebElement header = wait.until(ExpectedConditions.elementToBeClickable(accordionHeader));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", header);
            WebElement parentButton = header.findElement(By.xpath("ancestor::button[1]"));
            if ("false".equals(parentButton.getAttribute("aria-expanded"))) {
                header.click();
                WebDriverWaitManager.getUltraShortWait().until(
                        ExpectedConditions.invisibilityOfElementLocated(
                                By.xpath("//span[contains(normalize-space(.), '" + panelHeader + "')]/ancestor::button[1][not(@aria-expanded='true')]")));
            }
        } catch (Exception e) {
            logger.debug("Could not expand {}: {}", panelHeader, e.getMessage());
        }
    }

    public static void clickElement(WebDriver driver, WebDriverWait wait, By locator) {
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }

    public static void toggleCheckbox(WebDriver driver, String xpath, boolean shouldBeSelected) {
        WebElement checkbox = driver.findElement(By.xpath(xpath));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", checkbox);
        if (checkbox.isSelected() != shouldBeSelected) checkbox.click();
    }

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
                if (i == 0) {
                    try { Thread.sleep(25); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }
        }
        throw new IllegalStateException("Unable to click element safely");
    }

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
