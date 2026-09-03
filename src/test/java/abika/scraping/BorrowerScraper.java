package abika.scraping;

import com.abika.model.Borrower;
import com.abika.model.Investment;
import com.abika.services.DBService;
import com.abika.utils.ConfigReader;
import com.abika.utils.DroolsEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import abika.MethodTimer;
import abika.parsing.BorrowerDetailParser;
import abika.selenium.UIElementHandler;
import abika.selenium.WebDriverWaitManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;

/**
 * Handles borrower list scraping and processing
 */
public class BorrowerScraper {
    private static final Logger logger = LoggerFactory.getLogger(BorrowerScraper.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final By BORROWER_POPUP = By.cssSelector("div.sc-dtBdUo.hHvdph");
    private static final String PRIMARY_CARD_SELECTOR = "div.MuiBox-root.css-79elbk";
    private static final String BORROWER_ARROW_SELECTOR = "div[aria-label='View borrower details']";

    /**
     * Scrape and process borrowers from the loan list
     * Applies rules and handles lending decisions
     */
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

        MethodTimer npaBorrowersTimer = new MethodTimer("Loading NPA borrowers");
        Set<String> npaBorrowers = dbService.getNPABorrowersAsSet();
        npaBorrowersTimer.end();
        logger.info("📊 Total NPA Borrowers loaded: {}", npaBorrowers.size());
        if (npaBorrowers.isEmpty()) {
            logger.warn("⚠️ WARNING: NPA Borrowers list is empty! No NPA borrowers to filter.");
        }

        MethodTimer trustedBorrowersTimer = new MethodTimer("Loading trusted borrowers");
        Set<String> trustedBorrowers = dbService.getTrustedBorrowersAsSet();
        trustedBorrowersTimer.end();
        logger.info("📊 Total trusted borrowers loaded: {}", trustedBorrowers.size());

        MethodTimer currentlyLentTimer = new MethodTimer("Loading currently lent borrowers");
        Set<String> borrowersCurrentlyLent =
                new HashSet<>(dbService.getCurrentlyLendedBorrowers(ConfigReader.get(activateUser + "user")));
        currentlyLentTimer.end();
        logger.info("📊 Currently lent borrowers: {}", borrowersCurrentlyLent.size());

        boolean repeatedLoanConfig = Boolean.parseBoolean(ConfigReader.get("repeatedLoan"));
        long startTime = System.currentTimeMillis();

        Set<String> seenCards = new HashSet<>();
        Set<String> seenLoanIds = new HashSet<>();
        By cardLocator = By.cssSelector(PRIMARY_CARD_SELECTOR);
        List<WebElement> allCards = new ArrayList<>();

        // First batch is a page-readiness condition, not end-of-list detection. A transiently
        // empty list or a generated MUI class change must never be interpreted as "all cards seen".
        if (!waitForInitialBorrowerCards(driver)) {
            throw new IllegalStateException(
                    "Borrower list did not load: no borrower cards or 'View borrower details' controls became available"
            );
        }

        for (int retry = 0; retry < WebDriverWaitManager.MAX_RETRIES; retry++) {

            logger.info("retry: {}", retry + 1);

            double available = investment.getWalletAmount() - (investment.getReservedAmount() == null ? 0.0 : investment.getReservedAmount());
            if (available < investment.getLendAmtPerLoan()) {
                logger.info("Wallet balance insufficient (available: {}), stopping scrape.", available);
                break;
            }

            MethodTimer scrollTimer = new MethodTimer("scrollToLoadMoreCards (Retry " + (retry + 1) + ")");
            UIElementHandler.scrollToLoadMoreCards(driver);
            scrollTimer.end();

            MethodTimer cardWaitTimer = new MethodTimer("Waiting for unseen borrower cards");
            try {
                WebDriverWaitManager.getShortWait().until(driver1 ->
                        hasUnseenOrUncertainBorrowerCard(findBorrowerCards(driver1, cardLocator), seenCards)
                );
            } catch (TimeoutException e) {
                logger.info("No unseen borrower cards appeared after scroll; checking rendered batch before ending retries");
            }
            cardWaitTimer.end();

            allCards = findBorrowerCards(driver, cardLocator);
            logger.info("Found {} rendered cards after scroll (retry {})", allCards.size(), retry + 1);

            if (allCards.isEmpty()) {
                if (seenCards.isEmpty()) {
                    throw new IllegalStateException(
                            "Borrower list became empty before any borrower was processed; refusing to treat this as end-of-list"
                    );
                }
                logger.info("No borrower cards currently rendered after processing {} borrowers; ending retries", seenCards.size());
                break;
            }

            if (!hasUnseenOrUncertainBorrowerCard(allCards, seenCards)) {
                logger.info("✅ All currently rendered borrower cards were already processed. Ending retries.");
                break;
            }

            MethodTimer processCardsTimer = new MethodTimer("Processing cards batch (Retry " + (retry + 1) + ")");

            for (int i = 0; i < allCards.size(); i++) {

                double availableInner = investment.getWalletAmount() - (investment.getReservedAmount() == null ? 0.0 : investment.getReservedAmount());
                if (availableInner < investment.getLendAmtPerLoan())
                    break;

                WebElement card;
                String borrowerName;

                try {
                    if (i >= allCards.size()) break;
                    card = allCards.get(i);
                    borrowerName = extractBorrowerName(card);
                } catch (StaleElementReferenceException stale) {
                    logger.debug("Borrower card {} became stale; refreshing rendered card list", i);
                    allCards = findBorrowerCards(driver, cardLocator);
                    if (i >= allCards.size()) break;
                    card = allCards.get(i);
                    borrowerName = extractBorrowerName(card);
                }

                if (borrowerName == null || borrowerName.isEmpty() || seenCards.contains(borrowerName)) {
                    continue;
                }

                logger.info("{}-Opening borrower: {}", i + 1, borrowerName);

                MethodTimer cardClickTimer = new MethodTimer("clickCardArrowFast - " + borrowerName);
                boolean popupOpened = openBorrowerPopupWithSafeRecovery(driver, cardLocator, borrowerName, card);
                cardClickTimer.end();
                if (!popupOpened) {
                    logger.info("Borrower card rerendered out of the current batch before popup open; deferring borrower '{}' to a later retry", borrowerName);
                    continue;
                }

                seenCards.add(borrowerName);

                MethodTimer parseTimer = new MethodTimer("parseBorrowerDetails - " + borrowerName);
                Borrower borrower = BorrowerDetailParser.parseBorrowerDetails(driver);
                parseTimer.end();

                try {
                    validateBorrowerIdentity(borrowerName, borrower, seenLoanIds);
                } catch (IllegalStateException identityError) {
                    logger.error("CRITICAL borrower identity mismatch; aborting scrape: {}", identityError.getMessage());
                    UIElementHandler.closePopupFast(driver);
                    throw identityError;
                }

                if (npaBorrowers.contains(borrower.getName())) {
                    npaBorrowersInCurrentRun.add(borrower.getName());
                    MethodTimer closeTimer = new MethodTimer("closePopupFast - NPA");
                    UIElementHandler.closePopupFast(driver);
                    closeTimer.end();
                    logger.info("❌ NPA Borrower: {}. Popup closed.", borrower.getName());
                    continue;
                }

                if (borrowersCurrentlyLent.contains(borrower.getName())) {
                    MethodTimer closeTimer = new MethodTimer("closePopupFast - Existing");
                    UIElementHandler.closePopupFast(driver);
                    closeTimer.end();
                    logger.info("❌ Existing Borrower: {}. Popup closed.", borrower.getName());
                    continue;
                }

                if (trustedBorrowers.contains(borrower.getName()))
                    borrower.setTrusted(true);

                if (repeatedLoanConfig)
                    borrower.setRepeated(true);

                printBorrower(borrower);

                String failReason = com.abika.utils.RuleConditionEvaluator.evaluate(borrower, investment.getRuleName());
                if (failReason != null) {
                    com.abika.reporting.ExecutionMetrics.BorrowerRecord rec = new com.abika.reporting.ExecutionMetrics.BorrowerRecord(
                            investment.getRuleName(), borrower.getName(), borrower.getLoanAmount());
                    rec.setStatus("FAILED");
                    rec.setFailureReason(failReason);
                    rec.setSelectionTimeMs(System.currentTimeMillis());
                    metrics.addBorrowerRecord(rec);

                    MethodTimer closeTimer = new MethodTimer("closePopupFast - Rule Pre-check Failed");
                    UIElementHandler.closePopupFast(driver);
                    closeTimer.end();

                    logger.info("❌ Rule pre-check failed for: {}. Reason: {}. Popup closed.", borrower.getName(), failReason);
                    continue;
                }

                MethodTimer ruleTimer = new MethodTimer("droolsEngine.fireRuleByName - " + borrowerName);
                boolean fired = droolsEngine.fireRuleByName(borrower, investment.getRuleName());
                ruleTimer.end();

                if (fired) {
                    logger.info("✅ Rule fired for: {}. LendingAmount: {}", borrower.getName(), borrower.getLendingAmount());

                    MethodTimer addLoanTimer = new MethodTimer("clickAddLoanButton - " + borrowerName);
                    UIElementHandler.clickAddLoanButton(driver);
                    addLoanTimer.end();

                    investment.setLendAmtPerLoan((int) borrower.getLendingAmount());
                    investment.setLoanCounts(investment.getLoanCounts() + 1);
                    investment.setReservedAmount(investment.getReservedAmount() + borrower.getLendingAmount());

                    borrowerList.add(borrower);

                    logger.info("Number of Loans Selected: {} of rule type: {}",
                            investment.getLoanCounts(), investment.getRuleName());

                    com.abika.reporting.ExecutionMetrics.BorrowerRecord rec = new com.abika.reporting.ExecutionMetrics.BorrowerRecord(
                            investment.getRuleName(), borrower.getName(), borrower.getLoanAmount());
                    rec.setStatus("SELECTED");
                    rec.setSelectionTimeMs(System.currentTimeMillis());
                    metrics.addBorrowerRecord(rec);

                    MethodTimer closeTimer = new MethodTimer("closePopupFast - After Add Loan");
                    UIElementHandler.closePopupFast(driver);
                    closeTimer.end();
                } else {
                    MethodTimer closeTimer = new MethodTimer("closePopupFast - Rule Not Fired");
                    UIElementHandler.closePopupFast(driver);
                    closeTimer.end();
                    logger.info("❌ Rule not fired for: {}. Popup closed.", borrower.getName());

                    com.abika.reporting.ExecutionMetrics.BorrowerRecord rec = new com.abika.reporting.ExecutionMetrics.BorrowerRecord(
                            investment.getRuleName(), borrower.getName(), borrower.getLoanAmount());
                    rec.setStatus("FAILED");
                    rec.setFailureReason("Rule did not fire despite pre-check passing");
                    rec.setSelectionTimeMs(System.currentTimeMillis());
                    metrics.addBorrowerRecord(rec);
                }
            }

            processCardsTimer.end();
            logger.info("📊 Retry {} completed: processed {} unique borrowers so far",
                    retry + 1, seenCards.size());
        }

        overallTimer.end();

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Loan list popup open for: {} minutes",
                String.format("%.2f", duration / (1000.0 * 60)));
    }

    private static boolean waitForInitialBorrowerCards(WebDriver driver) {
        try {
            WebDriverWaitManager.getStandardWait().until(BorrowerScraper::hasBorrowerCardsInDom);
            return true;
        } catch (TimeoutException timeout) {
            logger.error("Borrower list readiness timeout: no cards or borrower detail controls became available");
            return false;
        }
    }

    private static boolean hasBorrowerCardsInDom(WebDriver driver) {
        Object count = ((JavascriptExecutor) driver).executeScript(
                "return document.querySelectorAll(arguments[0]).length || document.querySelectorAll(arguments[1]).length;",
                PRIMARY_CARD_SELECTOR,
                BORROWER_ARROW_SELECTOR
        );
        return count instanceof Number && ((Number) count).intValue() > 0;
    }

    /**
     * Prefer the fast historical card selector. If LenDenClub rotates its generated MUI class,
     * recover cards from the stable borrower-details aria-label and walk up to the nearest
     * MuiBox ancestor that also contains the borrower-name block.
     */
    @SuppressWarnings("unchecked")
    private static List<WebElement> findBorrowerCards(WebDriver driver, By primaryCardLocator) {
        List<WebElement> primaryCards = driver.findElements(primaryCardLocator);
        if (!primaryCards.isEmpty()) {
            return primaryCards;
        }

        Object fallback = ((JavascriptExecutor) driver).executeScript(
                "const arrows = Array.from(document.querySelectorAll(arguments[0]));" +
                "const cards = []; const seen = new Set();" +
                "for (const arrow of arrows) {" +
                "  let node = arrow.parentElement;" +
                "  while (node && node !== document.body) {" +
                "    if (node.matches && node.matches('div.MuiBox-root') && node.querySelector('div.css-69i1ev p.MuiTypography-root')) {" +
                "      if (!seen.has(node)) { seen.add(node); cards.push(node); }" +
                "      break;" +
                "    }" +
                "    node = node.parentElement;" +
                "  }" +
                "}" +
                "return cards;",
                BORROWER_ARROW_SELECTOR
        );

        if (fallback instanceof List<?>) {
            List<WebElement> fallbackCards = (List<WebElement>) fallback;
            if (!fallbackCards.isEmpty()) {
                logger.debug("Using stable aria-label fallback for {} borrower cards", fallbackCards.size());
            }
            return fallbackCards;
        }
        return new ArrayList<>();
    }

    private static boolean openBorrowerPopupWithSafeRecovery(
            WebDriver driver, By cardLocator, String borrowerName, WebElement initialCard) {
        try {
            UIElementHandler.clickCardArrowFast(driver, initialCard);
            return true;
        } catch (IllegalStateException firstFailure) {
            if (hasCause(firstFailure, StaleElementReferenceException.class)) {
                logger.info("Borrower card became stale before popup open for '{}'; refreshing card and retrying non-financial open once", borrowerName);
                WebElement refreshedCard = findRenderedCardByBorrowerName(findBorrowerCards(driver, cardLocator), borrowerName);
                if (refreshedCard == null) {
                    return false;
                }
                return openRefreshedCardWithTimeoutRecovery(driver, cardLocator, borrowerName, refreshedCard);
            }

            if (hasCause(firstFailure, TimeoutException.class)) {
                return recoverFromPopupOpenTimeout(driver, cardLocator, borrowerName);
            }

            throw firstFailure;
        }
    }

    private static boolean openRefreshedCardWithTimeoutRecovery(
            WebDriver driver, By cardLocator, String borrowerName, WebElement refreshedCard) {
        try {
            UIElementHandler.clickCardArrowFast(driver, refreshedCard);
            return true;
        } catch (IllegalStateException secondFailure) {
            if (hasCause(secondFailure, StaleElementReferenceException.class)) {
                logger.info("Borrower card became stale again before popup open for '{}'; deferring to a later retry", borrowerName);
                return false;
            }
            if (hasCause(secondFailure, TimeoutException.class)) {
                return recoverFromPopupOpenTimeout(driver, cardLocator, borrowerName);
            }
            throw secondFailure;
        }
    }

    private static boolean recoverFromPopupOpenTimeout(
            WebDriver driver, By cardLocator, String borrowerName) {
        logger.info("Borrower popup not visible within initial wait for '{}'; waiting once more without clicking", borrowerName);

        try {
            WebDriverWaitManager.getShortWait().until(BorrowerScraper::isBorrowerPopupVisible);
            logger.info("Borrower popup for '{}' became visible during grace wait; continuing without retry click", borrowerName);
            return true;
        } catch (TimeoutException graceTimeout) {
            // Continue only if the DOM is unambiguous: no borrower popup element at all.
        }

        List<WebElement> popupElements = driver.findElements(BORROWER_POPUP);
        if (!popupElements.isEmpty()) {
            throw new IllegalStateException(
                    "Borrower popup element exists after open timeout but is not safely visible; aborting to avoid a delayed double-click"
            );
        }

        WebElement refreshedCard = findRenderedCardByBorrowerName(findBorrowerCards(driver, cardLocator), borrowerName);
        if (refreshedCard == null) {
            logger.info("Borrower '{}' is no longer rendered after popup timeout; deferring to a later retry", borrowerName);
            return false;
        }

        logger.info("No borrower popup appeared for '{}' after grace wait; retrying the non-financial popup open once with a refreshed card", borrowerName);
        try {
            UIElementHandler.clickCardArrowFast(driver, refreshedCard);
            return true;
        } catch (IllegalStateException retryFailure) {
            if (hasCause(retryFailure, StaleElementReferenceException.class)) {
                logger.info("Borrower card became stale during timeout recovery for '{}'; deferring to a later retry", borrowerName);
                return false;
            }
            throw retryFailure;
        }
    }

    private static boolean isBorrowerPopupVisible(WebDriver driver) {
        for (WebElement popup : driver.findElements(BORROWER_POPUP)) {
            try {
                if (popup.isDisplayed()) {
                    return true;
                }
            } catch (StaleElementReferenceException ignored) {
                // The popup DOM is transitioning; keep waiting for a stable visible popup.
            }
        }
        return false;
    }

    private static WebElement findRenderedCardByBorrowerName(List<WebElement> cards, String borrowerName) {
        if (cards == null || borrowerName == null) {
            return null;
        }

        for (WebElement candidate : cards) {
            try {
                String candidateName = extractBorrowerName(candidate);
                if (normalizeBorrowerName(borrowerName).equalsIgnoreCase(normalizeBorrowerName(candidateName))) {
                    return candidate;
                }
            } catch (StaleElementReferenceException ignored) {
                // Keep looking through the fresh rendered batch.
            }
        }
        return null;
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> causeType) {
        Throwable current = error;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean hasUnseenOrUncertainBorrowerCard(List<WebElement> cards, Set<String> seenCards) {
        if (cards == null || cards.isEmpty()) {
            return false;
        }

        boolean readableCardFound = false;
        for (WebElement card : cards) {
            try {
                String borrowerName = extractBorrowerName(card);
                if (borrowerName == null || borrowerName.isEmpty()) {
                    continue;
                }

                readableCardFound = true;
                if (!seenCards.contains(borrowerName)) {
                    return true;
                }
            } catch (StaleElementReferenceException stale) {
                return true;
            }
        }

        return !readableCardFound;
    }

    private static String extractBorrowerName(WebElement card) {
        try {
            return card.findElement(
                By.cssSelector("div.css-69i1ev p.MuiTypography-root")
            ).getText().trim();
        } catch (StaleElementReferenceException stale) {
            throw stale;
        } catch (Exception e) {
            try {
                return card.findElement(
                    By.xpath(".//div[contains(@class,'css-69i1ev')]//p[1]")
                ).getText().trim();
            } catch (StaleElementReferenceException stale) {
                throw stale;
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static void validateBorrowerIdentity(String cardBorrowerName, Borrower borrower, Set<String> seenLoanIds) {
        if (borrower == null) {
            throw new IllegalStateException("Borrower parser returned null for card '" + cardBorrowerName + "'");
        }

        String popupBorrowerName = borrower.getName();
        if (popupBorrowerName == null || popupBorrowerName.isBlank()) {
            throw new IllegalStateException("Popup borrower name is missing for card '" + cardBorrowerName + "'");
        }

        if (!normalizeBorrowerName(cardBorrowerName).equalsIgnoreCase(normalizeBorrowerName(popupBorrowerName))) {
            throw new IllegalStateException(
                    "card borrower '" + cardBorrowerName + "' != popup borrower '" + popupBorrowerName
                            + "' (loanId=" + borrower.getLoanId() + ")");
        }

        String loanId = borrower.getLoanId();
        if (loanId == null || loanId.isBlank()) {
            throw new IllegalStateException("Popup loanId is missing for borrower '" + popupBorrowerName + "'");
        }

        if (!seenLoanIds.add(loanId.trim())) {
            throw new IllegalStateException(
                    "duplicate loanId '" + loanId + "' encountered again for borrower '" + popupBorrowerName + "'");
        }
    }

    private static String normalizeBorrowerName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private static void printBorrower(Borrower borrower) {
        try {
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(borrower);
            logger.info("Borrower: {}", json);
        } catch (Exception e) {
            logger.info("Error serializing borrower: {}", e.getMessage());
        }
    }
}
