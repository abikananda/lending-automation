package abika;

import com.abika.utils.ConfigReader;
import org.junit.jupiter.api.Test;
import org.testng.Assert;

public class ConfigTest {

    @Test
    public void testPremiumBasedOnAge() {
        Assert.assertEquals(ConfigReader.get("user1.email.username"),"abika.2009@gmail.com");
    }
}

