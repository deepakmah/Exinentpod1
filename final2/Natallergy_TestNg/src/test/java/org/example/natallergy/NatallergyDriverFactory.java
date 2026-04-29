package org.example.natallergy;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * <p>Starts <b>visible</b> Chrome (not headless) so a person can watch the run. If you set {@code -Dwebdriver.chrome.driver}
 * to a {@code chromedriver.exe} path, that binary is used; otherwise WebDriverManager downloads a matching driver.</p>
 */
public final class NatallergyDriverFactory {

    /**
     * {@code --remote-allow-origins=*} avoids Chrome 111+ CDP handshake stalls with some Selenium pairings.
     */
    public static WebDriver createChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--window-position=0,0");
        options.addArguments("--disable-search-engine-choice-screen");
        options.addArguments("--remote-allow-origins=*");

        String manualDriver = System.getProperty("webdriver.chrome.driver");
        if (manualDriver != null && !manualDriver.isBlank()) {
            System.out.println("[Natallergy] Using chromedriver from -Dwebdriver.chrome.driver=" + manualDriver);
        } else {
            System.out.println("[Natallergy] Resolving ChromeDriver (WebDriverManager; first run may download driver)…");
            System.out.flush();
            WebDriverManager.chromedriver().setup();
        }
        System.out.flush();

        System.out.println("[Natallergy] Launching Chrome window…");
        System.out.flush();
        return new ChromeDriver(options);
    }

    private NatallergyDriverFactory() {
    }
}
