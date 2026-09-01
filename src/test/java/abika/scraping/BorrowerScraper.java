package abika.scraping;

import com.abika.model.Borrower;
import com.abika.model.Investment;
import com.abika.services.DBService;
import com.abika.utils.ConfigReader;
import com.abika.utils.DroolsEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.By;
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

        // Load all NPA borrowers into HashSet for O(1) lookup (batch load instead of N+1 queries)
        MethodTimer npaBorrowersTimer = new MethodTimer("Loading NPA borrowers");
        Set<String> npaBorrowers = dbService.getNPABorrowersAsSet();
        npaBorrowersTimer.end();
        logger.info("📊 Total NPA Borrowers loaded: {}", npaBorrowers.size());
        if (npaBorrowers.isEmpty()) {
            logger.warn("⚠️ WARNING: NPA Borrowers list is empty! No NPA borrowers to filter.");
        }

        // Load all trusted borrowers into HashSet for O(1) lookup (batch load instead of N+1 queries)
        MethodTimer trustedBorrowersTimer = new MethodTimer("Loading trusted borrowers");
        Set<String> trustedBorrowers = dbService.getTrustedBorrowersAsSet();
        trustedBorrowersTimer.end();
        logger.info("📊 Total trusted borrowers loaded: {}", trustedBorrowers.size());

        // Load currently lent borrowers
        MethodTimer currentlyLentTimer = new MethodTimer("Loading currently lent borrowers");
        Set<String> borrowersCurrentlyLent =
                new HashSet<>(dbService.getCurrentlyLendedBorrowers(ConfigReader.get(activateUser + "user")));
        currentlyLentTimer.end();
        logger.info("📊 Currently lent borrowers: {}", borrowersCurrentlyLent.size());

        // Cache the repeatedLoan config outside loop
        boolean repeatedLoanConfig = Boolean.parseBoolean(ConfigReader.get("repeatedLoan"));

        long startTime = System.currentTimeMillis();

        Set<String> seenCards = new HashSet<>();
        By cardLocator = By.cssSelector("div.MuiBox-root.css-79elbk");

        // Fetch cards once per batch. Individual card elements are reused until Selenium
        // reports that the virtualized list has made them stale; only then is the list refreshed.
        List<WebElement> allCards = new ArrayList<>();
        int previousCardCount = 0;

        for (int retry = 0; retry < WebDriverWaitManager.MAX_RETRIES; retry++) {

            logger.info("retry: {}", retry + 1);

            // Check available balance excluding reserved amount
            double available = investment.getWalletAmount() - (investment.getReservedAmount() == null ? 0.0 : investment.getReservedAmount());
            if (available < investment.getLendAmtPerLoan()) {
                logger.info("Wallet balance insufficient (available: {}), stopping scrape.", available);
                break;
            }

            // Auto-scroll to load more cards before processing. The helper only performs
            // the scroll; synchronization happens once below by waiting for card-count growth.
            MethodTimer scrollTimer = new MethodTimer("scrollToLoadMoreCards (Retry " + (retry + 1) + ")");
            UIElementHandler.scrollToLoadMoreCards(driver);
            scrollTimer.end();

            // Wait for new cards to appear after scroll.
            MethodTimer cardWaitTimer = new MethodTimer("Waiting for cards to load");
            try {
                final int prevCardCount = previousCardCount;
                WebDriverWaitManager.getShortWait().until(
                    driver1 -> driver1.findElements(cardLocator).size() > prevCardCount
                );
            } catch (TimeoutException e) {
                logger.info("No new cards appeared after scroll (reached end of list)");
            }
            cardWaitTimer.end();

            // Batch-fetch cards once per retry.
            allCards = driver.findElements(cardLocator);
            logger.info("Found {} total cards after scroll (retry {})", allCards.size(), retry + 1);

            if (allCards.size() == previousCardCount) {
                logger.info("✅ No new cards loaded. Ending retries.");
                break;
            }

            previousCardCount = allCards.size();

            MethodTimer processCardsTimer = new MethodTimer("Processing cards batch (Retry " + (retry + 1) + ")");

            for (int i = 0; i < allCards.size(); i++) {

                // Check available balance excluding reserved amount
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
                    // LenDenClub virtualizes/rerenders the list after popup interaction.
                    // Refresh only when staleness is real instead of doing a full DOM query
                    // before every borrower.
                    logger.debug("Borrower card {} became stale; refreshing rendered card list", i);
                    allCards = driver.findElements(cardLocator);
                    if (i >= allCards.size()) break;
                    card = allCards.get(i);
                    borrowerName = extractBorrowerName(card);
                }

                // Keep the existing borrower-name identity logic unchanged.
                if (borrowerName == null || borrowerName.isEmpty() || seenCards.contains(borrowerName)) {
                    continue;
                }

                seenCards.add(borrowerName);
                logger.info("{}-Opening borrower: {}", i + 1, borrowerName);

                // Click card arrow and get popup reference
                MethodTimer cardClickTimer = new MethodTimer("clickCardArrowFast - " + borrowerName);
                UIElementHandler.clickCardArrowFast(driver, card);
                cardClickTimer.end();

                // Parse borrower details while popup is loading
                MethodTimer parseTimer = new MethodTimer("parseBorrowerDetails - " + borrowerName);
                Borrower borrower = BorrowerDetailParser.parseBorrowerDetails(driver);
                parseTimer.end();

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

                // Pre-evaluate rule conditions to capture failing condition reasons
                String failReason = com.abika.utils.RuleConditionEvaluator.evaluate(borrower, investment.getRuleName());
                if (failReason != null) {
                    // Record failure reason in metrics
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

                    // Click "Add Loan" button with reduced wait
                    MethodTimer addLoanTimer = new MethodTimer("clickAddLoanButton - " + borrowerName);
                    UIElementHandler.clickAddLoanButton(driver);
                    addLoanTimer.end();

                    investment.setLendAmtPerLoan((int) borrower.getLendingAmount());
                    investment.setLoanCounts(investment.getLoanCounts() + 1);
                    // Reserve amount instead of deducting immediately. Actual wallet deduction happens after successful lending.
                    investment.setReservedAmount(investment.getReservedAmount() + borrower.getLendingAmount());

                    borrowerList.add(borrower);

                    logger.info("Number of Loans Selected: {} of rule type: {}",
                            investment.getLoanCounts(), investment.getRuleName());

                    // Add borrower record as SELECTED for metrics
                    com.abika.reporting.ExecutionMetrics.BorrowerRecord rec = new com.abika.reporting.ExecutionMetrics.BorrowerRecord(
                            investment.getRuleName(), borrower.getName(), borrower.getLoanAmount());
                    rec.setStatus("SELECTED");
                    rec.setSelectionTimeMs(System.currentTimeMillis());
                    metrics.addBorrowerRecord(rec);

                    // Close popup after adding loan
                    MethodTimer closeTimer = new MethodTimer("closePopupFast - After Add Loan");
                    UIElementHandler.closePopupFast(driver);
                    closeTimer.end();
                } else {
                    MethodTimer closeTimer = new MethodTimer("closePopupFast - Rule Not Fired");
                    UIElementHandler.closePopupFast(driver);
                    closeTimer.end();
                    logger.info("❌ Rule not fired for: {}. Popup closed.", borrower.getName());

                    // Record as failed with generic reason
                    com.abika.reporting.ExecutionMetrics.BorrowerRecord rec = new com.abika.reporting.ExecutionMetrics.BorrowerRecord(
                            investment.getRuleName(), borrower.getName(), borrower.getLoanAmount());
                    rec.setStatus("FAILED");
                    rec.setFailureReason("Rule did not fire despite pre-check passing");
                    rec.setSelectionTimeMs(System.currentTimeMillis());
                    metrics.addBorrowerRecord(rec);
                }
            }

            processCardsTimer.end();
            logger.info("📊 Retry {} completed: processed {} total borrowers this batch",
                    retry + 1, seenCards.size());
        }

        overallTimer.end();

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Loan list popup open for: {} minutes",
                String.format("%.2f", duration / (1000.0 * 60)));
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

    /**
     * Print borrower details in JSON format for logging.
     * Reuse the ObjectMapper to avoid rebuilding serialization metadata for every borrower.
     */
    private static void printBorrower(Borrower borrower) {
        try {
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(borrower);
            logger.info("Borrower: {}", json);
        } catch (Exception e) {
            logger.info("Error serializing borrower: {}", e.getMessage());
        }
    }
}
