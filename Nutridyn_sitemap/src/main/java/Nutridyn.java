import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.Keys;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.time.Duration;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Nutridyn {

    static String RUN_DATE = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    static String RUN_TIME = new SimpleDateFormat("HH-mm-ss").format(new Date());
    static int totalSteps = 0;
    static int passedSteps = 0;
    static int failedSteps = 0;
    static String START_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    static List<String> htmlSteps = new ArrayList<>();

    static String SS_DIR = "C:\\Users\\deepa\\Documents\\Automation\\Nutridyn\\screenshots\\" + RUN_DATE + "\\" + RUN_TIME;
    static String HTML_DIR = "C:\\Users\\deepa\\Documents\\Automation\\Nutridyn\\html\\" + RUN_DATE + "\\" + RUN_TIME;
    static String CSV_PATH = "C:\\Users\\deepa\\Documents\\Automation\\Nutridyn\\Nutridyn.csv";

    /** Main storefront sitemap (may be a sitemap index pointing at sitemap-1.xml, etc.) */
    static final String SITEMAP_URL = "https://nutridyn.com/pub/media/sitemap.xml";

    public static void main(String[] args) throws InterruptedException, IOException {

        ChromeOptions options = new ChromeOptions();
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);
        options.setExperimentalOption("prefs", prefs);
        options.addArguments(
                "--start-maximized",
                "--disable-save-password-bubble",
                "--password-store=basic",
                "--disable-features=PasswordLeakDetection"
        );

        WebDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();

        driver.navigate().to("https://nutridyn.com/");
        Thread.sleep(1000);
        System.out.println("Site is opened");
        takeScreenshot(driver, "Homepage");
        Thread.sleep(1000);

        //cookie accept//

        if (driver.findElements(By.xpath("//span[contains(text(),'Accept')]")).size() > 0)
        {
            driver.findElement(By.xpath("//span[contains(text(),'Accept')]")).click();
            Thread.sleep(2000);
            System.out.println("Cookie accepted");
            takeScreenshot(driver, "Cookie_Accepted");
        }
        else {
            System.out.println("Cookie accept button not found");
        }

        //login page ///

        // Login patient account //
        if (driver.findElements(By.xpath("//*[@id='header-account-1']/ul/li/a")).size() > 0) {
            driver.findElement(By.xpath("//*[@id='header-account-1']/ul/li/a")).click();
            Thread.sleep(3000);
            System.out.println("Login page opened");
            takeScreenshot(driver, "Login_Page");
        }
        else {
            System.out.println("Login link not found");
        }

        //user name//

        if (driver.findElements(By.id("email")).size() > 0) {

            driver.findElement(By.id("email")).sendKeys("gopal.exinentpatient@gmail.com");
            System.out.println("Username entered");
            Thread.sleep(1000);
        }
        else {
            System.out.println("Username field not found");
        }
        //password//
        if (driver.findElements(By.id("pass")).size() > 0) {
            driver.findElement(By.id("pass")).sendKeys("test@1234");
            System.out.println("Password is entered");
            Thread.sleep(2000);
        }
        else {
            System.out.println("Password field not found");
        }
        //click on login//
        if (driver.findElements(By.name("send")).size() > 0) {driver.findElement(By.name("send")).click();

            System.out.println("Patient account login successful");
            Thread.sleep(2000);
            takeScreenshot(driver, "Patient_Login_Success");
        }
        else {
            System.out.println("Login button not found");
        }

        // --- 2–3 products from sitemap (dynamic URLs) ---
        int numProducts = ThreadLocalRandom.current().nextInt(2, 4);
        List<String> productUrls = loadProductUrlsFromSitemap(SITEMAP_URL, numProducts);
        System.out.println("Sitemap: using " + productUrls.size() + " product URL(s) (requested up to " + numProducts + "):");
        for (String u : productUrls) {
            System.out.println("  - " + u);
        }

        if (productUrls.isEmpty()) {
            System.out.println("No product URLs from sitemap — skipping add-to-cart block.");
        } else {
            for (int i = 0; i < productUrls.size(); i++) {
                String purl = productUrls.get(i);
                driver.navigate().to(purl);
                Thread.sleep(2500);
                System.out.println("Opened product " + (i + 1) + ": " + purl);
                takeScreenshot(driver, "Product_Page_" + (i + 1) + "_" + productUrlSlug(purl));

                if (driver.findElements(By.id("product-addtocart-button")).size() > 0) {
                    driver.findElement(By.id("product-addtocart-button")).click();
                    System.out.println("Product " + (i + 1) + " added to cart");
                    Thread.sleep(1000);
                    takeScreenshot(driver, "Product_Added_To_Cart_" + (i + 1));
                } else {
                    System.out.println("Add to Cart not found for product " + (i + 1) + " — may be OOS or not a simple product.");
                }
            }
        }

        //click on cart icon//

        if (driver.findElements(By.xpath("//*[@id='minicart']/div[1]/span/span[1]")).size() > 0)

        {

            driver.findElement(By.xpath("//*[@id='minicart']/div[1]/span/span[1]")).click();

            Thread.sleep(2000);
            System.out.println("Cart page opened");
            takeScreenshot(driver, "Cart_Page");
        }
        else {

            System.out.println("Mini cart icon not found");
        }

        //scrool down the screen//

        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.scrollBy(0, 300);");
        Thread.sleep(2000);
        //checkout page//  (click <button>, not inner <span> — avoids intercepted clicks)

        List<WebElement> checkoutBtns = driver.findElements(
                By.xpath("//*[@id='maincontent']/div[3]/div/div[6]/div[1]/div[2]/ul/li/button"));
        if (!checkoutBtns.isEmpty()) {
            WebElement checkoutBtn = checkoutBtns.get(0);
            JavascriptExecutor jex = (JavascriptExecutor) driver;
            jex.executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", checkoutBtn);
            Thread.sleep(500);
            try {
                WebElement toClick = new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.elementToBeClickable(checkoutBtn));
                toClick.click();
            } catch (ElementClickInterceptedException | TimeoutException e) {
                jex.executeScript("arguments[0].click();", checkoutBtn);
            }
            Thread.sleep(5000);
            System.out.println("Checkout page opened");
            takeScreenshot(driver, "Checkout_Page");
        } else {
            System.out.println("Checkout button not found");
        }
        // go to home page//


        if (driver.findElements(By.xpath("//*[@id='header_logo']/a/img")).size() > 0)
        {

            driver.findElement(By.xpath("//*[@id='header_logo']/a/img")).click();


            Thread.sleep(2000);
            System.out.println("Home page opened");
            takeScreenshot(driver, "Home_After_Checkout");
        }
        else {

            System.out.println("Header logo not found");
        }

        //patient logout //

        // use because no thread.sleep is used after this step//
        // so, implicit wait is used//
        //use on mageto pages which takes time to load //


        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Account click
        WebElement accountLink = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='header-account-1']/ul/li/a")));

        accountLink.click();
        System.out.println("Account page opened");

        // Home click
        WebElement homeLogo = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='header_logo']/a")));

        homeLogo.click();
        System.out.println("Home page opened");

        //practioner login//

        if (driver.findElements(By.xpath("//*[@id='header-account-1']/ul/li/a")).size() > 0)
        {

            driver.findElement(By.xpath("//*[@id='header-account-1']/ul/li/a")).click();
            Thread.sleep(1000);
            System.out.println("Account login page opened");

        } else

        {

            System.out.println("Account link not found");
        }

        // Enter Username
        if (driver.findElements(By.id("email")).size() > 0)
        {
            driver.findElement(By.id("email")).sendKeys("foo.bar@getastra.live");
            System.out.println("Practitioner login username entered");
            Thread.sleep(1000);

        } else

        {
            System.out.println("Username field not found");
        }

        // Enter Password

        if (driver.findElements(By.id("pass")).size() > 0)
        {

            driver.findElement(By.id("pass")).sendKeys("123456");
            System.out.println("Practitioner login password is entered");
            Thread.sleep(2000);

        }

        else
        {
            System.out.println("Password field not found");
        }
        //click on login//

        if (driver.findElements(By.name("send")).size() > 0)
        {

            driver.findElement(By.name("send")).click();
            System.out.println("Practitioner account login successful");
            Thread.sleep(2000);
            takeScreenshot(driver, "Practitioner_Login_Success");
        }
        else
        {
            System.out.println("Login button not found");

        }


        // my orders//

        WebElement myOrders = driver.findElement(
                By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[2]/a/span")
        );

        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", myOrders);

        System.out.println("My Orders page is opened");
        takeScreenshot(driver, "My_Orders");
        Thread.sleep(1000);

        // first order view//

        if (driver.findElements(By.xpath("//*[@id=\"my-orders-table\"]/tbody/tr[1]/td[6]/a[1]/span")).size() > 0)
        {

            driver.findElement(By.xpath("//*[@id=\"my-orders-table\"]/tbody/tr[1]/td[6]/a[1]/span")).click();
            Thread.sleep(2000);
            System.out.println("First order view page is opened");
            takeScreenshot(driver, "First_Order_View");
        }
        else
        {
            System.out.println("First order view link not found");
        }


        //mysubscriptions//

        if (driver.findElements(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[3]/a/span")).size() > 0)
        {

            driver.findElement(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[3]/a/span")).click();
            Thread.sleep(2000);
            System.out.println("My Subscriptions page is opened");
            takeScreenshot(driver, "My_Subscriptions");
        }
        else
        {
            System.out.println("My Subscriptions link not found");
        }


        //click on subscription view//

        if (driver.findElements(By.xpath("//*[@id=\"my-orders-table\"]/tbody/tr[1]/td[6]/a/span")).size() > 0)
        {

            driver.findElement(By.xpath("//*[@id=\"my-orders-table\"]/tbody/tr[1]/td[6]/a/span")).click();
            Thread.sleep(2000);
            System.out.println("Subscription view page is opened");
            takeScreenshot(driver, "Subscription_View");
        }
        else
        {
            System.out.println("Subscription view link not found");
        }


        // list page//

        //list//

        if (driver.findElements(By.xpath("//*[@id='maincontent']/div[2]/div[2]/div/div/div/ul[1]/li[4]/a/span")).size() > 0)
        {

            driver.findElement(By.xpath("//*[@id='maincontent']/div[2]/div[2]/div/div/div/ul[1]/li[4]/a/span")).click();
            Thread.sleep(2000);
            System.out.println("List page is opened");
            takeScreenshot(driver, "List_Page");
        }
        else
        {
            System.out.println("List page link not found");
        }

        //my patients//
        if (driver.findElements(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[5]/a/span")).size() > 0)
        {

            driver.findElement(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[5]/a/span")).click();
            Thread.sleep(2000);
            System.out.println("My Patients page is opened");
            takeScreenshot(driver, "My_Patients");
        }
        else
        {
            System.out.println("My Patients link not found");
        }

        //addres book//

        if (driver.findElements(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[6]/a/span")).size() > 0)
        {

            driver.findElement(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[6]/a/span")).click();
            Thread.sleep(2000);
            System.out.println("Address Book page is opened");
            takeScreenshot(driver, "Address_Book");
        }
        else
        {
            System.out.println("Address Book link not found");
        }

        //scroll down the screen//

        JavascriptExecutor js3 = (JavascriptExecutor) driver;
        js3.executeScript("window.scrollBy(0, 400);");





        //nutriscript/

        if (driver.findElements(By.xpath("//a//span[text()='NutriScripts']")).size() > 0) {
            driver.findElement(By.xpath("//a//span[text()='NutriScripts']")).click();
            System.out.println("NutriScripts clicked");
            takeScreenshot(driver, "NutriScripts");
        } else {
            System.out.println("NutriScripts not found");
        }
        Thread.sleep(2000);

        //scrool down the screen//


        JavascriptExecutor js1 = (JavascriptExecutor) driver;
        js1.executeScript("window.scrollBy(0, 200);");

        // view nutriscript//

        List<WebElement> viewNutriLinks = driver.findElements(By.xpath("//a[contains(normalize-space(),'View NutriScript')]"));
        if (!viewNutriLinks.isEmpty()) {
            WebElement viewNutri = viewNutriLinks.get(0);
            JavascriptExecutor jex = (JavascriptExecutor) driver;
            jex.executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", viewNutri);
            Thread.sleep(500);
            try {
                WebElement toClick = new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.elementToBeClickable(viewNutri));
                toClick.click();
            } catch (ElementClickInterceptedException | TimeoutException e) {
                jex.executeScript("arguments[0].click();", viewNutri);
            }
            System.out.println("View NutriScript clicked");
            takeScreenshot(driver, "View_NutriScript");
        }
        else {
            System.out.println("View NutriScript not found");
        }


        Thread.sleep(2000);


        //scrool down the screen//
        JavascriptExecutor js2 = (JavascriptExecutor) driver;
        js2.executeScript("window.scrollBy(0, 100);");
        Thread.sleep(2000);


        //generic //

        if (driver.findElements(By.xpath("//span[contains(text(),'3X4 Genetics')]")).size() > 0) {
            driver.findElement(By.xpath("//span[contains(text(),'3X4 Genetics')]")).click();
            System.out.println("3X4 Genetics clicked");
            takeScreenshot(driver, "3X4_Genetics");
        } else {
            System.out.println("3X4 Genetics not found");
        }
        Thread.sleep(2000);

        //nutridyn connect//



        if (driver.findElements(By.xpath("//span[text()='NutriDyn Connect']")).size() > 0) {
            driver.findElement(By.xpath("//span[text()='NutriDyn Connect']")).click();
            System.out.println("NutriDyn Connect clicked");
            takeScreenshot(driver, "NutriDyn_Connect");
        } else {
            System.out.println("NutriDyn Connect not found");
        }


        Thread.sleep(2000);

        // nutridyn connect pro//

        if (driver.findElements(By.xpath("//a[normalize-space()='NutriDyn Connect Pro']")).size() > 0) {
            driver.findElement(By.xpath("//a[normalize-space()='NutriDyn Connect Pro']")).click();
            System.out.println("NutriDyn Connect Pro clicked");
            takeScreenshot(driver, "NutriDyn_Connect_Pro");
        } else {
            System.out.println("NutriDyn Connect Pro not found");
        }
        Thread.sleep(2000);

        //scrool down the screen//
        JavascriptExecutor js7 = (JavascriptExecutor) driver;
        js7.executeScript("window.scrollBy(0, 200);");
        Thread.sleep(2000);
        //applet//

        if (driver.findElements(By.xpath("//a[contains(@href,'connectpro/applet')]")).size() > 0) {
            driver.findElement(By.xpath("//a[contains(@href,'connectpro/applet')]")).click();
            System.out.println("NutriDyn Connect Applet clicked");
            takeScreenshot(driver, "Connect_Applet");
        } else {
            System.out.println("NutriDyn Connect Applet not found");
        }
        Thread.sleep(2000);

        //scroll down the screen//

        JavascriptExecutor js4 = (JavascriptExecutor) driver;
        js4.executeScript("window.scrollBy(0, 300);");
        Thread.sleep(2000);

        //connect links //

        if (driver.findElements(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[10]/ul/li[3]/a")).size() > 0) {
            driver.findElement(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[10]/ul/li[3]/a")).click();
            System.out.println("NutriDyn Connect Links clicked");
            takeScreenshot(driver, "Connect_Links");
        } else {
            System.out.println("NutriDyn Connect Links not found");

        }
        Thread.sleep(2000);
        //scrroll up the screen//
        JavascriptExecutor js5 = (JavascriptExecutor) driver;
        js5.executeScript("window.scrollBy(0, -400);");
        Thread.sleep(2000);
        //again click on nutriscript////

        if (driver.findElements(By.xpath("//a//span[text()='NutriScripts']")).size() > 0) {
            driver.findElement(By.xpath("//a//span[text()='NutriScripts']")).click();
            System.out.println("NutriScripts clicked again");
        } else {
            System.out.println("NutriScripts not found");

        }


        // go to homepage//


        WebElement logo = driver.findElement(By.xpath("//*[@id='header_logo']/a"));
        JavascriptExecutor js51 = (JavascriptExecutor) driver;
        js51.executeScript("arguments[0].click();", logo);
        System.out.println("Home page opened again");
        takeScreenshot(driver, "Home_Again");




        //logout//


        if (driver.findElements(By.xpath("//a[normalize-space()='Logout']")).size() > 0) {

            WebElement logout = driver.findElement(By.xpath("//a[normalize-space()='Logout']"));
            JavascriptExecutor js11 = (JavascriptExecutor) driver;
            js11.executeScript("arguments[0].click();", logout);

            System.out.println("Logout clicked using JS");
            takeScreenshot(driver, "Logout");
        } else {
            System.out.println("Logout link not found");
        }

        Thread.sleep(2000);
        driver.quit();

    }

    private static String httpGetString(String urlStr) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", "NutridynAutomation/1.0 (Sitemap)");
        c.setConnectTimeout(30000);
        c.setReadTimeout(120000);
        int code = c.getResponseCode();
        if (code >= 400) {
            throw new IOException("HTTP " + code + " for " + urlStr);
        }
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            in.transferTo(buf);
            return buf.toString(StandardCharsets.UTF_8);
        }
    }

    private static List<String> dedupeUrls(List<String> urls) {
        return new ArrayList<>(new LinkedHashSet<>(urls));
    }

    /**
     * Product rows in NutriDyn sitemap include {@code image:image} and {@code pub/media/catalog/product} in the entry.
     * Category-only URLs use priority 0.5 and are skipped.
     */
    private static List<String> extractProductUrlsFromUrlset(String xml) {
        List<String> out = new ArrayList<>();
        Pattern locPattern = Pattern.compile("<loc>(https://nutridyn\\.com[^<]*)</loc>");
        for (String chunk : xml.split("</url>")) {
            if (!chunk.contains("image:image") || !chunk.contains("catalog/product")) {
                continue;
            }
            Matcher m = locPattern.matcher(chunk);
            if (!m.find()) {
                continue;
            }
            String u = m.group(1).trim();
            if ("https://nutridyn.com/".equals(u) || "https://nutridyn.com".equals(u)) {
                continue;
            }
            if (u.contains("/catalog/category/")) {
                continue;
            }
            try {
                String path = URI.create(u).normalize().getPath();
                if (path == null) {
                    continue;
                }
                long segments = Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).count();
                if (segments < 2) {
                    continue;
                }
            } catch (IllegalArgumentException | NullPointerException ex) {
                continue;
            }
            out.add(u);
        }
        return out;
    }

    private static List<String> loadAllProductUrls(String sitemapUrl) throws IOException {
        String xml = httpGetString(sitemapUrl);
        if (xml.contains("<sitemapindex")) {
            List<String> merged = new ArrayList<>();
            Matcher m = Pattern.compile("<loc>(https://[^<]+\\.xml)</loc>").matcher(xml);
            while (m.find()) {
                String child = m.group(1);
                if (!child.contains("nutridyn.com")) {
                    continue;
                }
                try {
                    merged.addAll(extractProductUrlsFromUrlset(httpGetString(child)));
                } catch (IOException e) {
                    System.err.println("Child sitemap skipped: " + child + " — " + e.getMessage());
                }
            }
            return dedupeUrls(merged);
        }
        return dedupeUrls(extractProductUrlsFromUrlset(xml));
    }

    private static List<String> loadProductUrlsFromSitemap(String sitemapUrl, int maxCount) throws IOException {
        List<String> all = loadAllProductUrls(sitemapUrl);
        if (all.isEmpty()) {
            return all;
        }
        Collections.shuffle(all);
        int n = Math.min(maxCount, all.size());
        return new ArrayList<>(all.subList(0, n));
    }

    private static String productUrlSlug(String productUrl) {
        try {
            String path = URI.create(productUrl).getPath();
            if (path == null) {
                return "product";
            }
            String[] parts = path.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (!parts[i].isEmpty()) {
                    return parts[i].replaceAll("[^a-zA-Z0-9_-]+", "_");
                }
            }
        } catch (Exception ignored) {
        }
        return "product";
    }

    // ------------------ Full-page screenshot + CSV + HTML ------------------
    private static void takeScreenshot(WebDriver driver, String title) throws IOException, InterruptedException {
        takeScreenshot(driver, title, true, "Step completed successfully");
    }

    private static void takeScreenshot(WebDriver driver, String title, boolean isPass, String details) throws IOException, InterruptedException {
        totalSteps++;
        if (isPass) passedSteps++;
        else failedSteps++;

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String statusPrefix = isPass ? "SUCCESS_" : "ERROR_";
        String fileName = statusPrefix + title + "_" + timestamp + ".png";

        File folder = new File(SS_DIR);
        if (!folder.exists()) folder.mkdirs();

        File outputFile = new File(folder, fileName);
        saveFullPageScreenshot(driver, outputFile);

        System.out.println("Full-page screenshot saved: " + outputFile.getAbsolutePath());

        writeCsv(timestamp, title, outputFile.getName());
        writeHtmlReport(timestamp, title, outputFile.getName(), isPass, details);
    }

    private static double cdpNumber(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }

    /**
     * Chrome: true full-page PNG via layout size + device metrics (viewport-only CDP is unreliable on some builds).
     * Falls back to captureBeyondViewport, then WebDriver screenshot.
     */
    private static void saveFullPageScreenshot(WebDriver driver, File outputFile) throws IOException, InterruptedException {
        if (!(driver instanceof HasCdp hasCdp)) {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(src.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        Map<String, Object> empty = new HashMap<>();
        try {
            Map<String, Object> metrics = hasCdp.executeCdpCommand("Page.getLayoutMetrics", empty);
            @SuppressWarnings("unchecked")
            Map<String, Object> box = (Map<String, Object>) metrics.get("cssContentSize");
            if (box == null || box.isEmpty()) {
                box = (Map<String, Object>) metrics.get("contentSize");
            }

            int width = 1280;
            int height = 2000;
            if (box != null && box.get("width") != null && box.get("height") != null) {
                width = (int) Math.ceil(cdpNumber(box.get("width")));
                height = (int) Math.ceil(cdpNumber(box.get("height")));
            }
            width = Math.clamp(width, 400, 4096);
            height = Math.clamp(height, 400, 25000);

            Map<String, Object> override = new HashMap<>();
            override.put("width", width);
            override.put("height", height);
            override.put("deviceScaleFactor", 1);
            override.put("mobile", false);

            hasCdp.executeCdpCommand("Emulation.setDeviceMetricsOverride", override);
            Thread.sleep(400);
            try {
                Map<String, Object> cap = new HashMap<>();
                cap.put("format", "png");
                cap.put("captureBeyondViewport", true);
                cap.put("fromSurface", true);
                Map<String, Object> result = hasCdp.executeCdpCommand("Page.captureScreenshot", cap);
                String data = (String) result.get("data");
                Files.write(outputFile.toPath(), Base64.getDecoder().decode(data));
            } finally {
                hasCdp.executeCdpCommand("Emulation.clearDeviceMetricsOverride", empty);
            }
        } catch (Exception e) {
            System.out.println("Full-page capture (layout metrics) failed: " + e.getMessage() + " — using CDP viewport capture.");
            try {
                Map<String, Object> cap = new HashMap<>();
                cap.put("format", "png");
                cap.put("captureBeyondViewport", true);
                cap.put("fromSurface", true);
                Map<String, Object> result = hasCdp.executeCdpCommand("Page.captureScreenshot", cap);
                String data = (String) result.get("data");
                Files.write(outputFile.toPath(), Base64.getDecoder().decode(data));
            } catch (Exception e2) {
                File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Files.copy(src.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void writeCsv(String timestamp, String title, String localFileName) {
        File fileObj = new File(CSV_PATH);
        if (!fileObj.getParentFile().exists()) {
            fileObj.getParentFile().mkdirs();
        }
        boolean fileExists = fileObj.exists();

        try (FileWriter fw = new FileWriter(CSV_PATH, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            if (!fileExists) {
                out.println("Timestamp,Title,LocalFile");
            }
            out.println(timestamp + "," + title + "," + localFileName);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String escAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;");
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void writeHtmlReport(String timestamp, String title, String localFileName, boolean isPass, String details) {
        File htmlFolder = new File(HTML_DIR);
        if (!htmlFolder.exists()) htmlFolder.mkdirs();

        String htmlFile = HTML_DIR + "\\TestReport.html";
        File imageFile = new File(SS_DIR, localFileName);
        String fileUri = imageFile.getAbsoluteFile().toURI().toString();

        String stepStatusClass = isPass ? "pass" : "fail";
        String stepStatusIcon = isPass ? "✅" : "❌";
        String titleUpper = title.replace("_", " ").toUpperCase();

        StringBuilder stepHtml = new StringBuilder();
        stepHtml.append("            <div class=\"test-step ").append(stepStatusClass).append("\">\n");
        stepHtml.append("                <div class=\"step-header\">\n");
        stepHtml.append("                    <span>").append(stepStatusIcon).append(" ").append(titleUpper).append("</span>\n");
        stepHtml.append("                    <span class=\"step-time\">").append(timestamp.split("_")[1].replace("-", ":")).append("</span>\n");
        stepHtml.append("                </div>\n");
        stepHtml.append("                <div class=\"step-details\">").append(escHtml(details)).append("</div>\n");
        stepHtml.append("                <div class=\"screenshot-wrap\">\n");
        stepHtml.append("                    <p class=\"screenshot-note\">Thumbnail preview (full-page image is still saved on disk).</p>\n");
        stepHtml.append("                    <a class=\"screenshot-link\" href=\"").append(fileUri).append("\" target=\"_blank\" rel=\"noopener\">\n");
        stepHtml.append("                        <img class=\"screenshot\" src=\"").append(fileUri).append("\" alt=\"")
                .append(escAttr(title)).append(" screenshot\" loading=\"lazy\">\n");
        stepHtml.append("                    </a>\n");
        stepHtml.append("                    <div class=\"screenshot-actions\">\n");
        stepHtml.append("                        <a class=\"btn\" href=\"").append(fileUri).append("\" target=\"_blank\" rel=\"noopener\">View Local</a>\n");
        stepHtml.append("                        <a class=\"btn btn-secondary\" href=\"").append(fileUri).append("\" target=\"_blank\" rel=\"noopener\">View full size</a>\n");
        stepHtml.append("                    </div>\n");
        stepHtml.append("                </div>\n");
        stepHtml.append("            </div>");

        htmlSteps.add(stepHtml.toString());

        try (FileWriter fw = new FileWriter(htmlFile, false);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            double passRate = totalSteps > 0 ? ((double) passedSteps / totalSteps) * 100 : 0;
            String overallStatus = failedSteps > 0 ? "FAILED" : "PASSED";
            String statusBadgeClass = failedSteps > 0 ? "status-fail" : "status-pass";

            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("    <meta charset=\"UTF-8\">");
            out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.println("    <title>NutriDyn Automation - Test Report</title>");
            out.println("    <style>");
            out.println("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 20px; background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%); }");
            out.println("        .container { max-width: min(1920px, 100%); margin: 0 auto; background: white; border-radius: 15px; box-shadow: 0 10px 30px rgba(0,0,0,0.1); padding: 40px; }");
            out.println("        .header { text-align: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px; border-radius: 15px; margin-bottom: 40px; }");
            out.println("        .header h1 { margin: 0; font-size: 2.5em; font-weight: 300; }");
            out.println("        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 25px; margin-bottom: 40px; }");
            out.println("        .summary-card { background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); padding: 25px; border-radius: 15px; text-align: center; border-left: 6px solid #667eea; transition: transform 0.3s; }");
            out.println("        .summary-card:hover { transform: translateY(-5px); }");
            out.println("        .summary-card h3 { margin: 0 0 15px 0; color: #333; font-size: 1.1em; }");
            out.println("        .summary-card .number { font-size: 2.5em; font-weight: bold; color: #333; margin-bottom: 10px; }");
            out.println("        .progress-bar { width: 100%; height: 25px; background-color: #e9ecef; border-radius: 12px; overflow: hidden; margin: 15px 0; }");
            out.println("        .progress-fill { height: 100%; background: linear-gradient(90deg, #28a745, #20c997); transition: width 1s ease; border-radius: 12px; }");
            out.println("        .test-results { margin: 40px 0; display: flex; flex-direction: column; gap: 15px; align-items: stretch; width: 100%; }");
            out.println("        .test-step { margin: 15px 0; padding: 20px; border-radius: 12px; border-left: 6px solid; transition: all 0.3s; width: 100%; box-sizing: border-box; min-width: 0; }");
            out.println("        .test-step:hover { transform: translateX(5px); }");
            out.println("        .test-step.pass { background: linear-gradient(135deg, #d4edda 0%, #c3e6cb 100%); border-left-color: #28a745; }");
            out.println("        .test-step.fail { background: linear-gradient(135deg, #f8d7da 0%, #f5c6cb 100%); border-left-color: #dc3545; }");
            out.println("        .step-header { font-weight: bold; margin-bottom: 12px; font-size: 1.1em; }");
            out.println("        .step-details { font-size: 0.95em; color: #666; line-height: 1.5; }");
            out.println("        .step-time { font-size: 0.85em; color: #888; float: right; background: rgba(0,0,0,0.05); padding: 4px 8px; border-radius: 5px; }");
            out.println("        .screenshot-wrap { width: 100%; margin: 20px 0; padding: 14px 12px; background: #f1f3f5; border-radius: 10px; border: 1px solid #dee2e6; box-sizing: border-box; min-width: 0; text-align: center; }");
            out.println("        .screenshot-actions { margin-top: 14px; display: flex; flex-wrap: wrap; gap: 10px; align-items: center; justify-content: center; }");
            out.println("        .screenshot-note { margin: 0 0 12px 0; font-size: 0.9em; color: #495057; font-weight: 600; text-align: center; }");
            out.println("        .screenshot-link { display: inline-block; line-height: 0; max-width: 100%; }");
            out.println("        .screenshot { max-width: min(400px, 100%); max-height: 240px; width: auto; height: auto; object-fit: contain; display: block; margin: 0 auto; border-radius: 6px; border: 1px solid #ced4da; box-shadow: 0 2px 8px rgba(0,0,0,0.08); cursor: pointer; background: #fff; padding: 4px; image-rendering: auto; }");
            out.println("        .timestamp { text-align: center; color: #666; margin: 25px 0; font-size: 1.1em; }");
            out.println("        .status-badge { display: inline-block; padding: 8px 20px; border-radius: 25px; color: white; font-weight: bold; }");
            out.println("        .status-pass { background: linear-gradient(135deg, #28a745 0%, #20c997 100%); }");
            out.println("        .status-fail { background: linear-gradient(135deg, #dc3545 0%, #c82333 100%); }");
            out.println("        .btn { display: inline-block; padding: 8px 18px; background: linear-gradient(135deg, #28a745 0%, #20c997 100%); color: white; text-decoration: none; border-radius: 20px; font-weight: bold; font-size: 0.9em; transition: opacity 0.3s; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }");
            out.println("        .btn:hover { opacity: 0.9; }");
            out.println("        .btn-secondary { background: linear-gradient(135deg, #17a2b8 0%, #117a8b 100%); }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("    <div class=\"container\">");
            out.println("        <div class=\"header\">");
            out.println("            <h1>NutriDyn Automation</h1>");
            out.println("            <p style=\"font-size: 1.2em; margin: 10px 0;\">Test Report with Detailed Steps</p>");
            out.println("            <div class=\"timestamp\">Generated on: " + RUN_DATE + " at " + RUN_TIME.replace("-", ":") + "</div>");
            out.println("        </div>");
            out.println("        <div class=\"summary\">");
            out.println("            <div class=\"summary-card\">");
            out.println("                <h3>Overall Status</h3>");
            out.println("                <div class=\"status-badge " + statusBadgeClass + "\">" + overallStatus + "</div>");
            out.println("            </div>");
            out.println("            <div class=\"summary-card\">");
            out.println("                <h3>Total Steps</h3>");
            out.println("                <div class=\"number\">" + totalSteps + "</div>");
            out.println("            </div>");
            out.println("            <div class=\"summary-card\">");
            out.println("                <h3>Passed</h3>");
            out.println("                <div class=\"number\" style=\"color: #28a745;\">" + passedSteps + "</div>");
            out.println("            </div>");
            out.println("            <div class=\"summary-card\">");
            out.println("                <h3>Failed</h3>");
            out.println("                <div class=\"number\" style=\"color: #dc3545;\">" + failedSteps + "</div>");
            out.println("            </div>");
            out.println("            <div class=\"summary-card\">");
            out.println("                <h3>Pass Rate</h3>");
            out.println("                <div class=\"number\">" + String.format("%.1f", passRate) + "%</div>");
            out.println("                <div class=\"progress-bar\">");
            out.println("                    <div class=\"progress-fill\" style=\"width: " + passRate + "%\"></div>");
            out.println("                </div>");
            out.println("            </div>");
            out.println("        </div>");
            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            out.println("        <div style=\"text-align: center; margin: 20px 0;\">");
            out.println("            <p><strong>Test Duration:</strong> " + START_TIME + " to " + currentTime + "</p>");
            out.println("        </div>");
            out.println("        <div class=\"test-results\">");
            out.println("            <h2>Detailed Test Results</h2>");
            out.println("            <p style=\"color: #666; margin-bottom: 30px;\">Each step shows a <strong>small thumbnail</strong> of the saved screenshot for quick scanning. Use <strong>View Local</strong> or <strong>View full size</strong> to open the full-resolution PNG (still full-page capture on disk).</p>");
            for (String step : htmlSteps) {
                out.println(step);
            }
            out.println("        </div>");
            out.println("        <div style=\"margin-top: 50px; padding-top: 20px; border-top: 1px solid #dee2e6; text-align: center; color: #6c757d;\">");
            out.println("            <p>Generated by NutriDyn Automation Framework</p>");
            out.println("        </div>");
            out.println("    </div>");
            out.println("</body>");
            out.println("</html>");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
