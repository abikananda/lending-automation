package abika.lending;

import abika.MethodTimer;
import com.abika.model.Investment;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finalizes selected loans exactly once and clears reserved state only after success is confirmed.
 */
public class LendingFinalizer {
    private static final Logger logger = LoggerFactory.getLogger(LendingFinalizer.class);

    public static void finalizeLending(
            WebDriver driver,
            Investment investment,
            com.abika.reporting.ExecutionMetrics metrics) {

        MethodTimer timer = new MethodTimer("finalizeLending");
        try {
            if (investment.getLendAmtPerLoan() <= 0 || investment.getLoanCounts() <= 0) {
                logger.info("No loans to finalize.");
                return;
            }

            int targetAmount = investment.getLendAmtPerLoan();
            logger.info("Finalizing {} selected loans with amount per loan: {}",
                    investment.getLoanCounts(), targetAmount);

            if (!SliderHandler.adjustSlider(driver, targetAmount)) {
                throw new IllegalStateException(
                        "Slider adjustment failed before Continue; staged selections remain reserved");
            }

            // Financial action. LendButtonHandler must click Continue at most once.
            boolean continueClicked = LendButtonHandler.findAndClickLendButton(driver);
            if (!continueClicked) {
                throw new IllegalStateException(
                        "Continue button was not clicked; staged selections remain reserved");
            }

            long totalLendAmount = (long) targetAmount * investment.getLoanCounts();
            if (!SuccessValidator.isLendingSuccessfulByUrl(driver)) {
                throw new IllegalStateException(
                        "Continue was clicked but success could not be confirmed; financial outcome is uncertain");
            }

            double previousWallet = investment.getWalletAmount();
            investment.setWalletAmount(previousWallet - totalLendAmount);
            investment.setTotalBorrowersFinalized(investment.getLoanCounts());
            investment.setReservedAmount(0.0);

            SuccessValidator.logLendingCompletion(
                    investment.getLoanCounts(), totalLendAmount, investment.getWalletAmount());

            if (metrics != null) {
                int toFinalize = investment.getLoanCounts();
                int finalized = 0;
                for (int i = metrics.getBorrowerRecords().size() - 1;
                     i >= 0 && finalized < toFinalize; i--) {
                    com.abika.reporting.ExecutionMetrics.BorrowerRecord record =
                            metrics.getBorrowerRecords().get(i);
                    if (investment.getRuleName().equals(record.getRuleName())
                            && "SELECTED".equals(record.getStatus())) {
                        record.setStatus("FINALIZED");
                        record.setFinalizationTimeMs(System.currentTimeMillis());
                        finalized++;
                    }
                }
            }
        } finally {
            timer.end();
        }
    }
}
