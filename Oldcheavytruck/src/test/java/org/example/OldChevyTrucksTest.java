package org.example;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;

/**
 * TestNG entry point for {@link Oldcheavytruck} — run with {@code mvn test}.
 */
public class OldChevyTrucksTest {

    private WebDriver driver;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        driver = new ChromeDriver();
        driver.manage().window().maximize();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    @Test(
            description = "Old Chevy Trucks: categories, sitemap PDPs, cart, discount, checkout, search",
            timeOut = 1_800_000
    )
    public void oldChevyTrucksEndToEnd() throws IOException, InterruptedException {
        Oldcheavytruck.runEndToEndFlow(driver);
    }
}
