import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;
import java.time.Duration;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class Colbrook {

    static final Properties config = new Properties();

    // Create organized folder structure: Date \ Time
    static String RUN_DATE = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    static String RUN_TIME = new SimpleDateFormat("HH-mm-ss").format(new Date());

    // Tracking Statistics
    static int totalSteps = 0;
    static int passedSteps = 0;
    static int failedSteps = 0;
    static String START_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    static java.util.List<String> htmlSteps = new java.util.ArrayList<>();

    static String IMGBB_API_KEY;
    static String SS_DIR;
    static String HTML_DIR;
    static String CSV_PATH;

    static void loadConfig() throws IOException {
        try (InputStream in = Colbrook.class.getResourceAsStream("/config.properties")) {
            if (in != null) config.load(in);
        }
        if (config.isEmpty()) {
            try (InputStream in = new FileInputStream("config.properties")) {
                config.load(in);
            }
        }
        String base = getConfig("basePath", "C:/Users/deepa/Documents/Automation/Colbrook").replace("\\", "/");
        IMGBB_API_KEY = getConfig("imgbbApiKey", "46866c7eef7ee62b26a79f32a5d57a08");
        SS_DIR = base + "/screenshots/" + RUN_DATE + "/" + RUN_TIME;
        HTML_DIR = base + "/html/" + RUN_DATE + "/" + RUN_TIME;
        CSV_PATH = getConfig("csvPath", base + "/Colbrook.csv").replace("\\", "/");
    }

    static String getConfig(String key, String def) {
        String v = config.getProperty(key);
        return v != null ? v.trim() : def;
    }

    static int getConfigInt(String key, int def) {
        String v = config.getProperty(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    public static void main(String[] args) throws Exception {
        loadConfig();

        ChromeOptions options = new ChromeOptions();
        if (getConfig("useIncognito", "true").equalsIgnoreCase("true")) {
            options.addArguments("--incognito");
        }

        WebDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.get("https://www.colbrookkitchen.com/");
        waitForPageToLoad(driver);

        takeScreenshot(driver, "homepage");

        // Add your Colbrook test steps here, e.g.:
         product(driver);
      login(driver);

        driver.quit();
    }



    private static void product(WebDriver driver) throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Actions actions = new Actions(driver);

        WebElement coolTools = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//span[normalize-space()='Cool Tools']")));
        actions.moveToElement(coolTools).perform();

        safeClick(driver, By.xpath("//a[@id='ui-id-5']//span[contains(text(),'Gifts')]"));
        takeScreenshot(driver, "Category_Gifts");


        safeClick(driver, By.xpath("//a[@class='product-item-link'][normalize-space()='Cuisipro 4-sided Box Grater - C61']"));
        takeScreenshot(driver, "Product_Page");
        closeAbandonCartPopup(driver);
        safeClick(driver, By.xpath("//button[@id='product-addtocart-button']"));
        takeScreenshot(driver, "Product_Added_to_Cart");
        closeAddToCartPopup(driver);

        safeClick(driver, By.xpath("//a[@class='action showcart']"));
        takeScreenshot(driver, "View_Cart");
        safeClick(driver, By.xpath("//span[normalize-space()='View and Edit Cart']"));
        waitForPageToLoad(driver);
        closeAbandonCartPopup(driver);
        takeScreenshot(driver, "View_and_Edit_Cart");

        // Find qty input by "Qty" (testRigor path: Qty) - table column or first cart qty input
        WebElement qtyInput = null;
        for (By locator : new By[]{
                By.xpath("//th[contains(.,'Qty')]/ancestor::table//tbody//input[contains(@id,'qty')]"),
                By.xpath("//main[contains(@id,'maincontent')]//input[contains(@id,'qty')]"),
                By.cssSelector("input[id*='cart-'][id*='-qty']")
        }) {
            try {
                qtyInput = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
                break;
            } catch (Exception ignored) { }
        }
        if (qtyInput != null) {
            qtyInput.clear();
            qtyInput.sendKeys("4");
        }
        safeClick(driver, By.xpath("//span[normalize-space()='Update Shopping Cart']"));
        Thread.sleep(2000);
        takeScreenshot(driver, "Cart_Qty_Updated");
        safeClick(driver, By.xpath("//div[@id='block-discount']//div[@role='tab']"));
        Thread.sleep(500);
        WebElement couponInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("coupon_code")));
        couponInput.clear();
        couponInput.sendKeys(getConfig("discountCode", "exitest"));
        safeClick(driver, By.xpath("//button[@value='Apply Discount']"));
        Thread.sleep(5000);
        takeScreenshot(driver, "Discount_Applied");
        safeClick(driver, By.xpath("//span[normalize-space()='Proceed to Checkout']"));
        Thread.sleep(5000);
        takeScreenshot(driver, "Checkout");

        WebElement emailInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("customer-email")));
        emailInput.clear();
        emailInput.sendKeys(getConfig("email", "deepak.maheshwari@exinent.com"));
        // First Name (testRigor path: First Name)
        WebElement firstNameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
        By.xpath("//label[contains(.,'First Name')]/following-sibling::input | //input[@name='firstname'] | //input[@placeholder='First Name']")));
        firstNameInput.clear();
        firstNameInput.sendKeys("deepak");

        // Last Name (testRigor path: Last Name)
        WebElement lastNameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
        By.xpath("//label[contains(.,'Last Name')]/following-sibling::input | //input[@name='lastname'] | //input[@placeholder='Last Name']")));
        lastNameInput.clear();
        lastNameInput.sendKeys("Maheshwari");

        // Street Address: Line 1 (testRigor path: Street Address: Line 1)
        WebElement streetInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
        By.xpath("//label[contains(.,'Street Address') and contains(.,'Line 1')]/following-sibling::input | //input[@name='street[0]'] | //input[contains(@name,'street')]")));
        streetInput.clear();
        streetInput.sendKeys("Califonia");

        // Country (testRigor path: Country) - United States
        WebElement countrySelect = wait.until(ExpectedConditions.visibilityOfElementLocated(
        By.xpath("//label[contains(.,'Country')]/following-sibling::select | //select[@name='country_id'] | //select[contains(@name,'country')]")));
        new Select(countrySelect).selectByVisibleText("United States");

        // State/Province (testRigor path: State/Province)
        WebElement stateField = wait.until(ExpectedConditions.visibilityOfElementLocated(
        By.xpath("//label[contains(.,'State') or contains(.,'Province')]/following-sibling::select | //select[@name='region_id'] | //input[@name='region']")));
        if (stateField.getTagName().equalsIgnoreCase("select")) {
            new Select(stateField).selectByVisibleText("California");
        } else {
            stateField.clear();
            stateField.sendKeys("California");
        }

        // City (testRigor path: City)
        WebElement cityInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
        By.xpath("//label[contains(.,'City')]/following-sibling::input | //input[@name='city'] | //input[@placeholder='City']")));
        cityInput.clear();
        cityInput.sendKeys("California");

        // Zip/Postal Code (testRigor path: Zip/Postal Code)
        WebElement zipInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
        By.xpath("//label[contains(.,'Zip') or contains(.,'Postal Code')]/following-sibling::input | //input[@name='postcode'] | //input[@placeholder='Zip']")));
        zipInput.clear();
        zipInput.sendKeys("90001");

        // Phone Number (testRigor path: Phone Number)
        WebElement phoneInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
        By.xpath("//label[contains(.,'Phone Number')]/following-sibling::input | //input[@name='telephone'] | //input[@placeholder='Phone Number']")));
        phoneInput.clear();
        phoneInput.sendKeys("987099521");

        // Wait for shipping methods to load (can take some time), then take full page screenshot
        WebDriverWait shippingWait = new WebDriverWait(driver, Duration.ofSeconds(30));
        shippingWait.until(ExpectedConditions.visibilityOfElementLocated(
        By.cssSelector("#opc-shipping_method .step-content, #checkout-shipping-method-load, .table-checkout-shipping-method")));
        Thread.sleep(1500);

        // Select shipping method: flat rate
        safeClick(driver, By.cssSelector("input[value='flatrate_flatrate']"));
        Thread.sleep(500);

        // Full page screenshot before Next button
        takeScreenshot(driver, "Shipping_Methods_Loaded");

        // Next (testRigor path: Next)
        safeClick(driver, By.xpath("//button[@data-role='opc-continue'] | //button[.//span[normalize-space()='Next']] | //span[normalize-space()='Next']/ancestor::button"));
        waitForPageToLoad(driver);

        // Wait for payment step to load, then click Credit Card (testRigor path: Credit Card)
        WebDriverWait paymentWait = new WebDriverWait(driver, Duration.ofSeconds(20));
        paymentWait.until(ExpectedConditions.visibilityOfElementLocated(
        By.xpath("//*[contains(.,'Credit Card') or contains(.,'Payment')]")));
        Thread.sleep(1500);
        safeClick(driver, By.xpath("//label[contains(.,'Credit Card')] | //*[normalize-space()='Credit Card'] | //input[following-sibling::*[contains(.,'Credit Card')]]"));
        Thread.sleep(1500);
        takeScreenshot(driver, "Payment_Page");

        // Go to homepage and check search functionality
        driver.get("https://www.colbrookkitchen.com/");
        waitForPageToLoad(driver);
        closeAbandonCartPopup(driver);
        takeScreenshot(driver, "Homepage_After_Checkout");

        String validTerms = getConfig("validSearchTerms", "grater,knife");
        String invalidTerms = getConfig("invalidSearchTerms", "pizza,unicorn");

        for (String term : validTerms.split(",")) {
            String t = term.trim();
            if (t.isEmpty()) continue;
            driver.get("https://www.colbrookkitchen.com/");
            waitForPageToLoad(driver);
            WebElement searchInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("search")));
            searchInput.clear();
            searchInput.sendKeys(t);
            searchInput.sendKeys(Keys.ENTER);
            waitForPageToLoad(driver);
            Thread.sleep(2000);
            takeScreenshot(driver, "Search_Valid_" + t.replace(" ", "_"));
        }

        for (String term : invalidTerms.split(",")) {
            String t = term.trim();
            if (t.isEmpty()) continue;
            driver.get("https://www.colbrookkitchen.com/");
            waitForPageToLoad(driver);
            WebElement searchInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("search")));
            searchInput.clear();
            searchInput.sendKeys(t);
            searchInput.sendKeys(Keys.ENTER);
            waitForPageToLoad(driver);
            Thread.sleep(2000);
            takeScreenshot(driver, "Search_Invalid_" + t.replace(" ", "_"));
        }
    }

    
    private static void login(WebDriver driver) throws Exception{
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        // Sign In link (testRigor path: Sign In)
        safeClick(driver, By.xpath("//a[normalize-space()='Sign In']"));
        waitForPageToLoad(driver);
        takeScreenshot(driver, "Login_Page");

        WebElement emailInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("email")));
        emailInput.clear();
        emailInput.sendKeys(getConfig("email", "deepak.maheshwari@exinent.com"));
        WebElement passwordInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("password")));
        passwordInput.clear();
        passwordInput.sendKeys(getConfig("password", "Admin@123"));
        Thread.sleep(500);

        // Login button (testRigor path: Sign In) - wait, scroll into view, then click with JS
        WebDriverWait btnWait = new WebDriverWait(driver, Duration.ofSeconds(15));
        WebElement loginBtn = null;
        for (By by : new By[]{ By.id("send2"), By.cssSelector("button.action.login.primary"), By.xpath("//button[@type='submit']//span[normalize-space()='Sign In']/..") }) {
            try {
                loginBtn = btnWait.until(ExpectedConditions.presenceOfElementLocated(by));
                if (loginBtn != null && loginBtn.isDisplayed()) break;
            } catch (Exception ignored) { }
        }
        if (loginBtn != null) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", loginBtn);
            Thread.sleep(300);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginBtn);
        } else {
            safeClick(driver, By.id("send2"));
        }
        waitForPageToLoad(driver);
        Thread.sleep(2000);
        takeScreenshot(driver, "Login_Success");

        // Go to home page and click Add to Wish List (testRigor path: Add to Wish List)
        driver.get("https://www.colbrookkitchen.com/");
        waitForPageToLoad(driver);
        Thread.sleep(1500);
        WebDriverWait wishlistWait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement addToWishList = null;
        for (By by : new By[]{
                By.linkText("Add to Wish List"),
                By.xpath("//a[normalize-space()='Add to Wish List']"),
                By.xpath("//a[contains(text(),'Add to Wish List')]"),
                By.cssSelector("a.action.towishlist")
        }) {
            try {
                addToWishList = wishlistWait.until(ExpectedConditions.elementToBeClickable(by));
                if (addToWishList != null && addToWishList.isDisplayed()) break;
            } catch (Exception ignored) { }
        }
        if (addToWishList != null) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", addToWishList);
            Thread.sleep(300);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", addToWishList);
            waitForPageToLoad(driver);
            Thread.sleep(2000);
            takeScreenshot(driver, "Wishlist_Page");
        } else {
            safeClick(driver, By.linkText("Add to Wish List"));
            waitForPageToLoad(driver);
            Thread.sleep(2000);
            takeScreenshot(driver, "Wishlist_Page");
        }

        // Go to home and click Add to Compare (testRigor path: Add to Compare)
        driver.get("https://www.colbrookkitchen.com/");
        waitForPageToLoad(driver);
        Thread.sleep(1500);
        WebDriverWait compareWait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement addToCompare = null;
        for (By by : new By[]{
                By.linkText("Add to Compare"),
                By.xpath("//a[normalize-space()='Add to Compare']"),
                By.xpath("//a[contains(text(),'Add to Compare')]"),
                By.cssSelector("a.action.tocompare")
        }) {
            try {
                addToCompare = compareWait.until(ExpectedConditions.elementToBeClickable(by));
                if (addToCompare != null && addToCompare.isDisplayed()) break;
            } catch (Exception ignored) { }
        }
        if (addToCompare != null) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", addToCompare);
            Thread.sleep(300);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", addToCompare);
        } else {
            safeClick(driver, By.linkText("Add to Compare"));
        }
        waitForPageToLoad(driver);
        Thread.sleep(2000);
        takeScreenshot(driver, "Add_To_Compare");

        // Click "comparison list" to go to comparison page
        safeClick(driver, By.xpath("//a[normalize-space()='comparison list']"));
        waitForPageToLoad(driver);
        Thread.sleep(2000);
        takeScreenshot(driver, "Comparison_List_Page");
    }

    /** Close the abandon-cart email popup by clicking "No thank you, not this time". */
    public static void closeAbandonCartPopup(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));
            WebElement noThanks = wait.until(ExpectedConditions.elementToBeClickable(
                    By.id("bw_no_thanks")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", noThanks);
            Thread.sleep(300);
        } catch (Exception ignored) {
            // Abandon cart popup not present
        }
    }

    /** Close the add-to-cart popup by clicking "Continue Shopping". */
    public static void closeAddToCartPopup(WebDriver driver) {
        try {
            Thread.sleep(1500); // Wait for popup animation to show
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        try {
            // Wait for popup to be visible
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".aw-acp-popup[data-role='aw-acp-ui']")));
        } catch (Exception e) {
            System.out.println("Add-to-cart popup did not appear: " + e.getMessage());
            return;
        }
        // Try multiple selectors and click methods
        By[] selectors = {
                By.xpath("//a[contains(@class,'aw-acp-popup__close') and @data-action='continue']"),
                By.xpath("//button[contains(@class,'aw-acp-popup__mobile-close') and @data-action='continue']"),
                By.xpath("//a[normalize-space()='Continue Shopping']"),
                By.xpath("//button[normalize-space()='Continue Shopping']"),
                By.cssSelector(".aw-acp-popup__close[data-action='continue']"),
                By.cssSelector("button.aw-acp-popup__mobile-close[data-action='continue']")
        };
        for (By by : selectors) {
            try {
                WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(by));
                if (btn.isDisplayed()) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", btn);
                    Thread.sleep(300);
                    try {
                        btn.click();
                    } catch (Exception e) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
                    }
                    Thread.sleep(500);
                    System.out.println("Add-to-cart popup closed.");
                    return;
                }
            } catch (Exception ignored) {
                // Try next selector
            }
        }
        // Fallback: close via Magnific Popup if present
        try {
            WebElement mfpClose = driver.findElement(By.cssSelector(".mfp-close, button.mfp-close"));
            if (mfpClose.isDisplayed()) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", mfpClose);
                Thread.sleep(500);
                System.out.println("Popup closed via mfp-close.");
            }
        } catch (Exception ignored) {
            System.out.println("Could not close add-to-cart popup.");
        }
    }

    // ------------------ Global page load wait ------------------
    public static void waitForPageToLoad(WebDriver driver) {
        int secs = getConfigInt("pageLoadTimeout", 30);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(secs));
        try {
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState").equals("complete"));
            wait.until(webDriver -> ((Long) ((JavascriptExecutor) webDriver)
                    .executeScript("return window.jQuery != undefined && jQuery.active == 0 ? 1 : 0")) == 1);
        } catch (Exception e) {
            System.out.println("Page load wait timed out: " + e.getMessage());
        }
    }

    public static boolean safeClick(WebDriver driver, By locator) {
        int maxAttempts = getConfigInt("maxRetries", 3);
        int attempts = 0;
        while (attempts < maxAttempts) {
            try {
                WebElement elem = driver.findElement(locator);
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", elem);
                try {
                    waitForElementToBeClickable(driver, elem);
                    elem.click();
                    return true;
                } catch (TimeoutException te) {
                    System.out.println("Timeout waiting for element to be clickable. Attempting JS Click fallback...");
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", elem);
                    return true;
                }
            } catch (ElementNotInteractableException e) {
                System.out.println("Click intercepted or not interactable. Retrying...");
                try { Thread.sleep(1000); } catch (Exception ignored) {}
            } catch (Exception e) {
                System.out.println("Exception during click: " + e.getMessage());
                try { Thread.sleep(1000); } catch (Exception ignored) {}
            }
            attempts++;
        }
        try {
            takeScreenshot(driver, "Failed_Click", false, "Unable to click element: " + locator);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Unable to click: " + locator + " - Continuing script...");
        return false;
    }

    public static void waitForElementToBeClickable(WebDriver driver, WebElement element) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.elementToBeClickable(element));
    }

    // ------------------ Screenshot + Upload + CSV + HTML ------------------
    private static void takeScreenshot(WebDriver driver, String title) throws IOException {
        takeScreenshot(driver, title, true, "Step completed successfully");
    }

    private static void takeScreenshot(WebDriver driver, String title, boolean isPass, String details) throws IOException {
        totalSteps++;
        if (isPass) passedSteps++;
        else failedSteps++;

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String statusPrefix = isPass ? "SUCCESS_" : "ERROR_";
        String fileName = statusPrefix + title + "_" + timestamp + ".png";

        File folder = new File(SS_DIR);
        if (!folder.exists()) folder.mkdirs();

        File outputFile = new File(folder, fileName);
        File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Files.copy(src.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());

        String uploadedUrl = "Upload failed/skipped";
        try {
            uploadedUrl = uploadToImgbb(outputFile);
            System.out.println("Uploaded URL: " + uploadedUrl);
        } catch (Exception e) {
            System.out.println("Could not upload to Imgbb: " + e.getMessage());
        }

        writeCsv(timestamp, title, uploadedUrl, outputFile.getName());
        writeHtmlReport(timestamp, title, outputFile.getName(), uploadedUrl, isPass, details);
    }

    private static String uploadToImgbb(File imageFile) throws IOException {
        byte[] fileContent = Files.readAllBytes(imageFile.toPath());
        String encodedImage = Base64.getEncoder().encodeToString(fileContent);

        String data = "key=" + IMGBB_API_KEY +
                "&image=" + URLEncoder.encode(encodedImage, "UTF-8");

        URL url = new URL("https://api.imgbb.com/1/upload");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");

        OutputStream os = conn.getOutputStream();
        os.write(data.getBytes());
        os.flush();
        os.close();

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) response.append(line);
        br.close();

        String json = response.toString();
        return json.split("\"url\":\"")[1].split("\"")[0].replace("\\/", "/");
    }

    private static void writeCsv(String timestamp, String title, String url, String localFileName) {
        File fileObj = new File(CSV_PATH);
        if (!fileObj.getParentFile().exists()) {
            fileObj.getParentFile().mkdirs();
        }
        boolean fileExists = fileObj.exists();

        try (FileWriter fw = new FileWriter(CSV_PATH, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            if (!fileExists) {
                out.println("Timestamp,Title,LocalFile,UploadedURL");
            }
            out.println(timestamp + "," + title + "," + localFileName + "," + url);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeHtmlReport(String timestamp, String title, String localFileName, String url, boolean isPass, String details) {
        File htmlFolder = new File(HTML_DIR);
        if (!htmlFolder.exists()) htmlFolder.mkdirs();

        String htmlFile = HTML_DIR + "\\TestReport.html";
        String relativeImgPath = "../../../screenshots/" + RUN_DATE + "/" + RUN_TIME + "/" + localFileName;

        String stepStatusClass = isPass ? "pass" : "fail";
        String stepStatusIcon = isPass ? "✅" : "❌";

        StringBuilder stepHtml = new StringBuilder();
        stepHtml.append("            <div class=\"test-step ").append(stepStatusClass).append("\">\n");
        stepHtml.append("                <div class=\"step-header\">\n");
        stepHtml.append("                    <span>").append(stepStatusIcon).append(" ").append(title.replace("_", " ").toUpperCase()).append("</span>\n");
        stepHtml.append("                    <span class=\"step-time\">").append(timestamp.split("_")[1].replace("-", ":")).append("</span>\n");
        stepHtml.append("                </div>\n");
        stepHtml.append("                <div class=\"step-details\">").append(details).append("</div>\n");
        stepHtml.append("                <div style=\"margin-top: 15px;\">\n");
        stepHtml.append("                    <a href=\"").append(relativeImgPath).append("\" target=\"_blank\">\n");
        stepHtml.append("                        <img class=\"screenshot\" src=\"").append(relativeImgPath).append("\" alt=\"").append(title).append("\">\n");
        stepHtml.append("                    </a>\n");
        stepHtml.append("                </div>\n");
        stepHtml.append("                <div style=\"margin-top: 10px;\">\n");
        stepHtml.append("                    <a class=\"btn\" href=\"").append(relativeImgPath).append("\" target=\"_blank\">View Local</a>\n");
        if (url != null && url.startsWith("http")) {
            stepHtml.append("                    <a class=\"btn imgbb\" href=\"").append(url).append("\" target=\"_blank\">View ImgBB</a>\n");
        }
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
            out.println("    <title>Colbrook Kitchen Automation - Enhanced Test Report</title>");
            out.println("    <style>");
            out.println("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 20px; background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%); }");
            out.println("        .container { max-width: 1400px; margin: 0 auto; background: white; border-radius: 15px; box-shadow: 0 10px 30px rgba(0,0,0,0.1); padding: 40px; }");
            out.println("        .header { text-align: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px; border-radius: 15px; margin-bottom: 40px; }");
            out.println("        .header h1 { margin: 0; font-size: 2.5em; font-weight: 300; }");
            out.println("        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 25px; margin-bottom: 40px; }");
            out.println("        .summary-card { background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); padding: 25px; border-radius: 15px; text-align: center; border-left: 6px solid #667eea; transition: transform 0.3s; }");
            out.println("        .summary-card:hover { transform: translateY(-5px); }");
            out.println("        .summary-card h3 { margin: 0 0 15px 0; color: #333; font-size: 1.1em; }");
            out.println("        .summary-card .number { font-size: 2.5em; font-weight: bold; color: #333; margin-bottom: 10px; }");
            out.println("        .progress-bar { width: 100%; height: 25px; background-color: #e9ecef; border-radius: 12px; overflow: hidden; margin: 15px 0; }");
            out.println("        .progress-fill { height: 100%; background: linear-gradient(90deg, #28a745, #20c997); transition: width 1s ease; border-radius: 12px; }");
            out.println("        .test-results { margin: 40px 0; display: flex; flex-direction: column; gap: 15px; }");
            out.println("        .test-step { margin: 15px 0; padding: 20px; border-radius: 12px; border-left: 6px solid; transition: all 0.3s; }");
            out.println("        .test-step:hover { transform: translateX(5px); }");
            out.println("        .test-step.pass { background: linear-gradient(135deg, #d4edda 0%, #c3e6cb 100%); border-left-color: #28a745; }");
            out.println("        .test-step.fail { background: linear-gradient(135deg, #f8d7da 0%, #f5c6cb 100%); border-left-color: #dc3545; }");
            out.println("        .step-header { font-weight: bold; margin-bottom: 12px; font-size: 1.1em; }");
            out.println("        .step-details { font-size: 0.95em; color: #666; line-height: 1.5; }");
            out.println("        .step-time { font-size: 0.85em; color: #888; float: right; background: rgba(0,0,0,0.05); padding: 4px 8px; border-radius: 5px; }");
            out.println("        .screenshot { max-width: 300px; border-radius: 8px; margin: 15px 0; border: 2px solid #ddd; transition: transform 0.3s; cursor: pointer; }");
            out.println("        .screenshot:hover { transform: scale(1.05); }");
            out.println("        .timestamp { text-align: center; color: #666; margin: 25px 0; font-size: 1.1em; }");
            out.println("        .status-badge { display: inline-block; padding: 8px 20px; border-radius: 25px; color: white; font-weight: bold; }");
            out.println("        .status-pass { background: linear-gradient(135deg, #28a745 0%, #20c997 100%); }");
            out.println("        .status-fail { background: linear-gradient(135deg, #dc3545 0%, #c82333 100%); }");
            out.println("        .btn { display: inline-block; padding: 8px 15px; background: linear-gradient(135deg, #28a745 0%, #20c997 100%); color: white; text-decoration: none; border-radius: 20px; font-weight: bold; font-size: 0.9em; margin-right: 10px; transition: opacity 0.3s; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }");
            out.println("        .btn:hover { opacity: 0.9; }");
            out.println("        .btn.imgbb { background: linear-gradient(135deg, #17a2b8 0%, #117a8b 100%); }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("    <div class=\"container\">");
            out.println("        <div class=\"header\">");
            out.println("            <h1>🛒 Colbrook Kitchen Automation</h1>");
            out.println("            <p style=\"font-size: 1.2em; margin: 10px 0;\">Enhanced Test Report with Detailed Steps</p>");
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
            out.println("            <h2>📋 Detailed Test Results</h2>");
            out.println("            <p style=\"color: #666; margin-bottom: 30px;\">Comprehensive step-by-step execution details with visual evidence</p>");
            for (String step : htmlSteps) {
                out.println(step);
            }
            out.println("        </div>");
            out.println("        <div style=\"margin-top: 50px; padding-top: 20px; border-top: 1px solid #dee2e6; text-align: center; color: #6c757d;\">");
            out.println("            <p>🤖 Generated by Colbrook Kitchen Automation Framework v2.0</p>");
            out.println("            <p><small>Report updated dynamically during execution.</small></p>");
            out.println("        </div>");
            out.println("    </div>");
            out.println("</body>");
            out.println("</html>");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
