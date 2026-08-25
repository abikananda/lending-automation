package abika.parsing;

import com.abika.model.Borrower;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import abika.MethodTimer;
import abika.selenium.UIElementHandler;
import abika.selenium.WebDriverWaitManager;

import java.util.Map;
import java.util.HashMap;

/**
 * Handles extraction and parsing of borrower details from the UI
 */
public class BorrowerDetailParser {
    private static final Logger logger = LoggerFactory.getLogger(BorrowerDetailParser.class);

    /**
     * Parse complete borrower details from the popup
     * Uses batch JavaScript calls per panel (reduces 10 calls → 4 calls, 60% reduction)
     * Falls back to element-based extraction if JS fails
     */
    public static Borrower parseBorrowerDetails(WebDriver driver) {
        MethodTimer parseTimer = new MethodTimer("parseBorrowerDetails - Batch extraction per panel");

        Borrower b = new Borrower();

        try {
            // Panel 1: Risk Category & Score - Extract 2 fields in 1 batch call
            MethodTimer panel1Timer = new MethodTimer("Panel 1: Batch extract (Bureau Score, LenDen Score)");
            UIElementHandler.expandPanelFast(driver, "Risk Category & Score");
            Map<String, Object> panel1Data = extractPanelFieldsBatch(driver, 
                new String[]{"Bureau Score", "LenDenClub Score"},
                new String[]{"bureauScore", "lendenScore"});
            b.setCreditScore(TextParsingUtils.parseDouble((String) panel1Data.get("bureauScore")));
            b.setLendenScore(TextParsingUtils.parseDouble((String) panel1Data.get("lendenScore")));
            panel1Timer.end();

            // Panel 2: Professional Details - Extract 2 fields in 1 batch call
            MethodTimer panel2Timer = new MethodTimer("Panel 2: Batch extract (Occupation, Monthly Income)");
            UIElementHandler.expandPanelFast(driver, "Professional Details");
            Map<String, Object> panel2Data = extractPanelFieldsBatch(driver,
                new String[]{"Occupation", "Monthly Income"},
                new String[]{"occupation", "income"});
            b.setBorrowerType(((String) panel2Data.get("occupation")).trim());
            b.setIncome(TextParsingUtils.parseDouble((String) panel2Data.get("income")));
            panel2Timer.end();

            // Panel 3: Personal Details - Extract 2 fields in 1 batch call
            MethodTimer panel3Timer = new MethodTimer("Panel 3: Batch extract (Name, Age)");
            UIElementHandler.expandPanelFast(driver, "Personal Details");
            Map<String, Object> panel3Data = extractPanelFieldsBatch(driver,
                new String[]{"Name", "Age"},
                new String[]{"name", "age"});
            b.setName(((String) panel3Data.get("name")).trim());
            b.setAge(TextParsingUtils.parseInt((String) panel3Data.get("age")));
            panel3Timer.end();

            // Panel 4: Loan Details - Extract 4 fields in 1 batch call
            MethodTimer panel4Timer = new MethodTimer("Panel 4: Batch extract (Loan ID, Amount, Tenure, Interest)");
            UIElementHandler.expandPanelFast(driver, "Loan Details");
            Map<String, Object> panel4Data = extractPanelFieldsBatch(driver,
                new String[]{"Loan ID", "Loan Amount", "Tenure", "Annualized Interest Rate"},
                new String[]{"loanId", "loanAmount", "tenure", "interestRate"});
            b.setLoanId(((String) panel4Data.get("loanId")).trim());
            b.setLoanAmount(TextParsingUtils.parseDouble((String) panel4Data.get("loanAmount")));
            b.setTenure(TextParsingUtils.parseIntWithMonths((String) panel4Data.get("tenure")));
            b.setInterestRate(TextParsingUtils.parseDoubleWithPercent((String) panel4Data.get("interestRate")));
            panel4Timer.end();

        } catch (Exception e) {
            logger.info("Error extracting borrower details via batch JavaScript: {}", e.getMessage());
            fallbackParseBorrowerDetails(driver, b);
        }

        parseTimer.end();
        return b;
    }

    /**
     * Extract multiple fields from currently open panel in a single batch JavaScript call
     * Reduces 10 individual JS calls → 4 batch calls (60% reduction!)
     * @param driver WebDriver instance
     * @param labels Array of field labels to extract (e.g., "Bureau Score", "Lenden Score")
     * @param keys Array of keys for returned map (e.g., "bureauScore", "lendenScore")
     * @return Map containing all extracted fields from the current panel
     */
    private static Map<String, Object> extractPanelFieldsBatch(WebDriver driver, String[] labels, String[] keys) {
        if (labels.length != keys.length) {
            logger.warn("Labels and keys arrays have different lengths");
            return new java.util.HashMap<>();
        }

        // Build JavaScript that extracts multiple fields in one call
        StringBuilder jsBuilder = new StringBuilder();
        jsBuilder.append("function extractLabelValue(label) {\n");
        jsBuilder.append("    var xpath = \"//div[normalize-space()='\" + label + \"']/ancestor::div[1]/following-sibling::div[1]//div\";\n");
        jsBuilder.append("    var result = document.evaluate(xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);\n");
        jsBuilder.append("    return result.singleNodeValue ? result.singleNodeValue.innerText : '';\n");
        jsBuilder.append("}\n");
        jsBuilder.append("return {\n");

        for (int i = 0; i < labels.length; i++) {
            jsBuilder.append("    ").append(keys[i]).append(": extractLabelValue('").append(labels[i]).append("')");
            if (i < labels.length - 1) {
                jsBuilder.append(",");
            }
            jsBuilder.append("\n");
        }

        jsBuilder.append("};");

        String batchScript = jsBuilder.toString();
        
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(batchScript);

            if (result instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (java.util.Map<String, Object>) result;
                logger.debug("✅ Batch JS extraction successful: {} fields extracted from panel in single call", data.size());
                return data;
            }

            logger.warn("Batch JS extraction returned unexpected type: {}", result.getClass().getName());
            return new java.util.HashMap<>();
        } catch (Exception e) {
            logger.warn("Batch JS extraction failed: {}", e.getMessage());
            return new java.util.HashMap<>();
        }
    }

    /**
     * Fallback method if JavaScript extraction fails
     */
    private static void fallbackParseBorrowerDetails(WebDriver driver, Borrower b) {
        logger.info("Using fallback method to extract borrower details");
        try {
            UIElementHandler.expandPanel(driver, WebDriverWaitManager.getUltraShortWait(), "Risk Category & Score");
            b.setCreditScore(TextParsingUtils.safelyParseDouble(By.xpath(getXPath("Bureau Score")), driver));
            b.setLendenScore(TextParsingUtils.safelyParseDouble(By.xpath(getXPath("LenDenClub Score")), driver));

            UIElementHandler.expandPanel(driver, WebDriverWaitManager.getUltraShortWait(), "Professional Details");
            b.setBorrowerType(TextParsingUtils.safelyParseString(By.xpath(getXPath("Occupation")), driver));
            b.setIncome(TextParsingUtils.safelyParseDouble(By.xpath(getXPath("Monthly Income")), driver));

            UIElementHandler.expandPanel(driver, WebDriverWaitManager.getUltraShortWait(), "Personal Details");
            b.setName(TextParsingUtils.safelyParseString(By.xpath(getXPath("Name")), driver));
            b.setAge(TextParsingUtils.safelyParseInt(By.xpath(getXPath("Age")), driver));

            UIElementHandler.expandPanel(driver, WebDriverWaitManager.getUltraShortWait(), "Loan Details");
            b.setLoanId(TextParsingUtils.safelyParseString(By.xpath(getXPath("Loan ID")), driver));
            b.setLoanAmount(TextParsingUtils.safelyParseDouble(By.xpath(getXPath("Loan Amount")), driver));
            b.setTenure(TextParsingUtils.safelyParseIntWithMonths(By.xpath(getXPath("Tenure")), driver));
            b.setInterestRate(TextParsingUtils.safelyParseDoubleWithPercent(By.xpath(getXPath("Annualized Interest Rate")), driver));
        } catch (Exception e) {
            logger.info("Fallback extraction also failed: {}", e.getMessage());
        }
    }

    /**
     * Build XPath for extracting field values from the borrower details popup
     */
    private static String getXPath(String label) {
        return "//div[normalize-space()='" + label + "']/ancestor::div[1]/following-sibling::div[1]//div";
    }
}

