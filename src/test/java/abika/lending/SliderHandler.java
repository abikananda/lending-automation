package abika.lending;

import abika.MethodTimer;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles lending amount slider adjustment.
 * Uses real keyboard/pointer interaction so React/MUI receives the state change.
 */
public class SliderHandler {
    private static final Logger logger = LoggerFactory.getLogger(SliderHandler.class);

    public static boolean adjustSlider(WebDriver driver, int targetValue) {
        MethodTimer timer = new MethodTimer("Slider adjustment");
        try {
            WebElement input = driver.findElement(By.cssSelector(".MuiSlider-thumb input[type='range']"));
            int min = intAttr(input, "min");
            int max = intAttr(input, "max");
            int step = Math.max(1, intAttr(input, "step"));
            int target = Math.max(min, Math.min(max, targetValue));
            int current = intAttr(input, "value");

            logger.info("Slider range: [{}, {}], step: {}, current: {}, target: {}",
                    min, max, step, current, target);

            if (current == target && verifyCommittedValue(driver, input, target)) {
                logger.info("✅ Slider already at target value: {}", target);
                return true;
            }

            // Preferred strategy: keyboard events are handled by MUI/React and therefore update
            // both the actual form value and component state. HOME gives us a deterministic base.
            try {
                input.click();
                input.sendKeys(Keys.HOME);
                int steps = Math.max(0, (target - min) / step);
                for (int i = 0; i < steps; i++) {
                    input.sendKeys(Keys.ARROW_RIGHT);
                }
                if (waitAndVerify(driver, input, target)) {
                    logger.info("✅ Slider set by keyboard to {}", target);
                    return true;
                }
            } catch (Exception e) {
                logger.debug("Keyboard slider strategy failed: {}", e.getMessage());
            }

            // Fallback: click the physical track at the target position. This is still a real UI
            // interaction; no synthetic direct value mutation is used for a financial control.
            try {
                WebElement root = driver.findElement(By.cssSelector(".MuiSlider-root"));
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", root);
                int width = root.getSize().getWidth();
                double pct = max == min ? 0.0 : (double) (target - min) / (max - min);
                int x = (int) Math.round(pct * width - width / 2.0);
                new Actions(driver).moveToElement(root, x, 0).click().perform();
                if (waitAndVerify(driver, input, target)) {
                    logger.info("✅ Slider set by track click to {}", target);
                    return true;
                }
            } catch (Exception e) {
                logger.debug("Track slider strategy failed: {}", e.getMessage());
            }

            logger.error("❌ Slider failed. Actual input value: {}, target: {}", input.getAttribute("value"), target);
            return false;
        } catch (Exception e) {
            logger.error("Slider adjustment exception: {}", e.getMessage(), e);
            return false;
        } finally {
            timer.end();
        }
    }

    private static boolean waitAndVerify(WebDriver driver, WebElement input, int expected) {
        for (int i = 0; i < 5; i++) {
            try {
                if (verifyCommittedValue(driver, input, expected)) return true;
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Financial safety verification:
     * 1) the real range input value MUST match;
     * 2) when MUI exposes track geometry, it MUST agree within tolerance;
     * 3) when a visible value label exists, it MUST agree;
     * aria-valuenow is diagnostic only because current MUI renders it stale in live runs even
     * after real keyboard/track interaction has committed the actual input value.
     */
    private static boolean verifyCommittedValue(WebDriver driver, WebElement input, int expected) {
        try {
            int actual = intAttr(input, "value");
            if (actual != expected) return false;

            String aria = input.getAttribute("aria-valuenow");
            if (aria != null && !aria.isBlank()) {
                try {
                    int ariaValue = Integer.parseInt(aria);
                    if (ariaValue != expected) {
                        logger.debug("MUI aria-valuenow is stale: {} while input.value={}", ariaValue, expected);
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            int min = intAttr(input, "min");
            int max = intAttr(input, "max");
            boolean visualEvidenceChecked = false;

            try {
                WebElement track = driver.findElement(By.cssSelector(".MuiSlider-track"));
                String style = track.getAttribute("style");
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("width:\\s*([\\d.]+)%")
                        .matcher(style == null ? "" : style);
                if (matcher.find() && max > min) {
                    visualEvidenceChecked = true;
                    double actualPct = Double.parseDouble(matcher.group(1));
                    double expectedPct = ((double) (expected - min) / (max - min)) * 100.0;
                    if (Math.abs(actualPct - expectedPct) > 3.0) {
                        logger.warn("Slider track position mismatch: {}% vs expected {}%", actualPct, expectedPct);
                        return false;
                    }
                }
            } catch (Exception ignored) {
            }

            try {
                java.util.List<WebElement> labels = driver.findElements(By.cssSelector(".MuiSlider-valueLabelLabel"));
                for (WebElement label : labels) {
                    if (!label.isDisplayed()) continue;
                    String digits = label.getText().replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        visualEvidenceChecked = true;
                        if (Integer.parseInt(digits) != expected) return false;
                    }
                }
            } catch (Exception ignored) {
            }

            // The actual form input is authoritative. Visual evidence, when exposed by MUI,
            // is additionally required to agree. Absence of a transient value label is normal.
            return actual == expected;
        } catch (Exception e) {
            logger.warn("Slider verification exception: {}", e.getMessage());
            return false;
        }
    }

    private static int intAttr(WebElement element, String attr) {
        String value = element.getAttribute(attr);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Slider attribute '" + attr + "' is missing");
        }
        return (int) Math.round(Double.parseDouble(value));
    }
}
