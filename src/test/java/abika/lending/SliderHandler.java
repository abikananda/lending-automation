package abika.lending;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import abika.MethodTimer;
import abika.selenium.WebDriverWaitManager;

/**
 * Handles slider adjustment for lending amount selection
 */
public class SliderHandler {
    private static final Logger logger = LoggerFactory.getLogger(SliderHandler.class);

    /**
     * Adjust the lending amount slider to target value
     *
     * @param driver WebDriver instance
     * @param targetValue Target lending amount
     * @return true if slider was adjusted successfully
     */
    public static boolean adjustSlider(WebDriver driver, int targetValue) {
        MethodTimer sliderTimer = new MethodTimer("Slider adjustment");
        try {
            WebElement sliderThumb = driver.findElement(By.cssSelector(".MuiSlider-thumb"));
            WebElement sliderInput = driver.findElement(By.cssSelector(".MuiSlider-thumb input[type='range']"));

            int min = Integer.parseInt(sliderInput.getAttribute("min"));
            int max = Integer.parseInt(sliderInput.getAttribute("max"));
            int step = Integer.parseInt(sliderInput.getAttribute("step"));
            int currentValue = Integer.parseInt(sliderInput.getAttribute("value"));

            // Clamp target value to valid range
            int clampedTarget = Math.max(min, Math.min(max, targetValue));

            logger.info("Slider range: [{}, {}], current: {}, target: {}", min, max, currentValue, clampedTarget);

            // Only move slider if target is different from current
            if (currentValue == clampedTarget) {
                logger.info("Slider already at target value: {}", currentValue);
                sliderTimer.end();
                return true;
            }

            // Strategy 0: Use JavaScript to directly set the slider value
            // This bypasses overlay issues and directly manipulates the hidden input
            logger.info("Attempting JavaScript direct value set");
            try {
                org.openqa.selenium.JavascriptExecutor jsExecutor = (org.openqa.selenium.JavascriptExecutor) driver;
                
                // Set the value directly
                jsExecutor.executeScript("arguments[0].value = arguments[1];", sliderInput, clampedTarget);
                
                // Trigger change event to notify React
                jsExecutor.executeScript(
                    "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));" +
                    "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                    "arguments[0].dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));" +
                    "arguments[0].dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));",
                    sliderInput
                );
                
                Thread.sleep(300);
                String newValue = sliderInput.getAttribute("value");
                int newVal = Integer.parseInt(newValue);
                
                logger.info("After JS direct set: value = {}", newVal);
                
                if (newVal == clampedTarget && verifySliderValue(driver, sliderInput, clampedTarget)) {
                    logger.info("✅ Slider set successfully via JavaScript: {}", newVal);
                    sliderTimer.end();
                    return true;
                } else {
                    logger.info("JS direct set changed value to {} (target {}), or verification failed, continuing with fallbacks", newVal, clampedTarget);
                }
            } catch (Exception e) {
                logger.error("❌ JavaScript direct set failed: {}", e.getMessage());
            }

            // Compute target index based on step
            int currentIndex = (currentValue - min) / step;
            int targetIndex = (clampedTarget - min) / step;
            logger.info("Current index: {}, Target index: {}, step: {}", currentIndex, targetIndex, step);

            // Ensure thumb is visible
            try {
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", sliderThumb);
                Thread.sleep(50);
            } catch (Exception ignore) {}

            Actions action = new Actions(driver);

            // Strategy 1: Click the mark corresponding to the target index (if present)
            try {
                WebElement mark = driver.findElement(By.cssSelector(".MuiSlider-mark[data-index='" + targetIndex + "']"));
                logger.info("Attempting to click mark index {}", targetIndex);
                action.moveToElement(mark).click().perform();

                // Wait for value to update
                Thread.sleep(150);
                String val = sliderInput.getAttribute("value");
                int newVal = Integer.parseInt(val);
                if (newVal == clampedTarget && verifySliderValue(driver, sliderInput, clampedTarget)) {
                    logger.info("✅ Slider set by mark click: {}", newVal);
                    sliderTimer.end();
                    return true;
                }
                logger.info("Mark click changed value to {} (target {}), or verification failed, continuing", newVal, clampedTarget);
            } catch (Exception e) {
                logger.error("❌ Mark click strategy failed: {}", e.getMessage());
            }

            // Strategy 2: Use keyboard arrows on the hidden input (rely on component key handling)
            try {
                logger.info("Attempting keyboard adjustment: navigating {} steps", Math.abs(targetIndex - currentIndex));
                sliderInput.click(); // focus
                int steps = targetIndex - currentIndex;
                org.openqa.selenium.Keys arrowKey = steps > 0 ? org.openqa.selenium.Keys.ARROW_RIGHT : org.openqa.selenium.Keys.ARROW_LEFT;
                steps = Math.abs(steps);
                for (int s = 0; s < steps; s++) {
                    sliderInput.sendKeys(arrowKey);
                    Thread.sleep(40);
                }

                Thread.sleep(150);
                String val2 = sliderInput.getAttribute("value");
                int newVal2 = Integer.parseInt(val2);
                if (newVal2 == clampedTarget && verifySliderValue(driver, sliderInput, clampedTarget)) {
                    logger.info("✅ Slider set by keyboard to {}", newVal2);
                    sliderTimer.end();
                    return true;
                }
                logger.info("Keyboard attempt changed value to {} (target {}), or verification failed, continuing", newVal2, clampedTarget);
            } catch (Exception e) {
                logger.error("❌ Keyboard adjustment strategy failed: {}", e.getMessage());
            }

            // Strategy 3: Fallback to clicking track position
            try {
                WebElement track = driver.findElement(By.cssSelector(".MuiSlider-root"));
                int trackWidth = track.getSize().getWidth();
                double pct = (double) (clampedTarget - min) / (max - min);
                int offsetFromCenter = (int) (pct * trackWidth - (trackWidth / 2.0));
                logger.info("Fallback: clicking track at pct={} offsetFromCenter={}", pct, offsetFromCenter);
                action.moveToElement(track, offsetFromCenter, 0).click().perform();

                Thread.sleep(150);
                String curr = sliderInput.getAttribute("value");
                int currentSliderValue = Integer.parseInt(curr);
                logger.info("After track click fallback: {} (target: {})", currentSliderValue, clampedTarget);
                if (currentSliderValue == clampedTarget && verifySliderValue(driver, sliderInput, clampedTarget)) {
                    logger.info("✅ Slider set by track click: {}", currentSliderValue);
                    sliderTimer.end();
                    return true;
                }
            } catch (Exception ex) {
                logger.error("❌ Track click strategy failed: {}", ex.getMessage());
            }

            // As last resort: attempt small drags across thumb center
            try {
                int attempt = 0;
                int currentSliderValue = currentValue;
                while (attempt < 12 && Math.abs(currentSliderValue - clampedTarget) > step) {
                    int remaining = clampedTarget - currentSliderValue;
                    int sign = remaining > 0 ? 1 : -1;
                    int move = sign * Math.min(60, Math.abs(remaining) / Math.max(1, step) * 20);
                    action.moveToElement(sliderThumb).clickAndHold().moveByOffset(move, 0).release().perform();
                    Thread.sleep(200);
                    String curr = sliderInput.getAttribute("value");
                    currentSliderValue = Integer.parseInt(curr);
                    logger.info("Drag fallback attempt {}: moved {} px -> value {}", attempt + 1, move, currentSliderValue);
                    attempt++;
                }
                if (Math.abs(currentSliderValue - clampedTarget) <= step && verifySliderValue(driver, sliderInput, clampedTarget)) {
                    logger.info("✅ Slider set by drag fallback: {}", currentSliderValue);
                    sliderTimer.end();
                    return true;
                }
            } catch (Exception ex) {
                logger.debug("Drag fallback failed: {}", ex.getMessage());
            }

            logger.error("❌ Slider failed. Final: {} (current val), Target: {}", sliderInput.getAttribute("value"), clampedTarget);
            sliderTimer.end();
            return false;

        } catch (Exception e) {
            logger.error("Slider adjustment exception: {}", e.getMessage());
            sliderTimer.end();
            return false;
        }
    }
    // Verify slider value by checking visual track position and all attributes
    // ALL checks must match expected value - no exceptions
    private static boolean verifySliderValue(WebDriver driver, WebElement sliderInput, int expected) {
        try {
            int min = Integer.parseInt(sliderInput.getAttribute("min"));
            int max = Integer.parseInt(sliderInput.getAttribute("max"));
            
            // Check 1: input value attribute
            String val = sliderInput.getAttribute("value");
            int inputValue = val != null ? Integer.parseInt(val) : Integer.MIN_VALUE;
            if (inputValue != expected) {
                logger.warn("Slider verification MISMATCH: input.value={} != expected={}", inputValue, expected);
                return false;
            }
            
            // Check 2: aria-valuenow attribute
            String aria = sliderInput.getAttribute("aria-valuenow");
            int ariaValue = aria != null ? Integer.parseInt(aria) : Integer.MIN_VALUE;
            if (ariaValue != expected) {
                logger.warn("Slider verification MISMATCH: aria-valuenow={} != expected={}", ariaValue, expected);
                return false;
            }
            
            // Check 3: Visual label showing correct value
            try {
                WebElement label = driver.findElement(By.cssSelector(".MuiSlider-valueLabelLabel"));
                String text = label.getText().replaceAll("[^0-9]", "");
                int visualValue = text.isEmpty() ? Integer.MIN_VALUE : Integer.parseInt(text);
                if (visualValue != expected) {
                    logger.warn("Slider verification MISMATCH: visible label={} != expected={}", visualValue, expected);
                    return false;
                }
            } catch (Exception e) {
                logger.warn("Slider verification: could not find/read visual label: {}", e.getMessage());
                return false;
            }
            
            // Check 4: Visual track position (calculate expected left % and compare)
            try {
                WebElement track = driver.findElement(By.cssSelector(".MuiSlider-track"));
                String style = track.getAttribute("style");
                // Extract "width: XX%;" from style
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("width:\\s*([\\d.]+)%");
                java.util.regex.Matcher m = p.matcher(style);
                if (m.find()) {
                    double actualWidthPct = Double.parseDouble(m.group(1));
                    double expectedWidthPct = ((double)(expected - min) / (max - min)) * 100.0;
                    double diff = Math.abs(actualWidthPct - expectedWidthPct);
                    // Allow 2% tolerance for rounding
                    if (diff > 2.0) {
                        logger.warn("Slider verification MISMATCH: track width={}% vs expected={}% (diff={}%)", 
                            actualWidthPct, expectedWidthPct, diff);
                        return false;
                    }
                }
            } catch (Exception e) {
                logger.debug("Slider track verification skipped: {}", e.getMessage());
            }
            
            logger.info("✅ Slider verification PASSED: all checks match expected={}", expected);
            return true;
            
        } catch (Exception e) {
            logger.warn("Slider verification exception: {}", e.getMessage());
            return false;
        }
    }

}

