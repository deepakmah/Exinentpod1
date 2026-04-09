package nutridyn;

import org.openqa.selenium.WebDriver;

/**
 * Optional entry point without Maven TestNG: run this main from your IDE if you prefer not to use {@code mvn test}.
 */
public final class NutridynLauncher {

    public static void main(String[] args) throws Exception {
        NutridynReporting.beginNewRun();
        WebDriver driver = NutridynWebDriver.createChrome();
        try {
            NutridynFlow.runEndToEnd(driver);
        } finally {
            driver.quit();
        }
    }
}
