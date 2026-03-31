package Speed;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

public class PageSpeedAutomation {

    public static void main(String[] args) {

        String[] websites = {
                "https://www.colbrookkitchen.com",
                "https://greatcellsolarmaterials.com/",
                "https://allfasteners.com/",
                "https://www.shopdap.com/",
                "https://www.mcfeelys.com/",
                "https://www.natlallergy.com/",
                "https://www.achooallergy.com/",
                "https://www.bandagesplus.com/",
                "https://oldchevytrucks.com/",
                "https://nutridyn.com/"
        };

        for (String site : websites) {
            runForSite(site);
        }

        System.out.println("\n=== ALL DONE ===");
    }

    // ================================================================
    // RUN PAGESPEED FOR ONE WEBSITE (SAFE RUNNER)
    // ================================================================
    private static void runForSite(String site) {

        WebDriver driver = null;
        WebDriverWait wait;

        try {
            System.out.println("\n=============================");
            System.out.println("Running PageSpeed for: " + site);
            System.out.println("=============================");

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--start-maximized");

            driver = new ChromeDriver(options);
            wait = new WebDriverWait(driver, Duration.ofSeconds(90));

            driver.get("https://pagespeed.web.dev/");
            Thread.sleep(3000);

            // Enter URL safely
            WebElement inputField = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//input[@id='i2']")
            ));

            inputField.clear();
            inputField.sendKeys(site);
            Thread.sleep(1500);

            // Click analyze
            WebElement analyzeBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[normalize-space()='Analyze']")
            ));
            analyzeBtn.click();

            System.out.println("Waiting for analysis...");

            // Wait for score load
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.className("lh-exp-gauge__percentage")
            ));
            System.out.println("✔ Analysis completed");

            String siteName = site.replace("https://", "").replace("www.", "").replace("/", "");

            // DESKTOP TAB
            clickTab(wait, "desktop_tab");
            scrollToSvgDiagnoseSection(driver, wait);
            takeFullPageScreenshot(driver, siteName + "_desktop");

            // MOBILE TAB
            clickTab(wait, "mobile_tab");
            scrollToSvgDiagnoseSection(driver, wait);
            takeFullPageScreenshot(driver, siteName + "_mobile");

        } catch (Exception e) {
            System.out.println("⚠ FAILED for site: " + site);
            System.out.println("Reason: " + e.getMessage());
        }
        finally {
            // Prevent crash on quit
            try {
                if (driver != null)
                    driver.quit();
            } catch (Exception ignore) {}
        }
    }

    // ================================================================
    // CLICK TAB SAFELY
    // ================================================================
    private static void clickTab(WebDriverWait wait, String tabId) {
        try {
            WebElement tabBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("button[id='" + tabId + "'] span.VfPpkd-YVzG2b")
            ));
            tabBtn.click();
            Thread.sleep(4000);
        } catch (Exception e) {
            System.out.println("⚠ Could not click tab: " + tabId);
        }
    }

    // ================================================================
    // SCROLL TO Diagnose performance issues
    // ================================================================
    private static void scrollToSvgDiagnoseSection(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement svgBlock = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("(//div[@class='nfmh5d'][normalize-space()='Diagnose performance issues'])[2]")
            ));

            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});",
                    svgBlock
            );

            Thread.sleep(1500);
            System.out.println("⬇ Scrolled to Diagnose Performance Issues");
        } catch (Exception e) {
            System.out.println("⚠ SVG scroll failed: " + e.getMessage());
        }
    }

    // ================================================================
    // Screenshot Method
    // ================================================================
    private static void takeFullPageScreenshot(WebDriver driver, String baseFilename) throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String filename = baseFilename + "_" + timestamp + ".png";

        File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        File folder = new File("C:\\Users\\deepa\\Documents\\screenshort\\");

        if (!folder.exists()) folder.mkdirs();

        Files.copy(srcFile.toPath(), new File(folder, filename).toPath());
        System.out.println("✔ Screenshot saved: " + folder + "\\" + filename);
    }
}
