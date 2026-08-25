package abika;

import com.abika.model.Borrower;
import com.abika.utils.DroolsEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RulesTest {
    DroolsEngine droolsEngine;

    @BeforeEach
    public void setup() {
        droolsEngine = new DroolsEngine();
    }

    @Test
    public void testPremiumBasedOnAge() {
        Borrower borrower = new Borrower();
        borrower.setCreditScore(400);
        borrower.setLendenScore(700);
        borrower.setIncome(55000);
        borrower.setBorrowerType("SALARIED");
        borrower.setLoanAmount(3000);
        boolean fired = droolsEngine.fireRuleByName(borrower, "Normal Lenders");

        if (fired) {
            System.out.println("Rule executed. LendingAmount: " + borrower.getLendingAmount());
        } else {
            System.out.println("Rule did not fire (either not matched or not found).");
        }

    }

    @AfterEach
    public void teardown() {
    }
}

