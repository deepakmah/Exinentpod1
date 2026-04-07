package Mcfeels;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
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
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class mcfeels {

    private static final String SITEMAP_URL = "https://www.mcfeelys.com/pub/sitemap.xml";
    private static final String SITEMAP_NS = "http://www.sitemaps.org/schemas/sitemap/0.9";
    private static final String SITEMAP_IMAGE_NS = "http://www.google.com/schemas/sitemap-image/1.1";
    private static final Random RANDOM = new Random();

    /** URLs chosen from the live sitemap (category = no product images; product = has image:image). */
    static class SitemapPick {
        final String categoryUrl;
        final String productUrl;
        final String searchKeyword;

        SitemapPick(String categoryUrl, String productUrl, String searchKeyword) {
            this.categoryUrl = categoryUrl;
            this.productUrl = productUrl;
            this.searchKeyword = searchKeyword;
        }
    }

    /** All category and product URLs from the sitemap (parsed once per run). */
    static class SitemapCatalog {
        final List<String> categories;
        final List<String> products;

        SitemapCatalog(List<String> categories, List<String> products) {
            this.categories = categories;
            this.products = products;
        }
    }

    static String IMGBB_API_KEY = "3b23b07a37fbcee41d4984d100162a10";
    
    // Create organized folder structure: Date \ Time
    static String RUN_DATE = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    static String RUN_TIME = new SimpleDateFormat("HH-mm-ss").format(new Date());
    
    // Tracking Statistics
    static int totalSteps = 0;
    static int passedSteps = 0;
    static int failedSteps = 0;
    static String START_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    static java.util.List<String> htmlSteps = new java.util.ArrayList<>();

    // Separate paths for screenshots and html
    static String SS_DIR = "C:\\Users\\deepa\\Documents\\Automation\\Mcfeels\\screenshots\\" + RUN_DATE + "\\" + RUN_TIME;
    static String HTML_DIR = "C:\\Users\\deepa\\Documents\\Automation\\Mcfeels\\html\\" + RUN_DATE + "\\" + RUN_TIME;

    // Common CSV File
    static String CSV_PATH = "C:\\Users\\deepa\\Documents\\Automation\\Mcfeels\\Mcfeels.csv";

    public static void main(String[] args) throws Exception {

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--incognito");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.get("https://mcfeelys.com/");
        waitForPageToLoad(driver);

        dismissPopupIfExists(driver);
        takeScreenshot(driver, "homepage");

        SitemapCatalog catalog = loadSitemapCatalog();
        SitemapPick pick = product(driver, catalog);
        product_search(driver, pick, catalog);
        if (pick != null) {
            checkout(driver);
        } else {
            System.out.println("Skipping checkout (cart flow was skipped — no purchasable product).");
        }

         driver.quit();
    }

    // ------------------ Global page load wait ------------------
    public static void waitForPageToLoad(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        try {
            // Wait for document.readyState == complete
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState").equals("complete"));

            // Wait for jQuery AJAX to complete if jQuery is present
            wait.until(webDriver -> ((Long) ((JavascriptExecutor) webDriver)
                    .executeScript("return window.jQuery != undefined && jQuery.active == 0 ? 1 : 0")) == 1);
        } catch (Exception e) {
            System.out.println("Page load wait timed out: " + e.getMessage());
        }
    }

    /** Klaviyo signup flyover (often above page content). */
    public static void dismissKlaviyoIfExists(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(6));
            WebElement close = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("button.klaviyo-close-form, button[aria-label='Close dialog']")));
            if (!close.isDisplayed()) {
                return;
            }
            wait.until(ExpectedConditions.elementToBeClickable(close));
            try {
                close.click();
            } catch (Exception e) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", close);
            }
            System.out.println("Klaviyo popup closed.");
            Thread.sleep(400);
        } catch (TimeoutException | NoSuchElementException ignored) {
        } catch (Exception e) {
            System.out.println("Klaviyo dismiss attempt: " + e.getMessage());
        }
    }

    public static void dismissPopupIfExists(WebDriver driver) {
        dismissKlaviyoIfExists(driver);
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(2));
            WebElement popup = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("div.modal-inner-wrap, div.modals-overlay")
            ));

            try {
                WebElement closeBtn = popup.findElement(By.cssSelector("button.action-close[data-role='closeBtn']"));
                closeBtn.click();
                System.out.println("Popup closed successfully.");
            } catch (NoSuchElementException e) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].style.display='none';", popup);
                System.out.println("Popup hidden by JS.");
            }

            Thread.sleep(500);
        } catch (Exception ignored) {}
        dismissKlaviyoIfExists(driver);
    }

    public static boolean safeClick(WebDriver driver, By locator) {
        int attempts = 0;
        while (attempts < 3) {
            try {
                dismissPopupIfExists(driver);
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

    private static final int MAX_PRODUCT_TRIES = 30;
    /** Randomly add this many distinct in-stock products to cart (inclusive range). */
    private static final int CART_PRODUCTS_MIN = 3;
    private static final int CART_PRODUCTS_MAX = 4;

    /**
     * Loads {@link #SITEMAP_URL} and returns all category URLs (no image entries) and product URLs (with image:image).
     */
    public static SitemapCatalog loadSitemapCatalog() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(SITEMAP_URL).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setConnectTimeout(60_000);
        conn.setReadTimeout(180_000);
        conn.setInstanceFollowRedirects(true);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc;
        try (InputStream in = conn.getInputStream()) {
            doc = db.parse(in);
        } finally {
            conn.disconnect();
        }

        NodeList urlNodes = doc.getElementsByTagNameNS(SITEMAP_NS, "url");
        List<String> categories = new ArrayList<>();
        List<String> products = new ArrayList<>();

        for (int i = 0; i < urlNodes.getLength(); i++) {
            Element urlEl = (Element) urlNodes.item(i);
            NodeList locs = urlEl.getElementsByTagNameNS(SITEMAP_NS, "loc");
            if (locs.getLength() == 0) {
                continue;
            }
            String loc = locs.item(0).getTextContent().trim();
            if (loc.isEmpty()) {
                continue;
            }
            boolean isProduct = urlEl.getElementsByTagNameNS(SITEMAP_IMAGE_NS, "image").getLength() > 0;
            if (isProduct) {
                products.add(loc);
            } else if (loc.endsWith(".html")) {
                URI u = new URI(loc);
                String path = u.getPath();
                if (path != null && path.length() > 1) {
                    categories.add(loc);
                }
            }
        }

        if (categories.isEmpty() || products.isEmpty()) {
            throw new IllegalStateException(
                    "Sitemap parsing failed: categories=" + categories.size() + ", products=" + products.size());
        }

        System.out.println("Sitemap loaded: " + categories.size() + " categories, " + products.size() + " products.");
        return new SitemapCatalog(Collections.unmodifiableList(categories), Collections.unmodifiableList(products));
    }

    /**
     * True when the PDP shows an enabled, visible {@code #product-addtocart-button} (skips out-of-stock / notify-only pages).
     */
    static boolean isPurchasableAddToCartVisible(WebDriver driver, int waitSeconds) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
        try {
            return Boolean.TRUE.equals(wait.until(d -> {
                List<WebElement> els = d.findElements(By.cssSelector("#product-addtocart-button"));
                if (els.isEmpty()) {
                    return false;
                }
                WebElement b = els.get(0);
                try {
                    return b.isDisplayed() && b.isEnabled();
                } catch (StaleElementReferenceException e) {
                    return false;
                }
            }));
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** Picks a reasonable single-word search term from the product page slug. */
    static String keywordFromProductUrl(String productUrl) throws Exception {
        URI u = new URI(productUrl);
        String path = u.getPath();
        if (path == null) {
            return "wood";
        }
        int slash = path.lastIndexOf('/');
        String slug = path.substring(slash + 1).replace(".html", "");
        String[] parts = slug.split("-");
        String[] stop = {"qty", "with", "head", "flat", "drive", "steel", "stainless", "wood", "inch"};
        for (String p : parts) {
            if (p.length() < 4 || p.matches("\\d+")) {
                continue;
            }
            String pl = p.toLowerCase();
            boolean skip = false;
            for (String s : stop) {
                if (pl.equals(s)) {
                    skip = true;
                    break;
                }
            }
            if (!skip) {
                return pl.length() > 24 ? pl.substring(0, 24) : pl;
            }
        }
        return "screw";
    }

    static void clickFirstConfigurableOptionIfPresent(WebDriver driver) {
        List<WebElement> links = driver.findElements(
                By.cssSelector(".fieldset.configurable table tbody tr td a, div.swatch-attribute .swatch-option"));
        if (!links.isEmpty()) {
            WebElement first = links.get(0);
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", first);
            try {
                first.click();
            } catch (Exception e) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", first);
            }
        }
    }

    static boolean isGroupedProductPage(WebDriver driver) {
        return !driver.findElements(By.cssSelector("#super-product-table, table.data.grouped"))
                .isEmpty();
    }

    /**
     * Grouped products require qty &gt; 0 on at least one child before Add to cart is enabled.
     * Increments the first row’s qty once (0 → 1) via {@code .qty-inc}, or sets the first qty input to 1.
     */
    static void increaseFirstGroupedChildQty(WebDriver driver) {
        List<WebElement> incs = driver.findElements(
                By.cssSelector("#super-product-table tbody tr td.col.qty a.qty-inc"));
        if (incs.isEmpty()) {
            incs = driver.findElements(By.cssSelector("#super-product-table a.qty-inc"));
        }
        if (!incs.isEmpty()) {
            WebElement inc = incs.get(0);
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", inc);
            try {
                inc.click();
            } catch (Exception e) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", inc);
            }
            System.out.println("Grouped product: increased qty for first child item.");
            return;
        }
        List<WebElement> qtyInputs = driver.findElements(
                By.cssSelector("#super-product-table input.input-text.qty[name^='super_group']"));
        if (!qtyInputs.isEmpty()) {
            WebElement input = qtyInputs.get(0);
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", input);
            input.clear();
            input.sendKeys("1");
            System.out.println("Grouped product: set qty=1 on first child input.");
        }
    }

//    Product page

    /**
     * Opens a random category, then adds 3–4 distinct in-stock products from the sitemap.
     * Returns {@code null} if none could be added.
     */
    public static SitemapPick product(WebDriver driver, SitemapCatalog catalog) throws Exception {
        String categoryUrl = catalog.categories.get(RANDOM.nextInt(catalog.categories.size()));
        driver.get(categoryUrl);
        waitForPageToLoad(driver);
        dismissPopupIfExists(driver);
        takeScreenshot(driver, "category_page");

        int targetCount = CART_PRODUCTS_MIN + RANDOM.nextInt(CART_PRODUCTS_MAX - CART_PRODUCTS_MIN + 1);
        int maxTries = Math.min(MAX_PRODUCT_TRIES * 6, catalog.products.size());
        Set<Integer> triedIndices = new HashSet<>();
        Set<String> addedProductUrls = new HashSet<>();
        List<String> cartProductUrls = new ArrayList<>();

        while (addedProductUrls.size() < targetCount && triedIndices.size() < maxTries) {
            int idx = RANDOM.nextInt(catalog.products.size());
            if (!triedIndices.add(idx)) {
                continue;
            }
            String productUrl = catalog.products.get(idx);
            if (addedProductUrls.contains(productUrl)) {
                continue;
            }

            driver.get(productUrl);
            waitForPageToLoad(driver);
            dismissPopupIfExists(driver);

            boolean groupedPdp = isGroupedProductPage(driver);
            if (groupedPdp) {
                increaseFirstGroupedChildQty(driver);
                Thread.sleep(400);
                dismissKlaviyoIfExists(driver);
            }

            if (!isPurchasableAddToCartVisible(driver, 10)) {
                System.out.println("Skipping product (no enabled Add to cart / likely OOS): " + productUrl);
                continue;
            }

            takeScreenshot(driver, "Product_page_" + (addedProductUrls.size() + 1));
            if (!groupedPdp) {
                clickFirstConfigurableOptionIfPresent(driver);
                Thread.sleep(500);
            }
            dismissKlaviyoIfExists(driver);
            if (!isPurchasableAddToCartVisible(driver, 5)) {
                System.out.println("Skipping product (Add to cart not available after options/qty): " + productUrl);
                continue;
            }
            if (!safeClick(driver, By.cssSelector("#product-addtocart-button"))) {
                System.out.println("Skipping product (Add to cart click failed): " + productUrl);
                continue;
            }

            addedProductUrls.add(productUrl);
            cartProductUrls.add(productUrl);
            System.out.println("Added to cart (" + addedProductUrls.size() + "/" + targetCount + "): " + productUrl);
            Thread.sleep(800);
            dismissKlaviyoIfExists(driver);
        }

        if (cartProductUrls.isEmpty()) {
            System.out.println("No in-stock product found after " + maxTries + " tries; skipping cart and login steps.");
            takeScreenshot(driver, "product_flow_skipped_no_purchasable_product");
            return null;
        }

        if (cartProductUrls.size() < targetCount) {
            System.out.println("Added " + cartProductUrls.size() + " of " + targetCount
                    + " target products; continuing with cart.");
        }

        String chosenProductUrl = cartProductUrls.get(0);
        String keyword = keywordFromProductUrl(chosenProductUrl);
        SitemapPick pick = new SitemapPick(categoryUrl, chosenProductUrl, keyword);
        System.out.println("Cart products (" + cartProductUrls.size() + "): first for search keyword = " + chosenProductUrl);
        System.out.println("Search keyword: " + keyword);

        Thread.sleep(1000);
        takeScreenshot(driver, "Add_to_cart");

        safeClick(driver, By.xpath("//a[@class=\"action showcart\"]"));
        Thread.sleep(500);
        takeScreenshot(driver, "Mini_cart");

        safeClick(driver, By.xpath("//span[normalize-space()=\"View Cart\"]"));
        Thread.sleep(500);
        takeScreenshot(driver, "View_cart");

        if (cartProductUrls.size() < 3) {
            safeClick(driver, By.cssSelector("a.qty-inc"));
            Thread.sleep(2500);
            safeClick(driver, By.cssSelector("a.qty-inc"));
            safeClick(driver, By.cssSelector("button[title=\"Update Cart\"]"));
            Thread.sleep(3000);
            takeScreenshot(driver, "Update_cart");
        }

        driver.findElement(By.cssSelector("#coupon_code")).sendKeys("exitest");
        Thread.sleep(100);
        safeClick(driver, By.cssSelector("button[value=\"Apply Discount\"]"));
        Thread.sleep(3000);
        takeScreenshot(driver,"Discount_in_cart");



        safeClick(driver, By.cssSelector("button[data-role=\"proceed-to-checkout\"]"));
        Thread.sleep(100);
        driver.findElement(By.cssSelector("#email")).sendKeys("deepak.maheshwari@exinent.com");
        driver.findElement(By.cssSelector("#password")).sendKeys("Admin@123");
        Thread.sleep(800);

        safeClick(driver, By.cssSelector("#customer_form_login_popup_showPassword"));
        safeClick(driver, By.xpath("//button[@id=\"customer-form-login-popup-send2\"]"));
        Thread.sleep(200);
        takeScreenshot(driver,"User_login");

        Thread.sleep(5000);
        takeScreenshot(driver,"Logged_in");
        return pick;
    }

    // Product search and filters

    public static void product_search(WebDriver driver, SitemapPick pick, SitemapCatalog catalog) throws Exception {
        String searchKeyword = pick != null
                ? pick.searchKeyword
                : keywordFromProductUrl(catalog.products.get(RANDOM.nextInt(catalog.products.size())));
        String listingCategoryUrl = pick != null
                ? pick.categoryUrl
                : catalog.categories.get(RANDOM.nextInt(catalog.categories.size()));

        driver.get("https://mcfeelys.com/");
        waitForPageToLoad(driver);
        Thread.sleep(2000);
        dismissPopupIfExists(driver);

        WebElement search1 = driver.findElement(By.id("search"));
        search1.clear();
        search1.sendKeys(searchKeyword);
        search1.sendKeys(Keys.ENTER);
        waitForPageToLoad(driver);
        Thread.sleep(5000);
        takeScreenshot(driver, "product_search_valid");

        WebElement search2 = driver.findElement(By.id("search"));
        search2.clear();
        search2.sendKeys("zzznomatch999");
        search2.sendKeys(Keys.ENTER);
        waitForPageToLoad(driver);
        Thread.sleep(5000);
        takeScreenshot(driver, "product_search_invalid");

        driver.get(listingCategoryUrl);
        waitForPageToLoad(driver);
        Thread.sleep(2000);

        WebElement sorterEl = null;
        try {
            WebDriverWait sortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
            sorterEl = sortWait.until(ExpectedConditions.presenceOfElementLocated(By.id("sorter")));
        } catch (TimeoutException e) {
            System.out.println("No #sorter on this page (not a listing or different layout); skipping price sort.");
        }

        if (sorterEl != null) {
            try {
                if (sorterEl.isDisplayed()) {
                    Select sorter = new Select(sorterEl);
                    sorter.selectByValue("price");
                    Thread.sleep(3000);
                    takeScreenshot(driver, "product_filter_by_price");
                } else {
                    System.out.println("Sort control (#sorter) not visible; skipping price sort.");
                    takeScreenshot(driver, "product_filter_skipped_no_sorter");
                }
            } catch (Exception e) {
                System.out.println("Could not apply price sort: " + e.getMessage());
                takeScreenshot(driver, "product_filter_sort_failed");
            }
        } else {
            takeScreenshot(driver, "product_filter_skipped_no_sorter");
        }

        safeClick(driver, By.xpath("//a[@class=\"action showcart\"]"));
        Thread.sleep(500);


        safeClick(driver, By.xpath("//span[normalize-space()=\"View Cart\"]"));
        Thread.sleep(500);


    }

    // checkout
    public static void checkout(WebDriver driver) throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        // Wait for the checkout button to be present
        WebElement checkoutBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("button[data-role='proceed-to-checkout']")));

        // Scroll into view
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", checkoutBtn);

        // Wait until clickable (visible + enabled)
        WebElement finalCheckoutBtn = checkoutBtn;
        checkoutBtn = wait.until(driver1 -> {
            if (finalCheckoutBtn.isDisplayed() && finalCheckoutBtn.isEnabled()) {
                return finalCheckoutBtn;
            } else {
                return null;
            }
        });

        // Click using JS to bypass any overlay issues
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", checkoutBtn);

        // Wait for checkout page to load
        waitForPageToLoad(driver);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".checkout-header")));
        Thread.sleep(2000); // allow animations to finish

        // Fill checkout form
        driver.findElement(By.name("street[0]")).clear();
        driver.findElement(By.name("street[0]")).sendKeys("California");

        driver.findElement(By.name("city")).clear();
        driver.findElement(By.name("city")).sendKeys("California");

        driver.findElement(By.name("company")).clear();
        driver.findElement(By.name("company")).sendKeys("Exinent Test");

        driver.findElement(By.name("region_id")).sendKeys("California");

        driver.findElement(By.name("postcode")).clear();
        driver.findElement(By.name("postcode")).sendKeys("90001");

        driver.findElement(By.name("telephone")).clear();
        driver.findElement(By.name("telephone")).sendKeys("9870999521");


        Thread.sleep(3000);
        takeScreenshot(driver, "checkout_form");
    }


    // ------------------ Screenshot + Upload + CSV + HTML ------------------
    private static void takeScreenshot(WebDriver driver, String title) throws IOException {
        takeScreenshot(driver, title, true, "Step completed successfully");
    }

    private static void takeScreenshot(WebDriver driver, String title, boolean isPass, String details) throws IOException {
        totalSteps++;
        if (isPass) passedSteps++;
        else failedSteps++;

        // Use a short timestamp for file name
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String statusPrefix = isPass ? "SUCCESS_" : "ERROR_";
        String fileName = statusPrefix + title + "_" + timestamp + ".png";

        // Folder to store screenshots (Organized by Date and Time)
        File folder = new File(SS_DIR);
        if (!folder.exists()) folder.mkdirs();

        // Save screenshot locally
        File outputFile = new File(folder, fileName);
        File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Files.copy(src.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());

        // Upload screenshot to Imgbb
        String uploadedUrl = "Upload failed/skipped";
        try {
            uploadedUrl = uploadToImgbb(outputFile);
            System.out.println("Uploaded URL: " + uploadedUrl);
        } catch (Exception e) {
            System.out.println("Could not upload to Imgbb: " + e.getMessage());
        }

        // Write to CSV (append mode)
        writeCsv(timestamp, title, uploadedUrl, outputFile.getName());
        
        // Write to HTML report
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
        String csvFile = CSV_PATH;
        File fileObj = new File(csvFile);
        if (!fileObj.getParentFile().exists()) {
            fileObj.getParentFile().mkdirs();
        }
        boolean fileExists = fileObj.exists();

        try (FileWriter fw = new FileWriter(csvFile, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            // If CSV does not exist, write header first
            if (!fileExists) {
                out.println("Timestamp,Title,LocalFile,UploadedURL");
            }

            // Append new row
            out.println(timestamp + "," + title + "," + localFileName + "," + url);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeHtmlReport(String timestamp, String title, String localFileName, String url, boolean isPass, String details) {
        File htmlFolder = new File(HTML_DIR);
        if (!htmlFolder.exists()) htmlFolder.mkdirs();

        String htmlFile = HTML_DIR + "\\TestReport.html";
        
        // Relative path from HTML file to the Screenshot image
        String relativeImgPath = "../../../screenshots/" + RUN_DATE + "/" + RUN_TIME + "/" + localFileName;

        // Build the HTML for the current step and add it to our static list
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

        // Overwrite file entirely on every step to update the total counts at the top and include ALL steps
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
            out.println("    <title>McFeels E-commerce Automation - Enhanced Test Report</title>");
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
            out.println("            <h1>🛒 McFeels E-commerce Automation</h1>");
            out.println("            <p style=\"font-size: 1.2em; margin: 10px 0;\">Enhanced Test Report with Detailed Steps</p>");
            out.println("            <div class=\"timestamp\">Generated on: " + RUN_DATE + " at " + RUN_TIME.replace("-", ":") + "</div>");
            out.println("        </div>");
            
            // Dynamic Summary Cards
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

            // Append ALL Step details dynamically from our static list
            for (String step : htmlSteps) {
                out.println(step);
            }

            out.println("        </div>"); // Closing test-results
            
            // Footer
            out.println("        <div style=\"margin-top: 50px; padding-top: 20px; border-top: 1px solid #dee2e6; text-align: center; color: #6c757d;\">");
            out.println("            <p>🤖 Generated by McFeels E-commerce Automation Framework v2.0</p>");
            out.println("            <p>Dynamic testing with random categories and products for comprehensive coverage</p>");
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
