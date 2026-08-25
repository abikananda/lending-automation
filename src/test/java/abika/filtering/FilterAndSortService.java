package abika.filtering;

import com.abika.utils.ConfigReader;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import abika.MethodTimer;
import abika.selenium.UIElementHandler;

/**
 * Handles filter and sort operations on the loans list
 */
public class FilterAndSortService {
    private static final Logger logger = LoggerFactory.getLogger(FilterAndSortService.class);

    /**
     * Apply filters and sorting to the borrower list
     */
    public static void applyFiltersAndSort(WebDriver driver, WebDriverWait wait) {
        MethodTimer overallTimer = new MethodTimer("applyFiltersAndSort");

        MethodTimer filterTimer = new MethodTimer("Open Filter & Sort");
        UIElementHandler.clickElement(driver, wait, By.xpath("//div[contains(@class, 'MuiBox-root')]//span[contains(normalize-space(.), 'Filter & Sort')]"));
        filterTimer.end();

        MethodTimer selectAllTimer = new MethodTimer("Select All Checkbox");
        UIElementHandler.toggleCheckbox(driver, "//label[span[text()='Select All']]/preceding-sibling::span//input[@type='checkbox']", true);
        selectAllTimer.end();

        if (Boolean.parseBoolean(ConfigReader.get("businessFilter"))) {
            MethodTimer filterLoansTimer = new MethodTimer("Uncheck salaried filter options");
            UIElementHandler.toggleCheckbox(driver, "//label[.//span[contains(text(),'Salaried')]]/preceding-sibling::span//input[@type='checkbox']", false);
            filterLoansTimer.end();
        }else {
            MethodTimer filterLoansTimer = new MethodTimer("Uncheck business filter options");
            UIElementHandler.toggleCheckbox(driver, "//label[.//span[contains(text(),'Self-employed')]]/preceding-sibling::span//input[@type='checkbox']", false);
            filterLoansTimer.end();
        }

        MethodTimer uncheck25kTimer = new MethodTimer("Uncheck 25k filter");
        UIElementHandler.toggleCheckbox(driver, "(//label[.//span[contains(text(),'Upto ₹ 25,000')]]/preceding-sibling::span//input[@type='checkbox'])[1]", false);
        uncheck25kTimer.end();

        if (Boolean.parseBoolean(ConfigReader.get("low_high_risk_filter"))) {
            MethodTimer filterLoansTimer = new MethodTimer("Uncheck low/high risk filter options");
            UIElementHandler.toggleCheckbox(driver, "//label[.//span[contains(text(),'12 Months (Monthly)')]]/preceding-sibling::span//input[@type='checkbox']", false);
            UIElementHandler.toggleCheckbox(driver, "//label[.//span[contains(text(),'12 Months (Daily)')]]/preceding-sibling::span//input[@type='checkbox']", false);
            UIElementHandler.toggleCheckbox(driver, "//label[.//span[contains(text(),'A (High)')]]/preceding-sibling::span//input[@type='checkbox']", false);
            UIElementHandler.toggleCheckbox(driver, "(//label[.//span[contains(text(),'₹ 25,001 to ₹ 50,000')]]/preceding-sibling::span//input[@type='checkbox'])[1]", false);
            UIElementHandler.toggleCheckbox(driver, "(//label[.//span[contains(text(),'₹ 50,001 to ₹ 1,00,000')]]/preceding-sibling::span//input[@type='checkbox'])[2]", false);
            UIElementHandler.toggleCheckbox(driver, "(//label[.//span[contains(text(),'More than ₹ 1,00,000')]]/preceding-sibling::span//input[@type='checkbox'])[2]", false);
            UIElementHandler.toggleCheckbox(driver, "//label[.//span[contains(text(),'₹ 1 to ₹ 1000')]]/preceding-sibling::span//input[@type='checkbox']", false);
            filterLoansTimer.end();
        }

        MethodTimer sortOpenTimer = new MethodTimer("Open Sort");
        UIElementHandler.clickElement(driver, wait, By.xpath("//button[normalize-space(text())='Sort']"));
        sortOpenTimer.end();

        MethodTimer sortLendenTimer = new MethodTimer("Sort by LenDenClub Score");
        UIElementHandler.clickElement(driver, wait, By.xpath("//p[text()='LenDenClub Score']/following-sibling::div[1]//button[normalize-space(text())='Higher to Lower']"));
        sortLendenTimer.end();

        MethodTimer sortLoanTimer = new MethodTimer("Sort by Loan amount");
        UIElementHandler.clickElement(driver, wait, By.xpath("//p[text()='Loan amount']/following-sibling::div[1]//button[normalize-space(text())='Lower to Higher']"));
        sortLoanTimer.end();

        MethodTimer sortTenureTimer = new MethodTimer("Sort by Tenure");
        UIElementHandler.clickElement(driver, wait, By.xpath("//p[text()='Tenure']/following-sibling::div[1]//button[normalize-space(text())='Lower to Higher']"));
        sortTenureTimer.end();

        MethodTimer sortIncomeTimer = new MethodTimer("Sort by Income");
        UIElementHandler.clickElement(driver, wait, By.xpath("//p[text()='Income']/following-sibling::div[1]//button[normalize-space(text())='Higher to Lower']"));
        sortIncomeTimer.end();

        MethodTimer sortInterestTimer = new MethodTimer("Sort by Interest Rate");
        UIElementHandler.clickElement(driver, wait, By.xpath("//p[text()='Interest Rate']/following-sibling::div[1]//button[normalize-space(text())='Higher to Lower']"));
        sortInterestTimer.end();

        MethodTimer applyTimer = new MethodTimer("Click Apply button");
        UIElementHandler.clickElement(driver, wait, By.xpath("//button[normalize-space(text())='Apply']"));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//button[normalize-space(text())='Apply']")));
        applyTimer.end();

        overallTimer.end();
    }
}

