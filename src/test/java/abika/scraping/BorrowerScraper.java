package abika.scraping;

import abika.MethodTimer;
import abika.parsing.BorrowerDetailParser;
import abika.selenium.UIElementHandler;
import abika.selenium.WebDriverWaitManager;
import com.abika.model.Borrower;
import com.abika.model.Investment;
import com.abika.services.DBService;
import com.abika.utils.ConfigReader;
import com.abika.utils.DroolsEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fast, fail-safe borrower scraper.
 * Keeps the original simple retry/batch structure while avoiding repeated DOM scans and
 * validating card/popup identity before any financial action.
 */
public class BorrowerScraper {
    private static final Logger logger = LoggerFactory.getLogger(BorrowerScraper.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final By CARD_LOCATOR = By.cssSelector("div.MuiBox-root.css-79elbk");
    private static final String CARD_SELECTOR = "div.MuiBox-root.css-79elbk";

    public static void scrapeAndProcessBorrowers(
            WebDriver driver,
            DBService dbService,
            DroolsEngine droolsEngine,
            Investment investment,
            List<Borrower> borrowerList,
            List<String> npaBorrowersInCurrentRun,
            String activateUser,
            com.abika.reporting.ExecutionMetrics metrics) {

        MethodTimer overallTimer = new MethodTimer("scrapeAndProcessBorrowers");
        long startTime = System.currentTimeMillis();

        Set<String> npaBorrowers = normalizeSet(dbService.getNPABorrowersAsSet());
        Set<String> trustedBorrowers = normalizeSet(dbService.getTrustedBorrowersAsSet());
        Set<String> borrowersCurrentlyLent = normalizeSet(
                dbService.getCurrentlyLendedBorrowers(ConfigReader.get(activateUser + "user")));

        logger.info("📊 Total NPA Borrowers loaded: {}", npaBorrowers.size());
        logger.info("📊 Total trusted borrowers loaded: {}", trustedBorrowers.size());
        logger.info("📊 Currently lent borrowers: {}", borrowersCurrentlyLent.size());

        boolean repeatedLoanConfig = Boolean.parseBoolean(ConfigReader.get("repeatedLoan"));
        Set<String> seenCards = new HashSet<>();
        Set<String> seenLoanIds = new HashSet<>();

        for (int retry = 0; retry < WebDriverWaitManager.MAX_RETRIES; retry++) {
            logger.info("retry: {}", retry + 1);

            double lendPerLoan = Math.max(0, investment.getLendAmtPerLoan());
            double available = investment.getWalletAmount()
                    - (investment.getReservedAmount() == null ? 0.0 : investment.getReservedAmount());
            if (lendPerLoan > 0 && available < lendPerLoan) {
                logger.info("Wallet balance insufficient (available: {}), stopping scrape.", available);
                break;
            }

            UIElementHandler.scrollToLoadMoreCards(driver);

            List<String> names = waitForRenderedNames(driver);
            List<WebElement> cards = driver.findElements(CARD_LOCATOR);
            logger.info("Found {} rendered cards after scroll (retry {})", cards.size(), retry + 1);

            if (cards.isEmpty()) {
                if (retry == 0) {
                    throw new IllegalStateException("Borrower list opened but no borrower cards were rendered");
                }
                break;
            }

            boolean hasUnseen = false;
            for (String name : names) {
                if (!normalize(name).isEmpty() && !seenCards.contains(normalize(name))) {
                    hasUnseen = true;
                    break;
                }
            }
            if (!hasUnseen) {
                logger.info("✅ All currently rendered borrower cards were already processed. Ending retries.");
                break;
            }

            int limit = Math.min(cards.size(), names.size());
            for (int i = 0; i < limit; i++) {
                double availableInner = investment.getWalletAmount()
                        - (investment.getReservedAmount() == null ? 0.0 : investment.getReservedAmount());
                if (investment.getLendAmtPerLoan() > 0 && availableInner < investment.getLendAmtPerLoan()) {
                    break;
                }

                String cardName = names.get(i) == null ? "" : names.get(i).trim();
                String normalizedCardName = normalize(cardName);
                if (normalizedCardName.isEmpty() || seenCards.contains(normalizedCardName)) continue;

                WebElement card = cards.get(i);
                logger.info("{}-Opening borrower: {}", i + 1, cardName);

                boolean opened = openBorrowerPopup(driver, card, cardName);
                if (!opened) {
                    logger.info("Borrower '{}' rerendered out of current batch; deferring to a later retry", cardName);
                    continue;
                }

                seenCards.add(normalizedCardName);

                Borrower borrower = BorrowerDetailParser.parseBorrowerDetails(driver);
                validateBorrowerIdentity(cardName, borrower, seenLoanIds);

                String borrowerKey = normalize(borrower.getName());
                if (npaBorrowers.contains(borrowerKey)) {
                    npaBorrowersInCurrentRun.add(borrower.getName());
                    UIElementHandler.closePopupFast(driver);
                    logger.info("❌ NPA Borrower: {}. Popup closed.", borrower.getName());
                    continue;
                }

                if (borrowersCurrentlyLent.contains(borrowerKey)) {
                    UIElementHandler.closePopupFast(driver);
                    logger.info("❌ Existing Borrower: {}. Popup closed.", borrower.getName());
                    continue;
                }

                borrower.setTrusted(trustedBorrowers.contains(borrowerKey));
                if (repeatedLoanConfig) borrower.setRepeated(true);

                printBorrower(borrower);

                String failReason = com.abika.utils.RuleConditionEvaluator.evaluate(
                        borrower, investment.getRuleName());
                if (failReason != null) {
                    com.abika.reporting.ExecutionMetrics.BorrowerRecord rec =
                            new com.abika.reporting.ExecutionMetrics.BorrowerRecord(
                                    investment.getRuleName(), borrower.getName(), borrower.getLoanAmount());
                    rec.setStatus("FAILED");
                    rec.setFailureReason(failReason);
                    rec.setSelectionTimeMs(System.currentTimeMillis());
                    metrics.addBorrowerRecord(rec);

                    UIElementHandler.closePopupFast(driver);
                    logger.info("❌ Rule pre-check failed for: {}. Reason: {}. Popup closed.",
                            borrower.getName(), failReason);
                    continue;
                }

                boolean fired = droolsEngine.fireRuleByName(borrower, investment.getRuleName());
                if (fired) {
                    logger.info("✅ Rule fired for: {}. LendingAmount: {}",
                            borrower.getName(), borrower.getLendingAmount());

                    UIElementHandler.clickAddLoanButton(driver);
                    investment.setLendAmtPerLoan((int) borrower.getLendingAmount());
                    investment.setLoanCounts(investment.getLoanCounts() + 1);
                    investment.setReservedAmount(investment.getReservedAmount() + borrower.getLendingAmount());
                    borrowerList.add(borrower);

                    com.abika.reporting.ExecutionMetrics.BorrowerRecord rec =
                            new com.abika.reporting.ExecutionMetrics.BorrowerRecord(
                                    investment.getRuleName(), borrower.getName(), borrower.getLoanAmount());
                    rec.setStatus("SELECTED");
                    rec.setSelectionTimeMs(System.currentTimeMillis());
                    metrics.addBorrowerRecord(rec);

                    logger.info("Number of Loans Selected: {} of rule type: {}",
                            investment.getLoanCounts(), investment.getRuleName());
                    UIElementHandler.closePopupFast(driver);
                } else {
                    UIElementHandler.closePopupFast(driver);
                    logger.info("❌ Rule not fired for: {}. Popup closed.", borrower.getName());

                    com.abika.reporting.ExecutionMetrics.BorrowerRecord rec =
                            new com.abika.reporting.ExecutionMetrics.BorrowerRecord(
                                    investment.getRuleName(), borrower.getName(), borrower.getLoanAmount());
                    rec.setStatus("FAILED");
                    rec.setFailureReason("Rule did not fire despite pre-check passing");
                    rec.setSelectionTimeMs(System.currentTimeMillis());
                    metrics.addBorrowerRecord(rec);
                }
            }

            logger.info("📊 Retry {} completed: processed {} unique borrowers so far",
                    retry + 1, seenCards.size());
        }

        overallTimer.end();
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Loan list popup open for: {} minutes",
                String.format("%.2f", duration / (1000.0 * 60)));
    }

    private static boolean openBorrowerPopup(WebDriver driver, WebElement initialCard, String borrowerName) {
        try {
            UIElementHandler.clickCardArrowFast(driver, initialCard);
            return true;
        } catch (IllegalStateException first) {
            if (!hasCause(first, StaleElementReferenceException.class)) throw first;

            logger.debug("Card became stale before popup open for '{}'; resolving it once from live DOM", borrowerName);
            WebElement refreshed = findRenderedCardByName(driver, borrowerName);
            if (refreshed == null) return false;
            UIElementHandler.clickCardArrowFast(driver, refreshed);
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> waitForRenderedNames(WebDriver driver) {
        try {
            return WebDriverWaitManager.getShortWait().until(d -> {
                Object value = ((JavascriptExecutor) d).executeScript(
                        "return Array.from(document.querySelectorAll(arguments[0])).map(card => {" +
                        "const n=card.querySelector('div.css-69i1ev p.MuiTypography-root') || " +
                        "card.querySelector('div[class*=css-69i1ev] p');" +
                        "return n ? n.textContent.trim() : '';});",
                        CARD_SELECTOR);
                List<String> result = (List<String>) value;
                return result != null && !result.isEmpty() ? result : null;
            });
        } catch (Exception e) {
            Object value = ((JavascriptExecutor) driver).executeScript(
                    "return Array.from(document.querySelectorAll(arguments[0])).map(card => {" +
                    "const n=card.querySelector('div.css-69i1ev p.MuiTypography-root') || " +
                    "card.querySelector('div[class*=css-69i1ev] p');" +
                    "return n ? n.textContent.trim() : '';});",
                    CARD_SELECTOR);
            return value instanceof List ? (List<String>) value : new ArrayList<>();
        }
    }

    private static WebElement findRenderedCardByName(WebDriver driver, String borrowerName) {
        String expected = normalize(borrowerName);
        for (WebElement candidate : driver.findElements(CARD_LOCATOR)) {
            try {
                String name = candidate.findElement(
                        By.cssSelector("div.css-69i1ev p.MuiTypography-root")).getText();
                if (normalize(name).equals(expected)) return candidate;
            } catch (StaleElementReferenceException ignored) {
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void validateBorrowerIdentity(
            String cardBorrowerName, Borrower borrower, Set<String> seenLoanIds) {
        if (borrower == null) {
            throw new IllegalStateException("Borrower parser returned null for card '" + cardBorrowerName + "'");
        }
        if (!normalize(cardBorrowerName).equals(normalize(borrower.getName()))) {
            throw new IllegalStateException(
                    "Borrower identity mismatch: card='" + cardBorrowerName +
                            "', popup='" + borrower.getName() + "', loanId=" + borrower.getLoanId());
        }
        String loanId = borrower.getLoanId();
        if (loanId == null || loanId.isBlank()) {
            throw new IllegalStateException("Popup loanId is missing for borrower '" + borrower.getName() + "'");
        }
        if (!seenLoanIds.add(loanId.trim())) {
            throw new IllegalStateException(
                    "Duplicate loanId '" + loanId + "' encountered in the same rule run");
        }
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }

    private static Set<String> normalizeSet(java.util.Collection<String> values) {
        Set<String> result = new HashSet<>();
        if (values == null) return result;
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private static void printBorrower(Borrower borrower) {
        try {
            logger.info("Borrower: {}",
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(borrower));
        } catch (Exception e) {
            logger.debug("Error serializing borrower: {}", e.getMessage());
        }
    }
}
