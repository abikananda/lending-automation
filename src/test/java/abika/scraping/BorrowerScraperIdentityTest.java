package abika.scraping;

import com.abika.model.Borrower;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorrowerScraperIdentityTest {

    @Test
    void acceptsMatchingCardAndPopupIdentityIgnoringCaseAndExtraWhitespace() {
        Borrower borrower = borrower("LOA-22584T7Y", "Dinesh Meghnath Pedhvi");
        Set<String> seenLoanIds = new HashSet<>();

        assertDoesNotThrow(() -> validate("  DINESH   MEGHNATH PEDHVI  ", borrower, seenLoanIds));
        assertTrue(seenLoanIds.contains("LOA-22584T7Y"));
    }

    @Test
    void rejectsPopupBelongingToDifferentBorrowerBeforeEvaluation() {
        Borrower stalePopupBorrower = borrower("LOA-22584T7Y", "Dinesh Meghnath Pedhvi");
        Set<String> seenLoanIds = new HashSet<>();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> validate("MR AMARANADH GUDALI", stalePopupBorrower, seenLoanIds));

        assertTrue(error.getMessage().contains("card borrower 'MR AMARANADH GUDALI'"));
        assertTrue(error.getMessage().contains("popup borrower 'Dinesh Meghnath Pedhvi'"));
    }

    @Test
    void rejectsDuplicateLoanIdWithinSameScrapeRun() {
        Set<String> seenLoanIds = new HashSet<>();
        Borrower borrower = borrower("LOA-DUPLICATE", "Dinesh Meghnath Pedhvi");

        assertDoesNotThrow(() -> validate("DINESH MEGHNATH PEDHVI", borrower, seenLoanIds));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> validate("DINESH MEGHNATH PEDHVI", borrower, seenLoanIds));

        assertTrue(error.getMessage().contains("duplicate loanId 'LOA-DUPLICATE'"));
    }

    private static Borrower borrower(String loanId, String name) {
        Borrower borrower = new Borrower();
        borrower.setLoanId(loanId);
        borrower.setName(name);
        return borrower;
    }

    private static void validate(String cardName, Borrower borrower, Set<String> seenLoanIds) {
        try {
            Method method = BorrowerScraper.class.getDeclaredMethod(
                    "validateBorrowerIdentity", String.class, Borrower.class, Set.class);
            method.setAccessible(true);
            method.invoke(null, cardName, borrower, seenLoanIds);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
