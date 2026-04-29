import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allergy Control (Magento) storefront automation: Chrome + sitemap, cart/checkout, Klaviyo dismiss, reports.
 * <p>
 * JVM: {@code -DallergyControl.site.base}, {@code -DallergyControl.sitemap.url}, optional {@code -Dwebdriver.chrome.driver}.
 * Optional customer login (dev / test stores with captcha bypass): {@code -DallergyControl.login.enabled=true},
 * {@code -DallergyControl.login.username}, {@code -DallergyControl.login.password}.
 * Chrome Password Manager breach popups: disabled via launch prefs; optional Enter fallback after login:
 * {@code -DallergyControl.chrome.dismissBreachDialogEnter=false} to disable.
 * Args: optional leading {@code http(s)://} store URL; optional numeric seed for shuffling product order.
 */
public class AllergyControl {

    /* ===================== 1. OUTPUT — edit paths here ===================== */
    static String RUN_DATE = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    static String RUN_TIME = new SimpleDateFormat("HH-mm-ss").format(new Date());
    static String START_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    static String OUTPUT_ROOT = "C:\\Users\\deepa\\Documents\\Automation\\AllergyControl";
    static String SS_DIR = OUTPUT_ROOT + "\\screenshots\\" + RUN_DATE + "\\" + RUN_TIME;
    static String HTML_DIR = OUTPUT_ROOT + "\\html\\" + RUN_DATE + "\\" + RUN_TIME;
    static String CSV_PATH = OUTPUT_ROOT + "\\AllergyControl.csv";
    static int totalSteps = 0;
    static int passedSteps = 0;
    static int failedSteps = 0;
    static List<String> htmlSteps = new ArrayList<>();

    /* ===================== 2. SITE URL, SITEMAP, CART LIMITS ===================== */
    static final String LOG_PREFIX = "[AllergyControl]";
    static final String PROP_SITE_BASE = "allergyControl.site.base";
    static final String PROP_SITEMAP_URL = "allergyControl.sitemap.url";
    static final String DEFAULT_SITE_BASE = "https://allergycontrol-dev.99stockpics.com/";
    static String siteBase = DEFAULT_SITE_BASE;
    static String sitemapUrl = siteBase + "/sitemap.xml";
    static final int MAX_SITEMAP_PRODUCT_TRIES = 50;
    static final Duration ADD_TO_CART_STOCK_WAIT = Duration.ofSeconds(28);
    static final int MIN_PRODUCTS_BEFORE_CHECKOUT = 3;
    static final int MAX_PRODUCTS_BEFORE_CHECKOUT = 4;
    static final int MIN_PRODUCTS_AFTER_LOGIN = 2;
    static final int MAX_PRODUCTS_AFTER_LOGIN = 3;

    static final String PROP_LOGIN_ENABLED = "allergyControl.login.enabled";
    static final String PROP_LOGIN_USERNAME = "allergyControl.login.username";
    static final String PROP_LOGIN_PASSWORD = "allergyControl.login.password";
    /** Dev-only token written into Magento’s hidden g-recaptcha-response when the store bypasses server validation. */
    static final String LOGIN_RECAPTCHA_RESPONSE_PLACEHOLDER = "dassfsd";

    private static final By SIGN_IN_HEADER_LINK = By.xpath("//a[normalize-space()='Sign In']");
    private static final By LOGIN_FORM = By.id("login-form");
    private static final By LOGIN_EMAIL = By.id("email");
    private static final By LOGIN_PASSWORD = By.id("pass");
    private static final By LOGIN_SUBMIT = By.id("send2");

    private static final Pattern SITEMAP_LOC_PATTERN =
            Pattern.compile("<loc>\\s*([^<]+?)\\s*</loc>", Pattern.CASE_INSENSITIVE);
    private static final By SHIPPING_NEW_ADDRESS_FORM = By.id("shipping-new-address-form");
    /** Knockout checkout often uses {@code name="ko_unique_*"} instead of {@code shipping_method}. */
    private static final By SHIPPING_RATE_TABLE_RADIOS =
            By.cssSelector("#checkout-shipping-method-load table.table-checkout-shipping-method input[type='radio']");
    private static final By SHIPPING_METHOD_LOAD_ANY_RADIO =
            By.cssSelector("#checkout-shipping-method-load input[type='radio']");
    private static final By SHIPPING_NEXT_SPAN = By.xpath("//div[@id='checkout-step-shipping']//span[normalize-space()='Next']");
    private static final By SHIPPING_NEXT_IN_TOOLBAR =
            By.xpath("//div[@id='shipping-method-buttons-container']//span[normalize-space()='Next']");

    /**
     * Program entry: opens Chrome, loads store, discovers products from sitemap, runs cart/checkout scenario, quits browser.
     *
     * @param args optional storefront URL as first arg; remaining args → {@link #shuffleRngFromArgs}
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        String[] runArgs = configureSiteFromProgramArgs(args);
        WebDriver driver = createChromeDriver();
        try {
            driver.manage().window().maximize();
            System.out.println(LOG_PREFIX + " Chrome should be visible. Loading storefront: " + siteBase + "/");
            driver.get(siteBase + "/");
            waitForPageFullyLoaded(driver);

            if (isCustomerLoginEnabled()) {
                System.out.println(LOG_PREFIX + " Customer login enabled — signing in…");
                performCustomerLogin(driver, new WebDriverWait(driver, Duration.ofSeconds(25)));
                waitForPageFullyLoaded(driver);
                takeScreenshot(driver, "after_customer_login");
            }

            System.out.println(LOG_PREFIX + " Downloading & parsing sitemap.xml (often 30–120 s, ~1 MB). "
                    + "The browser may sit on the homepage until this finishes — this is normal.");
            List<String> productUrls = discoverProductUrlsFromSitemap();
            if (productUrls.isEmpty()) {
                throw new IllegalStateException("No product URLs matched filters from " + sitemapUrl);
            }
            System.out.println(LOG_PREFIX + " Sitemap ready: " + productUrls.size() + " product URLs. Capturing homepage screenshot…");

            takeScreenshot(driver, "homepage");
            runEndToEnd(driver, runArgs, productUrls);
        } finally {
            driver.quit();
        }
    }

    /**
     * Reads site URL from JVM properties and/or first program arg; sets {@link #siteBase} and {@link #sitemapUrl}.
     *
     * @return args with leading URL stripped (for seed parsing)
     */
    private static String[] configureSiteFromProgramArgs(String[] args) {
        String base = normalizeSiteBase(System.getProperty(PROP_SITE_BASE, DEFAULT_SITE_BASE));
        int skip = 0;
        if (args != null && args.length > 0 && args[0] != null && looksLikeHttpUrl(args[0])) {
            base = normalizeSiteBase(args[0]);
            skip = 1;
        }
        siteBase = base;
        String sitemapProp = System.getProperty(PROP_SITEMAP_URL);
        if (sitemapProp != null && !sitemapProp.isBlank()) {
            sitemapUrl = sitemapProp.trim();
        } else {
            sitemapUrl = siteBase + "/sitemap.xml";
        }
        System.out.println(LOG_PREFIX + " siteBase=" + siteBase);
        System.out.println(LOG_PREFIX + " sitemapUrl=" + sitemapUrl);
        if (args == null) {
            return new String[0];
        }
        return skip == 0 ? args : Arrays.copyOfRange(args, skip, args.length);
    }

    /** Trims and removes trailing slashes; empty → {@link #DEFAULT_SITE_BASE}. */
    private static String normalizeSiteBase(String s) {
        if (s == null) {
            return DEFAULT_SITE_BASE;
        }
        s = s.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? DEFAULT_SITE_BASE : s;
    }

    /** True if string starts with http:// or https:// (case-insensitive). */
    private static boolean looksLikeHttpUrl(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        return t.regionMatches(true, 0, "http://", 0, 7) || t.regionMatches(true, 0, "https://", 0, 8);
    }

    /* ===================== 3. BROWSER HELPERS ===================== */

    /** Launches Chrome; respects {@code -Dwebdriver.chrome.driver} or WebDriverManager. */
    private static WebDriver createChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized", "--window-position=0,0",
                "--disable-search-engine-choice-screen", "--remote-allow-origins=*");
        /* Chrome Password Manager "Change your password" / breach UI is native — disable prompts for automation. */
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);
        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--disable-features=PasswordCheck");
        String manualDriver = System.getProperty("webdriver.chrome.driver");
        if (manualDriver != null && !manualDriver.isBlank()) {
            System.out.println(LOG_PREFIX + " Using chromedriver from -Dwebdriver.chrome.driver=" + manualDriver);
        } else {
            System.out.println(LOG_PREFIX + " Resolving ChromeDriver (WebDriverManager; first run may download driver)…");
            System.out.flush();
            WebDriverManager.chromedriver().setup();
        }
        System.out.flush();
        System.out.println(LOG_PREFIX + " Launching Chrome window…");
        System.out.flush();
        return new ChromeDriver(options);
    }

    /** Normal click with JS fallback (overlays / stale intercepts). */
    private static void safeClick(WebDriver driver, WebElement element) {
        try {
            element.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    /** Waits for element to be clickable, then {@link #safeClick(WebDriver, WebElement)}. */
    private static void safeClick(WebDriver driver, WebDriverWait wait, By locator) {
        safeClick(driver, wait.until(ExpectedConditions.elementToBeClickable(locator)));
    }

    /** Sets value via JS + events (Magento/Knockout read-only fields). */
    private static void fillTextInput(WebDriver driver, WebElement element, String text) {
        ((JavascriptExecutor) driver).executeScript(
                "var e=arguments[0], t=arguments[1];"
                        + "e.removeAttribute('readonly'); e.removeAttribute('disabled');"
                        + "e.focus(); e.value=t;"
                        + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "e.dispatchEvent(new Event('change',{bubbles:true}));"
                        + "e.dispatchEvent(new Event('blur',{bubbles:true}));",
                element, text);
    }

    /** Returns the input/textarea current value via JavaScript. */
    private static String inputValue(WebDriver driver, WebElement input) {
        Object v = ((JavascriptExecutor) driver).executeScript("return arguments[0].value;", input);
        return v != null ? String.valueOf(v) : "";
    }

    /** Closes Klaviyo email modal if present (Allergy Control). */
    private static void dismissKlaviyoSignupIfPresent(WebDriver driver) {
        By[] dismissSelectors = new By[]{
                By.cssSelector("button.klaviyo-close-form"),
                By.cssSelector("button[aria-label='Close dialog']"),
                By.xpath("//form[contains(@class,'klaviyo-form')]//button[contains(normalize-space(),'No, thanks')]"),
        };
        for (int attempt = 0; attempt < 14; attempt++) {
            for (By by : dismissSelectors) {
                for (WebElement el : driver.findElements(by)) {
                    try {
                        if (el.isDisplayed()) {
                            safeClick(driver, el);
                            try {
                                Thread.sleep(400);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return;
                        }
                    } catch (StaleElementReferenceException | ElementNotInteractableException ignored) {
                    }
                }
            }
            if (attempt >= 5 && driver.findElements(By.cssSelector("form.klaviyo-form")).isEmpty()) {
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Document complete + jQuery idle + Klaviyo dismiss. */
    private static void waitForPageFullyLoaded(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(d -> "complete".equals(
                ((JavascriptExecutor) d).executeScript("return document.readyState")));
        wait.until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                "return typeof jQuery === 'undefined' || jQuery.active === 0")));
        dismissKlaviyoSignupIfPresent(driver);
    }

    /** {@code -DallergyControl.login.enabled=true} to sign in after the homepage loads (before sitemap/cart flow). */
    private static boolean isCustomerLoginEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROP_LOGIN_ENABLED, "false"));
    }

    private static String customerLoginUsername() {
        String u = System.getProperty(PROP_LOGIN_USERNAME);
        if (u != null && !u.isBlank()) {
            return u.trim();
        }
        return "deepak.maheshwari@exinent.com";
    }

    private static String customerLoginPassword() {
        String p = System.getProperty(PROP_LOGIN_PASSWORD);
        if (p != null && !p.isBlank()) {
            return p;
        }
        return "Admin@123";
    }

    /**
     * Clicks header Sign In, fills Magento {@code #login-form}, runs dev reCAPTCHA bypass JS, submits, waits for leave login page.
     * <p>
     * Requires jQuery on the page. Intended only for environments where the server accepts the bypass (e.g. dev/stage).
     */
    private static void performCustomerLogin(WebDriver driver, WebDriverWait wait) throws IOException, InterruptedException {
        dismissKlaviyoSignupIfPresent(driver);
        safeClick(driver, wait, SIGN_IN_HEADER_LINK);

        WebDriverWait formWait = new WebDriverWait(driver, Duration.ofSeconds(30));
        WebElement emailField = formWait.until(ExpectedConditions.visibilityOfElementLocated(LOGIN_EMAIL));
        formWait.until(ExpectedConditions.visibilityOfElementLocated(LOGIN_FORM));

        safeClick(driver, emailField);
        fillTextInput(driver, emailField, customerLoginUsername());

        WebElement passField = driver.findElement(LOGIN_PASSWORD);
        safeClick(driver, passField);
        fillTextInput(driver, passField, customerLoginPassword());

        bypassLoginRecaptchaViaJs(driver);
        Thread.sleep(400);

        safeClick(driver, wait, LOGIN_SUBMIT);

        WebDriverWait done = new WebDriverWait(driver, Duration.ofSeconds(45));
        try {
            done.until(ExpectedConditions.or(
                    ExpectedConditions.invisibilityOfElementLocated(LOGIN_FORM),
                    ExpectedConditions.not(ExpectedConditions.urlContains("/customer/account/login"))));
        } catch (TimeoutException e) {
            takeScreenshot(driver, "customer_login_failed", false, "Still on login or form visible after submit — check credentials / captcha bypass");
            throw new IllegalStateException("Customer login did not complete within 45 s", e);
        }
        dismissPostLoginPopupIfPresent(driver);
        System.out.println(LOG_PREFIX + " Customer login finished. Current URL: " + driver.getCurrentUrl());
    }

    /** Opens /customer/account/login and signs in using configured credentials. */
    private static void loginFromLoginPage(WebDriver driver) throws IOException, InterruptedException {
        driver.get(siteBase + "/customer/account/login/");
        waitForPageFullyLoaded(driver);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));

        WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(LOGIN_EMAIL));
        wait.until(ExpectedConditions.visibilityOfElementLocated(LOGIN_FORM));
        safeClick(driver, emailField);
        fillTextInput(driver, emailField, customerLoginUsername());

        WebElement passField = driver.findElement(LOGIN_PASSWORD);
        safeClick(driver, passField);
        fillTextInput(driver, passField, customerLoginPassword());

        bypassLoginRecaptchaViaJs(driver);
        Thread.sleep(400);

        safeClick(driver, wait, LOGIN_SUBMIT);
        WebDriverWait done = new WebDriverWait(driver, Duration.ofSeconds(45));
        try {
            done.until(ExpectedConditions.or(
                    ExpectedConditions.invisibilityOfElementLocated(LOGIN_FORM),
                    ExpectedConditions.not(ExpectedConditions.urlContains("/customer/account/login"))));
        } catch (TimeoutException e) {
            takeScreenshot(driver, "customer_login_from_login_page_failed", false,
                    "Still on login page after submit");
            throw new IllegalStateException("Customer login from /customer/account/login did not complete within 45 s", e);
        }
        dismissPostLoginPopupIfPresent(driver);
        System.out.println(LOG_PREFIX + " Customer login (login page) finished. Current URL: " + driver.getCurrentUrl());
    }

    /** Matches your dev bypass: checkbox + hidden textarea before POST (server must accept it). */
    private static void bypassLoginRecaptchaViaJs(WebDriver driver) {
        ((JavascriptExecutor) driver).executeScript(
                "if (typeof jQuery !== 'undefined') {"
                        + " jQuery('[name=\"recaptcha-validate-\"]').prop('checked', true);"
                        + " jQuery('.g-recaptcha-response').val(arguments[0]);"
                        + "}",
                LOGIN_RECAPTCHA_RESPONSE_PLACEHOLDER);
    }

    /** Handles post-login warnings/popups (browser alert or site modal with OK button). */
    private static void dismissPostLoginPopupIfPresent(WebDriver driver) throws InterruptedException {
        try {
            Alert alert = driver.switchTo().alert();
            alert.accept();
            Thread.sleep(300);
            return;
        } catch (NoAlertPresentException ignored) {
        }

        By[] okButtons = new By[]{
                By.cssSelector("button.action-primary.action-accept"),
                By.xpath("//div[contains(@class,'modal-popup') or contains(@class,'ui-dialog')]//button[normalize-space()='OK' or .//span[normalize-space()='OK']]"),
                By.xpath("//button[normalize-space()='OK' or .//span[normalize-space()='OK']]"),
        };
        for (By by : okButtons) {
            for (WebElement btn : driver.findElements(by)) {
                try {
                    if (btn.isDisplayed() && btn.isEnabled()) {
                        safeClick(driver, btn);
                        Thread.sleep(400);
                        return;
                    }
                } catch (StaleElementReferenceException | ElementNotInteractableException ignored) {
                }
            }
        }
        /* Native Chrome Password Manager dialog is not in the DOM; Enter often accepts focused OK. */
        tryDismissChromeNativePasswordBreachDialogWithEnter();
    }

    static final String PROP_CHROME_ENTER_AFTER_LOGIN = "allergyControl.chrome.dismissBreachDialogEnter";

    /**
     * Sends Enter once after a short delay so a focused Chrome "Change your password" / breach OK is accepted.
     * Disabled on headless; disable with {@code -DallergyControl.chrome.dismissBreachDialogEnter=false}.
     */
    private static void tryDismissChromeNativePasswordBreachDialogWithEnter() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        if (!Boolean.parseBoolean(System.getProperty(PROP_CHROME_ENTER_AFTER_LOGIN, "true"))) {
            return;
        }
        try {
            Thread.sleep(1200);
            Robot robot = new Robot();
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
        } catch (Exception ignored) {
        }
    }

    /* ===================== 4. SITEMAP (HTTP + product URL filter) ===================== */

    /** Recursively reads sitemap XML and returns catalog product PDP URLs for {@link #siteBase}. */
    private static List<String> discoverProductUrlsFromSitemap() throws IOException {
        Set<String> leaf = new LinkedHashSet<>();
        collectSitemapLeafUrls(sitemapUrl, 0, leaf);
        List<String> products = new ArrayList<>();
        for (String u : leaf) {
            if (isCatalogProductPage(u)) {
                products.add(u);
            }
        }
        return products;
    }

    /** Parses all {@code <loc>...</loc>} entries from sitemap XML text. */
    private static List<String> extractSitemapLocs(String xml) {
        List<String> out = new ArrayList<>();
        Matcher m = SITEMAP_LOC_PATTERN.matcher(xml);
        while (m.find()) {
            String loc = m.group(1).trim();
            if (!loc.isEmpty()) {
                out.add(loc);
            }
        }
        return out;
    }

    /** GETs URL and returns UTF-8 body; throws on HTTP ≥ 400. */
    private static String fetchHttpText(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(60_000);
        conn.setReadTimeout(120_000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; AllergyControlAutomation/1.0)");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code >= 400) {
            throw new IOException("HTTP " + code + " for " + urlString);
        }
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** If sitemap {@code loc} host differs from {@link #siteBase}, rebuild URL using siteBase’s origin + loc’s path/query. */
    private static String rewriteLocHostToSiteBase(String loc) {
        try {
            URI u = URI.create(loc.trim());
            URI base = URI.create(siteBase);
            if (u.getHost() != null && u.getHost().equalsIgnoreCase(base.getHost())) {
                return loc.trim();
            }
            String path = u.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String q = u.getRawQuery();
            String frag = u.getRawFragment();
            StringBuilder sb = new StringBuilder();
            sb.append(base.getScheme()).append("://").append(base.getHost());
            if (base.getPort() != -1) {
                sb.append(":").append(base.getPort());
            }
            sb.append(path);
            if (q != null) {
                sb.append("?").append(q);
            }
            if (frag != null) {
                sb.append("#").append(frag);
            }
            return sb.toString();
        } catch (Exception e) {
            return loc.trim();
        }
    }

    /** Recursively follows nested {@code .xml} sitemaps; leaf urlsets add rewritten {@code loc}s to {@code leafUrls}. */
    private static void collectSitemapLeafUrls(String sitemapUrlArg, int depth, Set<String> leafUrls) throws IOException {
        if (depth > 12) {
            return;
        }
        String xml = fetchHttpText(sitemapUrlArg);
        List<String> locs = extractSitemapLocs(xml);
        boolean hasChildXml = false;
        for (String loc : locs) {
            if (loc.toLowerCase().endsWith(".xml")) {
                hasChildXml = true;
                break;
            }
        }
        if (hasChildXml) {
            for (String loc : locs) {
                if (loc.toLowerCase().endsWith(".xml")) {
                    collectSitemapLeafUrls(loc, depth + 1, leafUrls);
                }
            }
        } else {
            for (String loc : locs) {
                leafUrls.add(rewriteLocHostToSiteBase(loc));
            }
        }
    }

    /** True for Magento-style product PDPs on {@link #siteBase} host; excludes cart, checkout, category, etc. */
    private static boolean isCatalogProductPage(String url) {
        try {
            URI u = URI.create(url);
            if (u.getHost() == null) {
                return false;
            }
            String siteHost = URI.create(siteBase).getHost();
            if (siteHost != null && !u.getHost().equalsIgnoreCase(siteHost)) {
                return false;
            }
            String path = u.getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) {
                return false;
            }
            String lower = url.toLowerCase();
            int q = lower.indexOf('?');
            String pathLower = q >= 0 ? lower.substring(0, q) : lower;
            if (pathLower.contains("/customer/")
                    || pathLower.contains("/checkout")
                    || pathLower.contains("/cart")
                    || pathLower.contains("/catalogsearch/")
                    || pathLower.contains("/wishlist")) {
                return false;
            }
            if (pathLower.contains("/catalog/category/")) {
                return false;
            }
            if (pathLower.endsWith(".html")) {
                return true;
            }
            return pathLower.contains("/catalog/product/view/");
        } catch (Exception e) {
            return false;
        }
    }

    /* ===================== 5. PRODUCT PAGE & ADD TO CART ===================== */

    /** {@code Random} from first arg if parseable as long; else random seed. */
    private static Random shuffleRngFromArgs(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            try {
                return new Random(Long.parseLong(args[0].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return new Random(ThreadLocalRandom.current().nextLong());
    }

    /**
     * Opens product URL, picks configurable options, waits for salable add-to-cart, clicks add-to-cart, screenshots.
     *
     * @return false if OOS or button stays disabled
     */
    private static boolean visitProductAndAddToCartIfSalable(WebDriver driver, WebDriverWait wait, String candidate, int visitOrdinal)
            throws IOException, InterruptedException {
        driver.get(candidate);
        waitForPageFullyLoaded(driver);
        takeScreenshot(driver, "sitemap_product_visit" + visitOrdinal + "_" + urlSlugForScreenshots(candidate));

        selectConfigurableOptionsIfPresent(driver, wait);

        if (!waitForInStockAddToCartOrGiveUp(driver)) {
            System.out.println("Skipping (out of stock or add to cart unavailable): " + candidate);
            takeScreenshot(driver, "sitemap_product_oos_" + urlSlugForScreenshots(candidate), false,
                    "Out of stock or add to cart stayed disabled — trying next sitemap product");
            return false;
        }

        safeClick(driver, wait, By.xpath("//button[@id=\"product-addtocart-button\"]"));
        System.out.println("Added to cart: " + candidate);
        Thread.sleep(1500);
        takeScreenshot(driver, "after_add_line_" + visitOrdinal + "_" + urlSlugForScreenshots(candidate));
        return true;
    }

    /** Detects Magento OOS UI (stock classes, schema.org outOfStock). */
    private static boolean hasExplicitOutOfStockIndicators(WebDriver driver) {
        By[] selectors = new By[]{
                By.cssSelector(".stock.unavailable"),
                By.cssSelector(".availability.out-of-stock"),
                By.cssSelector(".product-info-stock-sku .stock.unavailable"),
                By.cssSelector("#product-options-wrapper .stock.unavailable"),
                By.cssSelector(".product.alert.stock"),
        };
        for (By by : selectors) {
            for (WebElement el : driver.findElements(by)) {
                try {
                    if (el.isDisplayed()) {
                        return true;
                    }
                } catch (StaleElementReferenceException ignored) {
                }
            }
        }
        for (WebElement el : driver.findElements(By.cssSelector("[itemprop='availability']"))) {
            try {
                if (!el.isDisplayed()) {
                    continue;
                }
                String href = el.getAttribute("href");
                if (href != null && href.toLowerCase().contains("outofstock")) {
                    return true;
                }
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return false;
    }

    /** True if {@code #product-addtocart-button} exists, visible, and not disabled. */
    private static boolean isAddToCartButtonEnabled(WebDriver driver) {
        List<WebElement> buttons = driver.findElements(By.id("product-addtocart-button"));
        if (buttons.isEmpty()) {
            return false;
        }
        WebElement b = buttons.get(0);
        try {
            if (!b.isDisplayed()) {
                return false;
            }
        } catch (StaleElementReferenceException e) {
            return false;
        }
        if (b.getAttribute("disabled") != null) {
            return false;
        }
        if ("true".equalsIgnoreCase(b.getAttribute("aria-disabled"))) {
            return false;
        }
        String cls = b.getAttribute("class");
        return cls == null || !cls.contains("disabled");
    }

    /** Polls until add-to-cart enabled or OOS shown, up to {@link #ADD_TO_CART_STOCK_WAIT}. */
    private static boolean waitForInStockAddToCartOrGiveUp(WebDriver driver) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ADD_TO_CART_STOCK_WAIT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (hasExplicitOutOfStockIndicators(driver)) {
                return false;
            }
            if (isAddToCartButtonEnabled(driver)) {
                return true;
            }
            Thread.sleep(350);
        }
        if (hasExplicitOutOfStockIndicators(driver)) {
            return false;
        }
        return isAddToCartButtonEnabled(driver);
    }

    /** Short filesystem-safe name from product path for screenshot filenames. */
    private static String urlSlugForScreenshots(String productUrl) {
        try {
            String path = URI.create(productUrl).getPath();
            if (path == null || path.isEmpty()) {
                return "product";
            }
            String last = path.substring(path.lastIndexOf('/') + 1);
            if (last.endsWith(".html")) {
                last = last.substring(0, last.length() - 5);
            }
            last = last.replaceAll("[^a-zA-Z0-9_-]+", "_");
            if (last.isEmpty()) {
                return "product";
            }
            return last.length() > 60 ? last.substring(0, 60) : last;
        } catch (Exception e) {
            return "product";
        }
    }

    /** For each visible Magento configurable {@code select}, picks first real option until none left empty. */
    private static void selectConfigurableOptionsIfPresent(WebDriver driver, WebDriverWait wait) throws InterruptedException {
        for (int round = 0; round < 8; round++) {
            List<WebElement> selects = driver.findElements(By.cssSelector(
                    "select[id^='attribute'], select.super-attribute-select, #product-options-wrapper select"));
            int acted = 0;
            for (WebElement selEl : selects) {
                try {
                    if (!selEl.isDisplayed()) {
                        continue;
                    }
                    Select s = new Select(selEl);
                    String current = s.getFirstSelectedOption().getAttribute("value");
                    if (current != null && !current.isEmpty() && !"0".equals(current)) {
                        continue;
                    }
                    for (WebElement o : s.getOptions()) {
                        String v = o.getAttribute("value");
                        if (v != null && !v.isEmpty() && !"0".equals(v)) {
                            s.selectByValue(v);
                            acted++;
                            waitForPageFullyLoaded(driver);
                            Thread.sleep(400);
                            break;
                        }
                    }
                } catch (StaleElementReferenceException | NoSuchElementException ignored) {
                }
            }
            if (acted == 0) {
                break;
            }
        }
    }

    /* ===================== 6. CHECKOUT (Magento one-page) ===================== */

    /** Waits for jQuery.active == 0 and checkout loading masks to disappear. */
    private static void waitForCheckoutSpinnersGone(WebDriver driver) {
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(60));
        w.until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                "return typeof jQuery === 'undefined' || jQuery.active === 0")));
        w.until(d -> {
            List<WebElement> spinners = d.findElements(By.cssSelector(
                    ".loading-mask, .opc-block-shipping-information .loading-mask, "
                            + "#checkout-step-shipping .loading-mask, .field._field-loading .spinner"));
            for (WebElement s : spinners) {
                try {
                    if (s.isDisplayed()) {
                        return false;
                    }
                } catch (StaleElementReferenceException ignored) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * After shipping: waits until payment UI is actually usable (not a single compound CSS locator — that can resolve
     * to a hidden {@code #checkout-step-payment} shell and time out while the real methods live in
     * {@code #checkout-payment-method-load}).
     */
    private static void waitForPaymentMethodsLoaded(WebDriver driver) throws InterruptedException {
        if (isPaymentStepLikelyOpen(driver)) {
            Thread.sleep(500);
            return;
        }
        try {
            waitForCheckoutSpinnersGone(driver);
        } catch (TimeoutException e) {
            System.out.println(LOG_PREFIX + " Initial checkout spinner wait timed out; checking payment UI directly.");
        }
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(20));
        try {
            w.until(AllergyControl::isPaymentStepLikelyOpen);
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "Payment step did not become ready quickly. URL=" + driver.getCurrentUrl()
                            + " — check shipping errors on page, saved-address flow, or theme checkout markup.",
                    e);
        }
        WebDriverWait payMaskWait = new WebDriverWait(driver, Duration.ofSeconds(20));
        try {
            payMaskWait.until(d -> {
                List<WebElement> masks = d.findElements(By.cssSelector(
                        "#checkout-step-payment .loading-mask, #checkout-payment-method-load .loading-mask, "
                                + ".opc-payment .loading-mask, .checkout-payment-method .loading-mask, "
                                + "#payment .loading-mask, .payment-methods .loading-mask"));
                for (WebElement m : masks) {
                    try {
                        if (m.isDisplayed()) {
                            return false;
                        }
                    } catch (StaleElementReferenceException ex) {
                        return false;
                    }
                }
                return true;
            });
        } catch (TimeoutException e) {
            if (!isPaymentStepLikelyOpen(driver)) {
                throw e;
            }
            System.out.println(LOG_PREFIX + " Payment masks still visible but methods are ready; continuing.");
        }
        try {
            waitForCheckoutSpinnersGone(driver);
        } catch (TimeoutException e) {
            if (!isPaymentStepLikelyOpen(driver)) {
                throw e;
            }
            System.out.println(LOG_PREFIX + " Checkout loader persisted after payment load; continuing.");
        }
        Thread.sleep(800);
    }

    private static boolean isPaymentStepLikelyOpen(WebDriver driver) {
        String current = "";
        try {
            current = String.valueOf(driver.getCurrentUrl()).toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
        }
        if (current.contains("#payment")) {
            return true;
        }
        try {
            Object active = ((JavascriptExecutor) driver).executeScript(
                    "var step=document.querySelector('#checkout-step-payment');"
                            + "if(!step){return false;}"
                            + "var c=(step.className||'');"
                            + "if(c.indexOf('_active')>=0){return true;}"
                            + "return !!step.querySelector('#checkout-payment-method-load, .payment-method, .payment-methods');");
            if (Boolean.TRUE.equals(active)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return isCheckoutPaymentUiReady(driver);
    }

    /** True when at least one payment method control is visible (Luma, many custom themes, some Hyvä hybrids). */
    private static boolean isCheckoutPaymentUiReady(WebDriver d) {
        List<WebElement> methodRadios = d.findElements(By.cssSelector("input[name='payment[method]']"));
        for (WebElement r : methodRadios) {
            try {
                if (r.isDisplayed()) {
                    return true;
                }
            } catch (StaleElementReferenceException ignored) {
            }
        }
        By[] containers = new By[]{
                By.cssSelector("#checkout-payment-method-load"),
                By.cssSelector("#checkout-step-payment._active"),
                By.cssSelector("li#checkout-step-payment._active"),
                By.cssSelector(".checkout-payment-method._active"),
                By.cssSelector("[data-bind*='checkout-payment'] .payment-method"),
                By.cssSelector(".payment-methods"),
                By.cssSelector(".payment-method-title"),
        };
        for (By by : containers) {
            for (WebElement el : d.findElements(by)) {
                try {
                    if (el.isDisplayed()) {
                        return true;
                    }
                } catch (StaleElementReferenceException ignored) {
                }
            }
        }
        for (WebElement el : d.findElements(By.id("checkout-step-payment"))) {
            try {
                if (el.isDisplayed()) {
                    String cls = el.getAttribute("class");
                    if (cls != null && cls.contains("_active")) {
                        return true;
                    }
                    for (WebElement inner : el.findElements(By.cssSelector("#checkout-payment-method-load, .payment-method"))) {
                        if (inner.isDisplayed()) {
                            return true;
                        }
                    }
                }
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return false;
    }

    /** Fills guest shipping address (US, region 12, hard-coded test data) on one-page checkout. */
    private static void fillShippingNewAddressForm(WebDriver driver, WebDriverWait wait) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(SHIPPING_NEW_ADDRESS_FORM));

        WebElement country = shippingFormSelect(driver, wait, "country_id");
        new Select(country).selectByValue("US");
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));", country);
        waitForCheckoutSpinnersGone(driver);

        WebDriverWait regionWait = new WebDriverWait(driver, Duration.ofSeconds(30));
        regionWait.until(d -> {
            try {
                WebElement sel = d.findElement(
                        By.cssSelector("#shipping-new-address-form select[name='region_id']"));
                for (WebElement o : new Select(sel).getOptions()) {
                    if ("12".equals(o.getAttribute("value"))) {
                        return true;
                    }
                }
            } catch (NoSuchElementException | StaleElementReferenceException e) {
                return false;
            }
            return false;
        });
        WebElement region = shippingFormSelect(driver, wait, "region_id");
        new Select(region).selectByValue("12");
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));", region);
        waitForCheckoutSpinnersGone(driver);

        fillShippingInput(driver, wait, "firstname", "deepak");
        fillShippingInput(driver, wait, "lastname", "Maheshwari");
        fillShippingInput(driver, wait, "company", "Exinent");
        fillShippingInput(driver, wait, "street[0]", "123 Main Street");
        fillShippingInput(driver, wait, "city", "Los Angeles");
        fillShippingInput(driver, wait, "postcode", "90001");
        fillShippingInput(driver, wait, "telephone", "9870999521");

        waitForCheckoutSpinnersGone(driver);

        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "firstname"), "deepak");
        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "lastname"), "Maheshwari");
        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "company"), "Exinent");
        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "street[0]"), "123 Main Street");
        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "city"), "Los Angeles");
        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "postcode"), "90001");
        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "telephone"), "9870999521");
    }

    /**
     * Magento usually does not advance from {@code #shipping} until a table-rate (etc.) radio is chosen and the scoped
     * “Continue” is used — a generic “primary” button can hit the wrong step.
     */
    private static void continueShippingStepToPayment(WebDriver driver, WebDriverWait wait) throws InterruptedException {
        waitForShippingRatesAndSelectFirstMethod(driver);
        Thread.sleep(500);
        clickMagentoShippingContinue(driver, wait);
        waitForCheckoutNextTransition(driver);

        WebDriverWait payPeek = new WebDriverWait(driver, Duration.ofSeconds(18));
        try {
            payPeek.until(AllergyControl::isCheckoutPaymentUiReady);
            return;
        } catch (TimeoutException ignored) {
        }
        System.out.println(LOG_PREFIX + " Payment not visible yet — retrying shipping method + Continue once…");
        waitForShippingRatesAndSelectFirstMethod(driver);
        Thread.sleep(600);
        clickMagentoShippingContinue(driver, wait);
        waitForCheckoutNextTransition(driver);
    }

    /**
     * Checkout "Next" is mostly AJAX and can keep jQuery.active > 0 for a long time. Avoid strict
     * full-page waits here and only wait for checkout spinners/DOM to settle.
     */
    private static void waitForCheckoutNextTransition(WebDriver driver) throws InterruptedException {
        try {
            waitForCheckoutSpinnersGone(driver);
        } catch (TimeoutException e) {
            System.out.println(LOG_PREFIX + " INFO: checkout loaders still active after Next; proceeding to payment checks.");
        }
        Thread.sleep(600);
    }

    /** Waits for {@code #checkout-shipping-method-load} quotes, then selects first rate (KO radios {@code ko_unique_*} or legacy {@code shipping_method}). */
    private static void waitForShippingRatesAndSelectFirstMethod(WebDriver driver) {
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(90));
        w.until(AllergyControl::anyShippingMethodRadioReady);
        boolean selected = false;
        for (WebElement r : findShippingRateRadios(driver)) {
            try {
                if (r.isDisplayed() && r.isEnabled() && r.isSelected()) {
                    selected = true;
                    break;
                }
            } catch (StaleElementReferenceException ignored) {
            }
        }
        if (!selected) {
            WebElement pick = firstDisplayedShippingRadio(driver);
            if (pick != null) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", pick);
                waitForCheckoutSpinnersGone(driver);
            }
        }
        waitForCheckoutSpinnersGone(driver);
    }

    private static List<WebElement> findShippingRateRadios(WebDriver driver) {
        List<WebElement> inTable = driver.findElements(SHIPPING_RATE_TABLE_RADIOS);
        if (!inTable.isEmpty()) {
            return inTable;
        }
        List<WebElement> inLoad = driver.findElements(SHIPPING_METHOD_LOAD_ANY_RADIO);
        if (!inLoad.isEmpty()) {
            return inLoad;
        }
        return driver.findElements(By.cssSelector("input[name='shipping_method']"));
    }

    private static WebElement firstDisplayedShippingRadio(WebDriver driver) {
        for (WebElement r : findShippingRateRadios(driver)) {
            try {
                if (r.isDisplayed() && r.isEnabled()) {
                    return r;
                }
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return null;
    }

    private static boolean anyShippingMethodRadioReady(WebDriver d) {
        return firstDisplayedShippingRadio(d) != null;
    }

    /**
     * Magento 2.4+ often shows “Next” on the shipping step; older themes use “Continue” in
     * {@code #shipping-method-buttons-container}.
     */
    private static void clickMagentoShippingContinue(WebDriver driver, WebDriverWait wait) {
        waitForCheckoutSpinnersGone(driver);
        for (By nextBy : new By[]{SHIPPING_NEXT_SPAN, SHIPPING_NEXT_IN_TOOLBAR}) {
            for (WebElement span : driver.findElements(nextBy)) {
                try {
                    if (span.isDisplayed()) {
                        safeClick(driver, span);
                        return;
                    }
                } catch (StaleElementReferenceException | ElementNotInteractableException ignored) {
                }
            }
        }
        By[] locators = new By[]{
                By.cssSelector("#shipping-method-buttons-container button.action.continue"),
                By.cssSelector("#shipping-method-buttons-container button[type='submit']"),
                By.cssSelector("#checkout-step-shipping .actions-toolbar .primary button.action.continue"),
                By.cssSelector("form#co-shipping-form .actions-toolbar button[type='submit']"),
        };
        for (By by : locators) {
            for (WebElement btn : driver.findElements(by)) {
                try {
                    if (btn.isDisplayed() && btn.isEnabled()) {
                        safeClick(driver, btn);
                        return;
                    }
                } catch (StaleElementReferenceException ignored) {
                }
            }
        }
        safeClick(driver, wait, By.xpath(
                "//div[@id='checkout-step-shipping']//button[contains(@class,'continue')][contains(@class,'primary')]"));
    }

    /** Locates {@code input[name]} inside {@link #SHIPPING_NEW_ADDRESS_FORM}. */
    private static WebElement shippingFormInput(WebDriver driver, WebDriverWait wait, String nameAttr) {
        return wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("#shipping-new-address-form input[name='" + nameAttr + "']")));
    }

    /** Locates {@code select[name]} inside shipping new-address form. */
    private static WebElement shippingFormSelect(WebDriver driver, WebDriverWait wait, String nameAttr) {
        return wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("#shipping-new-address-form select[name='" + nameAttr + "']")));
    }

    /** Clicks shipping input and sets value via {@link #fillTextInput}. */
    private static void fillShippingInput(WebDriver driver, WebDriverWait wait, String nameAttr, String value) {
        WebElement el = shippingFormInput(driver, wait, nameAttr);
        safeClick(driver, el);
        fillTextInput(driver, el, value);
    }

    /** Re-fills field if still empty after AJAX (Knockout race). */
    private static void ensureShippingTextFilled(WebDriver driver, WebElement el, String text) {
        if (el == null || text == null) {
            return;
        }
        if (inputValue(driver, el).trim().isEmpty()) {
            fillTextInput(driver, el, text);
        }
    }

    /** Waits for Magento/Algolia search dropdown; on timeout sleeps briefly so screenshot still makes sense. */
    private static void waitForSearchSuggestionsVisibleOrSettle(WebDriver driver) throws InterruptedException {
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(15));
        try {
            w.until(d -> {
                if (!Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                        "return typeof jQuery === 'undefined' || jQuery.active === 0"))) {
                    return false;
                }
                for (WebElement e : d.findElements(By.cssSelector(
                        "#search_autocomplete, .search-autocomplete, ul[role='listbox'], div[role='listbox'], "
                                + ".algolia-autocomplete, .aa-Panel, .aa-dropdown-menu"))) {
                    try {
                        if (e.isDisplayed()) {
                            return true;
                        }
                    } catch (StaleElementReferenceException ignored) {
                    }
                }
                return false;
            });
        } catch (TimeoutException e) {
            Thread.sleep(2500);
        }
        Thread.sleep(500);
    }

    /* ===================== 7. END-TO-END SCENARIO (cart → coupon → checkout → home search) ===================== */

    /**
     * Random in-stock products → minicart → full cart → qty 3 → coupon → checkout email/shipping → payment screenshot
     * → home → search “Allergy” → shopping cart: remove coupon, clear lines, then logout when login was enabled.
     */
    private static void runEndToEnd(WebDriver driver, String[] args, List<String> productUrls) throws IOException, InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        int targetLines = ThreadLocalRandom.current().nextInt(MIN_PRODUCTS_BEFORE_CHECKOUT, MAX_PRODUCTS_BEFORE_CHECKOUT + 1);
        List<Integer> order = new ArrayList<>(productUrls.size());
        for (int i = 0; i < productUrls.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, shuffleRngFromArgs(args));

        Set<String> addedUrls = new LinkedHashSet<>();
        int maxVisits = Math.min(productUrls.size(), MAX_SITEMAP_PRODUCT_TRIES);
        int visitCount = 0;

        System.out.println("Sitemap products found: " + productUrls.size()
                + " — adding " + targetLines + " random distinct in-stock product(s), up to " + maxVisits + " page visits");

        for (int listPos : order) {
            if (addedUrls.size() >= targetLines) {
                break;
            }
            if (visitCount >= maxVisits) {
                break;
            }
            String candidate = productUrls.get(listPos);
            if (addedUrls.contains(candidate)) {
                continue;
            }
            visitCount++;
            if (visitProductAndAddToCartIfSalable(driver, wait, candidate, visitCount)) {
                addedUrls.add(candidate);
            }
        }

        if (addedUrls.size() < targetLines) {
            throw new IllegalStateException("Added only " + addedUrls.size() + " of " + targetLines
                    + " distinct products after " + visitCount + " sitemap visits (cap " + maxVisits + ")");
        }

        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
        Thread.sleep(2000);
        takeScreenshot(driver, "after_add_to_cart_top");

        safeClick(driver, wait, By.xpath("//a[@class=\"action showcart\"]"));
        takeScreenshot(driver, "minicart_open");

        safeClick(driver, wait, By.xpath("//span[normalize-space()=\"View and Edit Cart\"]"));
        waitForPageFullyLoaded(driver);
        takeScreenshot(driver, "cart_page");

        WebElement qtyInput = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//th//span[normalize-space()='Qty']/ancestor::table//tbody//td[contains(@class,'qty')]//input")));
        safeClick(driver, qtyInput);
        qtyInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "3");

        safeClick(driver, wait, By.xpath("//span[normalize-space()=\"Update Shopping Cart\"]"));
        waitForPageFullyLoaded(driver);
        takeScreenshot(driver, "cart_page_after_qty_change");

        WebElement couponInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("coupon_code")));
        safeClick(driver, couponInput);
        couponInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "exitest");

        safeClick(driver, wait, By.xpath("//span[normalize-space()=\"Apply Discount\"]"));
        waitForPageFullyLoaded(driver);

        takeScreenshot(driver, "cart_after_apply_discount");

        safeClick(driver, wait, By.xpath("//span[normalize-space()=\"Proceed to Checkout\"]"));
        waitForPageFullyLoaded(driver);
        waitForCheckoutSpinnersGone(driver);
        takeScreenshot(driver, "checkout_page");

        WebElement customerEmail = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//input[@id=\"customer-email\"]")));
        safeClick(driver, customerEmail);
        fillTextInput(driver, customerEmail, "deepak.maheshwari@exinent.com");
        waitForCheckoutSpinnersGone(driver);

        fillShippingNewAddressForm(driver, wait);
        takeScreenshot(driver, "checkout_after_shipping_address");

        waitForCheckoutSpinnersGone(driver);
        continueShippingStepToPayment(driver, wait);
        try {
            waitForPaymentMethodsLoaded(driver);
        } catch (RuntimeException e) {
            boolean paymentVisible = isCheckoutPaymentUiReady(driver)
                    || driver.getCurrentUrl().contains("#payment")
                    || driver.getCurrentUrl().contains("/checkout");
            if (!paymentVisible) {
                throw e;
            }
            System.out.println(LOG_PREFIX + " INFO: payment appears visible; continuing despite loader timeout: " + e.getMessage());
        }
        takeScreenshot(driver, "payment_page");
        takeScreenshot(driver, "checkout_after_shipping_continue");

        runPostPaymentLoginSearchAndCheckout(driver, args, productUrls);

        cleanupCartRemoveCouponClearLinesAndLogout(driver);
    }

    /**
     * After first payment-page validation: go home, open login page, sign in, run search check,
     * add 2–3 random products, and verify navigation till checkout page.
     */
    private static void runPostPaymentLoginSearchAndCheckout(WebDriver driver, String[] args, List<String> productUrls)
            throws IOException, InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(18));

        driver.get(siteBase + "/");
        waitForPageFullyLoaded(driver);
        takeScreenshot(driver, "home_before_login_after_payment");

        loginFromLoginPage(driver);
        waitForPageFullyLoaded(driver);
        takeScreenshot(driver, "after_customer_login_post_payment");

        WebElement searchInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("search")));
        safeClick(driver, searchInput);
        searchInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Allergy");
        waitForSearchSuggestionsVisibleOrSettle(driver);
        takeScreenshot(driver, "search_after_login");

        int targetLines = ThreadLocalRandom.current().nextInt(MIN_PRODUCTS_AFTER_LOGIN, MAX_PRODUCTS_AFTER_LOGIN + 1);
        List<Integer> order = new ArrayList<>(productUrls.size());
        for (int i = 0; i < productUrls.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, shuffleRngFromArgs(args));

        Set<String> addedUrls = new LinkedHashSet<>();
        int maxVisits = Math.min(productUrls.size(), MAX_SITEMAP_PRODUCT_TRIES);
        int visitCount = 0;
        for (int listPos : order) {
            if (addedUrls.size() >= targetLines || visitCount >= maxVisits) {
                break;
            }
            String candidate = productUrls.get(listPos);
            if (addedUrls.contains(candidate)) {
                continue;
            }
            visitCount++;
            if (visitProductAndAddToCartIfSalable(driver, wait, candidate, visitCount)) {
                addedUrls.add(candidate);
            }
        }
        if (addedUrls.size() < targetLines) {
            throw new IllegalStateException("Post-login flow added only " + addedUrls.size() + " of " + targetLines
                    + " products after " + visitCount + " visits.");
        }

        safeClick(driver, wait, By.xpath("//a[@class=\"action showcart\"]"));
        safeClick(driver, wait, By.xpath("//span[normalize-space()=\"View and Edit Cart\"]"));
        waitForPageFullyLoaded(driver);
        takeScreenshot(driver, "cart_after_login_products");

        safeClick(driver, wait, By.xpath("//span[normalize-space()=\"Proceed to Checkout\"]"));
        waitForPageFullyLoaded(driver);
        waitForCheckoutSpinnersGone(driver);
        takeScreenshot(driver, "checkout_page_after_login_products");
    }

    /**
     * Opens the cart page, cancels an applied coupon if shown, clears cart in one click, then signs the customer out
     * when {@link #isCustomerLoginEnabled()} (same flag as the pre-run login).
     */
    private static void cleanupCartRemoveCouponClearLinesAndLogout(WebDriver driver)
            throws IOException, InterruptedException {
        System.out.println(LOG_PREFIX + " Cart cleanup (coupon + empty cart)" + (isCustomerLoginEnabled() ? " then logout" : "") + "…");
        driver.get(siteBase + "/checkout/cart/");
        waitForPageFullyLoaded(driver);
        dismissKlaviyoSignupIfPresent(driver);
        takeScreenshot(driver, "cart_cleanup_start");

        removeAppliedCouponFromCartIfPresent(driver);
        removeAllCartLineItems(driver);

        waitForPageFullyLoaded(driver);
        takeScreenshot(driver, "cart_after_cleanup");

        if (isCustomerLoginEnabled()) {
            driver.get(siteBase + "/customer/account/logout/");
            waitForPageFullyLoaded(driver);
            dismissKlaviyoSignupIfPresent(driver);
            takeScreenshot(driver, "after_customer_logout");
            System.out.println(LOG_PREFIX + " Customer logged out. URL: " + driver.getCurrentUrl());
        }
    }

    /** Magento 2: “Remove” on applied coupon (Luma / common themes). */
    private static void removeAppliedCouponFromCartIfPresent(WebDriver driver) throws InterruptedException {
        By[] removeCoupon = new By[]{
                By.cssSelector("[data-role='remove-coupon-link']"),
                By.cssSelector("#discount-coupon-form a.action.cancel"),
                By.cssSelector("form#discount-coupon-form a.action.cancel"),
                By.xpath("//div[contains(@class,'applied-coupon')]//a[contains(@class,'remove')]"),
        };
        for (By by : removeCoupon) {
            for (WebElement el : driver.findElements(by)) {
                try {
                    if (el.isDisplayed()) {
                        safeClick(driver, el);
                        waitForPageFullyLoaded(driver);
                        Thread.sleep(500);
                        return;
                    }
                } catch (StaleElementReferenceException | ElementNotInteractableException ignored) {
                }
            }
        }
    }

    /** Clicks "Clear Shopping Cart" once and confirms the modal OK if prompted. */
    private static void removeAllCartLineItems(WebDriver driver) throws InterruptedException {
        By emptyHint = By.xpath("//*[contains(.,'have no items in your shopping cart')"
                + " or contains(.,'You have no items in your shopping cart')]");
        if (!driver.findElements(emptyHint).isEmpty()) {
            return;
        }

        By clearCart = By.xpath("//span[normalize-space()='Clear Shopping Cart']");
        WebElement clearBtn = null;
        for (WebElement el : driver.findElements(clearCart)) {
            try {
                if (el.isDisplayed()) {
                    clearBtn = el;
                    break;
                }
            } catch (StaleElementReferenceException ignored) {
            }
        }
        if (clearBtn == null) {
            System.out.println(LOG_PREFIX + " Clear Shopping Cart button not found; skipping cart clear.");
            return;
        }

        safeClick(driver, clearBtn);
        confirmCartRemoveOkIfPrompted(driver);
        try {
            waitForPageFullyLoaded(driver);
        } catch (TimeoutException ignored) {
            // Cart clear is AJAX/modal-driven; document-ready may stay noisy.
        }
        WebDriverWait emptyWait = new WebDriverWait(driver, Duration.ofSeconds(30));
        try {
            emptyWait.until(d -> {
                for (WebElement h : d.findElements(emptyHint)) {
                    try {
                        if (h.isDisplayed()) {
                            return true;
                        }
                    } catch (StaleElementReferenceException ignored2) {
                    }
                }
                return false;
            });
        } catch (TimeoutException ignored) {
            System.out.println(LOG_PREFIX + " Clear cart click done, but empty-cart message did not appear in 30s.");
        }
    }

    /** Handles the cart "Are you sure?" confirmation modal by pressing OK/accept. */
    private static void confirmCartRemoveOkIfPrompted(WebDriver driver) throws InterruptedException {
        try {
            Alert alert = driver.switchTo().alert();
            alert.accept();
            Thread.sleep(250);
            return;
        } catch (NoAlertPresentException ignored) {
        }
        By[] confirmOk = new By[]{
                By.cssSelector("button.action-primary.action-accept"),
                By.xpath("//div[contains(@class,'modal-popup')]//button[normalize-space()='OK' or .//span[normalize-space()='OK']]"),
                By.xpath("//div[contains(@class,'modal-popup')]//button[contains(@class,'primary')]"),
                By.xpath("//button[normalize-space()='OK' or .//span[normalize-space()='OK']]"),
        };
        for (By by : confirmOk) {
            for (WebElement btn : driver.findElements(by)) {
                try {
                    if (btn.isDisplayed() && btn.isEnabled()) {
                        safeClick(driver, btn);
                        Thread.sleep(350);
                        return;
                    }
                } catch (StaleElementReferenceException | ElementNotInteractableException ignored) {
                }
            }
        }
    }

    /* ===================== 8. REPORTS: screenshot, CSV, HTML ===================== */

    /** Records a passing step screenshot + CSV + HTML. */
    private static void takeScreenshot(WebDriver driver, String title) throws IOException {
        takeScreenshot(driver, title, true, "Step completed successfully");
    }

    /** Saves PNG locally, appends CSV row, rebuilds HTML report; updates pass/fail counters. */
    private static void takeScreenshot(WebDriver driver, String title, boolean isPass, String details) throws IOException {
        totalSteps++;
        if (isPass) {
            passedSteps++;
        } else {
            failedSteps++;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String statusPrefix = isPass ? "SUCCESS_" : "ERROR_";
        String fileName = statusPrefix + title + "_" + timestamp + ".png";

        File folder = new File(SS_DIR);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File outputFile = new File(folder, fileName);
        File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Files.copy(src.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());

        writeCsv(timestamp, title, outputFile.getName());
        writeHtmlReport(timestamp, title, outputFile.getName(), isPass, details);
    }

    /** Appends one line to {@link #CSV_PATH} (creates header row on first write). */
    private static void writeCsv(String timestamp, String title, String localFileName) {
        File fileObj = new File(CSV_PATH);
        if (fileObj.getParentFile() != null && !fileObj.getParentFile().exists()) {
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

    /** Pushes step HTML into {@link #htmlSteps} and rewrites {@link #HTML_DIR} TestReport.html with full summary. */
    private static void writeHtmlReport(String timestamp, String title, String localFileName, boolean isPass, String details) {
        File htmlFolder = new File(HTML_DIR);
        if (!htmlFolder.exists()) {
            htmlFolder.mkdirs();
        }

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
        stepHtml.append("                    <a class=\"btn\" href=\"").append(relativeImgPath).append("\" target=\"_blank\">View screenshot</a>\n");
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
            out.println("    <title>AllergyControl Automation - Test Report</title>");
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
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("    <div class=\"container\">");
            out.println("        <div class=\"header\">");
            out.println("            <h1>AllergyControl Automation</h1>");
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
            out.println("            <p style=\"color: #666; margin-bottom: 30px;\">Step-by-step execution details with screenshots</p>");
            for (String step : htmlSteps) {
                out.println(step);
            }
            out.println("        </div>");
            out.println("        <div style=\"margin-top: 50px; padding-top: 20px; border-top: 1px solid #dee2e6; text-align: center; color: #6c757d;\">");
            out.println("            <p>Generated by AllergyControl Automation Framework</p>");
            out.println("        </div>");
            out.println("    </div>");
            out.println("</body>");
            out.println("</html>");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
