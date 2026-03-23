package org.example;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.time.Duration;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class Oldcheavytruck {



    static String IMGBB_API_KEY = "3b23b07a37fbcee41d4984d100162a10";
    static String RUN_DATE = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    static String RUN_TIME = new SimpleDateFormat("HH-mm-ss").format(new Date());
    static int totalSteps = 0;
    static int passedSteps = 0;
    static int failedSteps = 0;
    static String START_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    static List<String> htmlSteps = new ArrayList<>();

    static String SS_DIR = "C:\\Users\\deepa\\Documents\\Automation\\Oldcheavytruck\\screenshots\\" + RUN_DATE + "\\" + RUN_TIME;
    static String HTML_DIR = "C:\\Users\\deepa\\Documents\\Automation\\Oldcheavytruck\\html\\" + RUN_DATE + "\\" + RUN_TIME;
    static String CSV_PATH = "C:\\Users\\deepa\\Documents\\Automation\\Oldcheavytruck\\Oldcheavytruck.csv";

    /**
     * Full-page captures are huge as PNG. JPEG + downscale keeps reports and ImgBB uploads reasonable.
     * Set {@code false} for lossless PNG; set {@code SCREENSHOT_MAX_WIDTH} to {@code 0} to skip scaling.
     */
    static boolean SCREENSHOT_SAVE_AS_JPEG = true;
    /** 0.75–0.85 typical; lower = smaller files. */
    static float SCREENSHOT_JPEG_QUALITY = 0.78f;
    /** Max width in pixels before resize (height scales). {@code 0} = original width. */
    static int SCREENSHOT_MAX_WIDTH = 1600;

    /** [Jim Carter Truck Parts / Old Chevy Trucks](https://oldchevytrucks.com/) — Magento storefront. */
    static final String OLDCHEVY_BASE = "https://oldchevytrucks.com";

    /** Magento catalog sitemap — used to pick a PDP URL instead of hardcoded category + first product. */
    static final String OLDCHEVY_SITEMAP = OLDCHEVY_BASE + "/sitemap.xml";

    /**
     * Root-level product URLs in this store use SKU-style keys ({@code de101.html}, {@code dp112.html}, …).
     * Year hubs and CMS pages do not match (e.g. {@code 1947-1955.html}, {@code sale.html}).
     */
    private static final Pattern SITEMAP_ROOT_PRODUCT_URL = Pattern.compile(
            "^" + Pattern.quote(OLDCHEVY_BASE) + "/[a-zA-Z]{1,4}\\d+[a-zA-Z0-9-]*\\.html$",
            Pattern.CASE_INSENSITIVE);

    /** Year-range hub + PLP used for category screenshots and fallback multi-add from listing. */
    static final String CATEGORY_YEAR_HUB_PATH = "/1947-1955-truck-parts";
    static final String CATEGORY_PLP_PATH = "/1947-1955/lighting.html";

    private static final By PRODUCT_GRID_LINKS = By.cssSelector(".products .product-item a.product-item-link");

    private static final By ADD_TO_CART_BUTTON = By.id("product-addtocart-button");

    /** How long to wait for a PDP to expose Add to Cart before treating URL as bad (404, discontinued, etc.). */
    private static final Duration PDP_ADD_TO_CART_PROBE = Duration.ofSeconds(8);

    private static final By CART_COUPON_INPUT = By.xpath("//input[@id='coupon_code']");

    private static final By[] CART_COUPON_INPUT_FALLBACKS = new By[] {
            By.cssSelector("#discount-coupon-form input[name='coupon_code']"),
            By.cssSelector("form#discount-coupon-form input.input-text"),
            By.name("coupon_code")
    };

    /** Store promo / test coupon (Magento cart “Apply Discount”). Empty skips the apply step. */
    static String DISCOUNT_COUPON_CODE = "exitest";

    static String CHECKOUT_EMAIL = "deepak.maheshwari@exinent.com";
    static String SHIP_FIRSTNAME = "Deepak";
    static String SHIP_LASTNAME = "Maheshwari";
    static String SHIP_COMPANY = "Exinent LLC";
    static String SHIP_STREET = "123 Main Street";
    static String SHIP_CITY = "Independence";
    /** Magento {@code region_id} for Missouri (common default US directory); override if your catalog differs. */
    static String SHIP_REGION_ID = "36";
    static String SHIP_POSTCODE = "64055";
    static String SHIP_PHONE = "8168331913";

    /**
     * Full storefront journey (homepage screenshot through final cart). Call from TestNG or {@link #main}.
     */
    public static void runEndToEndFlow(WebDriver driver) throws IOException, InterruptedException {
        driver.get(OLDCHEVY_BASE);
        waitForPageFullyLoaded(driver);
        dismissCookieBannerIfPresent(driver);
        takeScreenshot(driver, "01_homepage");
        runOldChevyTrucksShoppingFlow(driver);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            runEndToEndFlow(driver);
        } finally {
            driver.quit();
        }
    }

    /** Best-effort GDPR / cookie banners common on Magento. */
    private static void dismissCookieBannerIfPresent(WebDriver driver) {
        WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(4));
        By[] candidates = new By[] {
                By.id("btn-cookie-allow"),
                By.cssSelector(".amgdprcookie-modal-container button.action-accept"),
                By.cssSelector("button#btn-cookie-allow"),
                By.xpath("//button[contains(.,'Accept') or contains(.,'Allow All')]")
        };
        for (By locator : candidates) {
            try {
                WebElement el = shortWait.until(ExpectedConditions.elementToBeClickable(locator));
                el.click();
                waitForPageFullyLoaded(driver);
                return;
            } catch (TimeoutException | StaleElementReferenceException ignored) {
                // try next
            }
        }
    }

    /**
     * If a cart remove confirmation is open (native prompt or Magento modal), dismiss with Cancel / Close
     * so products stay in the cart. No-op when nothing is shown.
     */
    private static void dismissRemoveModalWithCancelIfPresent(WebDriver driver) {
        try {
            Alert alert = driver.switchTo().alert();
            alert.dismiss();
            waitForPageFullyLoaded(driver);
            return;
        } catch (NoAlertPresentException ignored) {
            try {
                driver.switchTo().defaultContent();
            } catch (Exception ignored2) {
                // ignore
            }
        }
        List<WebElement> modalRoots = driver.findElements(
                By.cssSelector(".modal-popup._show, aside.modal-popup._show"));
        for (WebElement modalRoot : modalRoots) {
            try {
                if (!modalRoot.isDisplayed()) {
                    continue;
                }
                WebElement content;
                try {
                    content = modalRoot.findElement(
                            By.cssSelector(".modal-content[data-role='content'], .modal-content"));
                } catch (NoSuchElementException e) {
                    continue;
                }
                String body = content.getText();
                if (body == null || !body.toLowerCase().contains("remove")) {
                    continue;
                }
                try {
                    WebElement cancel = modalRoot.findElement(
                            By.cssSelector("footer.modal-footer button.action-secondary.action-dismiss, "
                                    + "button.action-secondary.action-dismiss[data-role='action']"));
                    ((JavascriptExecutor) driver).executeScript(
                            "arguments[0].scrollIntoView({block:'center'});", cancel);
                    safeClick(driver, cancel);
                } catch (NoSuchElementException e) {
                    WebElement close = modalRoot.findElement(
                            By.cssSelector("button.action-close[data-role='closeBtn'], .modal-header button.action-close"));
                    safeClick(driver, close);
                }
                waitForPageFullyLoaded(driver);
                return;
            } catch (StaleElementReferenceException | ElementNotInteractableException ignored) {
                // try next root
            }
        }
        try {
            driver.switchTo().defaultContent();
        } catch (Exception ignored) {
            // ignore
        }
    }

    /**
     * Category hub + PLP screenshots → 4–5 PDPs from sitemap (or PLP rows) → cart → checkout → search → cart.
     */
    private static void runOldChevyTrucksShoppingFlow(WebDriver driver) throws IOException, InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
        int step = 2;
        int numProducts = 4 + ThreadLocalRandom.current().nextInt(2);

        navigateCategoryPagesAndScreenshot(driver, wait, step);
        step += 2;

        List<String> sitemapProducts = loadSitemapRootProductUrlsQuietly();
        step = addMultipleProductsToCart(driver, wait, numProducts, sitemapProducts, step);

        driver.get(OLDCHEVY_BASE + "/checkout/cart/");
        waitForPageFullyLoaded(driver);
        dismissRemoveModalWithCancelIfPresent(driver);
        takeScreenshot(driver, stepLabel(step++, "shopping_cart"));

        List<WebElement> qtyInputs = driver.findElements(
                By.cssSelector("#shopping-cart-table input.qty, .cart.table-wrapper input.qty, input[data-role='cart-item-qty']"));
        if (!qtyInputs.isEmpty()) {
            WebElement qtyInput = qtyInputs.get(0);
            safeClick(driver, qtyInput);
            qtyInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "2");
            try {
                safeClick(driver, wait, By.cssSelector("button[name='update_cart_action']"));
            } catch (TimeoutException e) {
                safeClick(driver, wait, By.xpath("//span[normalize-space()='Update Shopping Cart']/ancestor::button"));
            }
            waitForPageFullyLoaded(driver);
            dismissRemoveModalWithCancelIfPresent(driver);
            takeScreenshot(driver, stepLabel(step++, "cart_after_qty_update"));
        }

        applyDiscountOnCartPage(driver, wait);
        dismissRemoveModalWithCancelIfPresent(driver);
        takeScreenshot(driver, stepLabel(step++, "cart_after_discount_attempt"));

        proceedToCheckoutFromCart(driver, wait);
        waitForCheckoutSpinnersGone(driver);

        fillGuestCheckoutEmailAndShipping(driver, wait);
        waitForShippingRatesReady(driver, wait);
        takeScreenshot(driver, stepLabel(step++, "checkout_shipping_step"));

        By continueCheckout = By.xpath(
                "//div[@id='checkout-step-shipping']//button[contains(@class,'continue')][contains(@class,'primary')]"
                        + " | //div[@id='shipping-method-buttons-container']//button[@type='submit']");
        waitForCheckoutSpinnersGone(driver);
        safeClick(driver, wait, continueCheckout);
        waitForPageFullyLoaded(driver);
        waitForPaymentMethodsLoaded(driver);
        takeScreenshot(driver, stepLabel(step++, "checkout_payment_step"));

        driver.get(OLDCHEVY_BASE);
        waitForPageFullyLoaded(driver);
        dismissCookieBannerIfPresent(driver);

        WebElement searchInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("search")));
        safeClick(driver, searchInput);
        searchInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "parklight");
        waitForSearchSuggestionsVisibleOrSettle(driver);
        try {
            safeClick(driver, wait, By.cssSelector("button.action.search"));
        } catch (TimeoutException e) {
            searchInput.sendKeys(Keys.ENTER);
            waitForPageFullyLoaded(driver);
        }
        waitForPageFullyLoaded(driver);
        takeScreenshot(driver, stepLabel(step++, "search_results"));

        driver.get(OLDCHEVY_BASE + "/checkout/cart/");
        waitForPageFullyLoaded(driver);
        dismissRemoveModalWithCancelIfPresent(driver);
        takeScreenshot(driver, stepLabel(step, "cart_final_items_kept"));
    }

    private static String stepLabel(int n, String slug) {
        return String.format("%02d_%s", n, slug);
    }

    /** Year hub + category listing — screenshots only (does not add to cart). */
    private static void navigateCategoryPagesAndScreenshot(WebDriver driver, WebDriverWait wait, int stepStart)
            throws IOException {
        driver.get(OLDCHEVY_BASE + CATEGORY_YEAR_HUB_PATH);
        waitForPageFullyLoaded(driver);
        takeScreenshot(driver, stepLabel(stepStart, "category_year_hub"));

        driver.get(OLDCHEVY_BASE + CATEGORY_PLP_PATH);
        waitForPageFullyLoaded(driver);
        takeScreenshot(driver, stepLabel(stepStart + 1, "category_plp_lighting"));
    }

    private static List<String> loadSitemapRootProductUrlsQuietly() {
        List<String> productUrls = new ArrayList<>();
        try {
            productUrls.addAll(parseSitemapRootProductUrls(fetchSitemapXml(OLDCHEVY_SITEMAP)));
            System.out.println("Sitemap: loaded " + productUrls.size() + " root product URLs");
        } catch (IOException e) {
            System.out.println("Sitemap download failed (will use PLP for products): " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Sitemap parse failed (will use PLP for products): " + e.getMessage());
        }
        return productUrls;
    }

    private static List<String> shuffledCopy(List<String> source) {
        List<String> copy = new ArrayList<>(source);
        Collections.shuffle(copy, new Random());
        return copy;
    }

    /** True when the page has a product Add to Cart control (missing on 404 / CMS pages). */
    private static boolean hasPurchasableProductForm(WebDriver driver) {
        WebDriverWait probe = new WebDriverWait(driver, PDP_ADD_TO_CART_PROBE);
        try {
            probe.until(ExpectedConditions.presenceOfElementLocated(ADD_TO_CART_BUTTON));
            List<WebElement> found = driver.findElements(ADD_TO_CART_BUTTON);
            for (WebElement b : found) {
                try {
                    if (b.isDisplayed()) {
                        return true;
                    }
                } catch (StaleElementReferenceException ignored) {
                    return true;
                }
            }
            return false;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * Adds {@code numProducts} items: walks a shuffled sitemap URL list and skips bad pages (404, removed SKUs),
     * then fills any shortfall from the category PLP (also skips bad PDPs).
     *
     * @return next screenshot step index (after {@code after_all_products_added_to_cart})
     */
    private static int addMultipleProductsToCart(WebDriver driver, WebDriverWait wait, int numProducts,
            List<String> sitemapProducts, int stepStart) throws IOException {
        List<String> queue = shuffledCopy(sitemapProducts);
        int step = stepStart;
        int added = 0;
        int urlIndex = 0;
        while (added < numProducts && urlIndex < queue.size()) {
            String url = queue.get(urlIndex++);
            System.out.println("Trying sitemap PDP (" + added + "/" + numProducts + " added): " + url);
            driver.get(url);
            waitForPageFullyLoaded(driver);
            if (!hasPurchasableProductForm(driver)) {
                System.out.println("Skip — no Add to Cart (404 or non-PDP): " + url);
                continue;
            }
            takeScreenshot(driver, stepLabel(step++, "product_" + String.format("%02d", added + 1) + "_of_" + numProducts + "_pdp"));
            applyConfigurableSelectionsIfNeeded(driver, wait);
            try {
                safeClick(driver, wait, ADD_TO_CART_BUTTON);
            } catch (TimeoutException e) {
                System.out.println("Skip — Add to Cart not clickable: " + url);
                continue;
            }
            waitForAddToCartSettled(driver);
            added++;
        }
        int remaining = numProducts - added;
        int plpFilled = 0;
        if (remaining > 0) {
            System.out.println("Adding " + remaining + " product(s) from category PLP (after " + added + " from sitemap)");
            int[] plp = addProductsFromCategoryListing(driver, wait, remaining, 0, step, numProducts, added);
            step = plp[0];
            plpFilled = plp[1];
        }
        if (added + plpFilled < numProducts) {
            System.out.println("Warning: only " + (added + plpFilled) + " of " + numProducts
                    + " products added (bad URLs, empty PLP, or exhausted listing).");
        }
        takeScreenshot(driver, stepLabel(step, "after_all_products_added_to_cart"));
        return step + 1;
    }

    /**
     * @param alreadyAdded how many line items were already added (for 1-based screenshot labels {@code product_XX_of_total})
     * @return {@code [ nextStep, filledCount ]}
     */
    private static int[] addProductsFromCategoryListing(WebDriver driver, WebDriverWait wait, int count, int gridIndexStart,
            int step, int numProductsTotal, int alreadyAdded) throws IOException {
        String plp = OLDCHEVY_BASE + CATEGORY_PLP_PATH;
        int gridIndex = gridIndexStart;
        int filled = 0;
        int safety = 0;
        final int maxSkips = Math.max(80, count * 25);
        while (filled < count && safety < maxSkips) {
            safety++;
            driver.get(plp);
            waitForPageFullyLoaded(driver);
            try {
                wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(PRODUCT_GRID_LINKS, gridIndex));
            } catch (TimeoutException e) {
                System.out.println("PLP: fewer than " + (gridIndex + 1) + " products; stopping listing fallback");
                break;
            }
            List<WebElement> links = driver.findElements(PRODUCT_GRID_LINKS);
            if (links.size() <= gridIndex) {
                break;
            }
            WebElement link = links.get(gridIndex);
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", link);
            safeClick(driver, link);
            waitForPageFullyLoaded(driver);
            gridIndex++;
            if (!hasPurchasableProductForm(driver)) {
                System.out.println("Skip PLP item — PDP has no Add to Cart (grid index " + (gridIndex - 1) + ")");
                continue;
            }
            int humanIndex = alreadyAdded + filled + 1;
            takeScreenshot(driver, stepLabel(step++, "product_" + String.format("%02d", humanIndex) + "_of_" + numProductsTotal + "_pdp"));
            applyConfigurableSelectionsIfNeeded(driver, wait);
            try {
                safeClick(driver, wait, ADD_TO_CART_BUTTON);
            } catch (TimeoutException e) {
                System.out.println("Skip PLP PDP — Add to Cart not clickable");
                continue;
            }
            waitForAddToCartSettled(driver);
            filled++;
        }
        return new int[] { step, filled };
    }

    private static String fetchSitemapXml(String sitemapUrl) throws IOException {
        URL url = new URL(sitemapUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120_000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; OldChevyTrucksAutomation/1.0)");
        conn.setRequestProperty("Accept", "application/xml,text/xml,*/*;q=0.8");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new IOException("Sitemap HTTP " + code);
        }
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            return buf.toString(StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private static List<String> parseSitemapRootProductUrls(String xml) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<loc>([^<]+)</loc>").matcher(xml);
        while (m.find()) {
            String loc = m.group(1).trim();
            if (loc.contains("/blog/") || loc.contains("parts-catalog")) {
                continue;
            }
            if (SITEMAP_ROOT_PRODUCT_URL.matcher(loc).matches()) {
                out.add(loc);
            }
        }
        return out;
    }

    private static void applyDiscountOnCartPage(WebDriver driver, WebDriverWait wait) {
        if (DISCOUNT_COUPON_CODE == null || DISCOUNT_COUPON_CODE.isBlank()) {
            return;
        }
        dismissRemoveModalWithCancelIfPresent(driver);
        WebDriverWait couponWait = new WebDriverWait(driver, Duration.ofSeconds(35));
        expandCartDiscountSectionIfCollapsed(driver, couponWait);
        forceExpandDiscountBlockViaJs(driver);

        WebElement couponInput = waitUntilCartCouponInputVisible(driver, couponWait);
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", couponInput);
        try {
            safeClick(driver, couponInput);
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].focus();", couponInput);
        }
        fillTextInput(driver, couponInput, DISCOUNT_COUPON_CODE.trim());

        By[] applyCouponButtons = new By[] {
                By.cssSelector("#discount-coupon-form button.action.apply"),
                By.cssSelector("form#discount-coupon-form button[type='submit']"),
                By.cssSelector("#discount-coupon-form .action.apply"),
                By.cssSelector("button.action-apply"),
                By.cssSelector("button.apply.primary"),
                By.xpath("//form[@id='discount-coupon-form']//button[contains(@class,'apply')]"),
                By.xpath("//span[normalize-space()='Apply Discount']/ancestor::button"),
                By.xpath("//button[contains(@class,'apply')][contains(@value,'Discount') or contains(.,'Apply')]")
        };
        boolean clicked = false;
        for (By loc : applyCouponButtons) {
            try {
                WebElement btn = couponWait.until(ExpectedConditions.elementToBeClickable(loc));
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", btn);
                safeClick(driver, btn);
                clicked = true;
                break;
            } catch (TimeoutException | StaleElementReferenceException ignored) {
                // try next selector
            }
        }
        if (!clicked) {
            try {
                safeClick(driver, couponWait, By.cssSelector("form#discount-coupon-form button[type='submit']"));
            } catch (TimeoutException e) {
                System.out.println("Apply Discount button not found — check form#discount-coupon-form on cart page.");
            }
        }
        waitForPageFullyLoaded(driver);
    }

    /** Magento collapsible: coupon lives under {@code #block-discount} — often hidden until expanded. */
    private static void forceExpandDiscountBlockViaJs(WebDriver driver) {
        ((JavascriptExecutor) driver).executeScript(
                "var b=document.getElementById('block-discount');"
                        + "if(b){b.classList.add('active');"
                        + "var c=b.querySelector('.content[data-role=content], .content');"
                        + "if(c){c.style.display='block';c.style.visibility='visible';c.removeAttribute('hidden');"
                        + "c.setAttribute('aria-hidden','false');}}"
                        + "var h=document.getElementById('block-discount-heading');"
                        + "if(h){h.click();}");
        waitForPageFullyLoaded(driver);
    }

    private static WebElement waitUntilCartCouponInputVisible(WebDriver driver, WebDriverWait couponWait) {
        By[] all = new By[CART_COUPON_INPUT_FALLBACKS.length + 1];
        all[0] = CART_COUPON_INPUT;
        System.arraycopy(CART_COUPON_INPUT_FALLBACKS, 0, all, 1, CART_COUPON_INPUT_FALLBACKS.length);
        for (By loc : all) {
            try {
                return couponWait.until(ExpectedConditions.visibilityOfElementLocated(loc));
            } catch (TimeoutException ignored) {
                // try next
            }
        }
        for (By loc : all) {
            try {
                WebElement el = couponWait.until(ExpectedConditions.presenceOfElementLocated(loc));
                ((JavascriptExecutor) driver).executeScript(
                        "var p=arguments[0]; while(p){p.style.display='block';p.style.visibility='visible';"
                                + "p.classList.remove('hidden');p=p.parentElement;}",
                        el);
                if (el.isDisplayed()) {
                    return el;
                }
            } catch (TimeoutException | StaleElementReferenceException ignored) {
                // try next
            }
        }
        throw new TimeoutException("Coupon field not found after expanding discount block (tried id=coupon_code and fallbacks).");
    }

    private static void expandCartDiscountSectionIfCollapsed(WebDriver driver, WebDriverWait couponWait) {
        ((JavascriptExecutor) driver).executeScript(
                "var b=document.getElementById('block-discount'); if(b) b.scrollIntoView({block:'center'});");
        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<WebElement> coupon = driver.findElements(CART_COUPON_INPUT);
        if (coupon.size() == 1) {
            try {
                if (coupon.get(0).isDisplayed()) {
                    return;
                }
            } catch (StaleElementReferenceException ignored) {
                // fall through to expand
            }
        }
        By[] toggleLocators = new By[] {
                By.id("block-discount-heading"),
                By.cssSelector("#block-discount .title"),
                By.cssSelector("#block-discount [data-role='title']"),
                By.cssSelector(".cart-discount .block-title"),
                By.cssSelector(".cart-discount .title"),
                By.cssSelector("[data-role='title'][id*='discount']"),
                By.xpath("//div[contains(@class,'cart-discount')]//*[contains(text(),'Discount Code')]"),
                By.xpath("//strong[contains(text(),'Discount')]"),
                By.xpath("//*[@id='block-discount']//strong[contains(.,'Discount')]")
        };
        for (By loc : toggleLocators) {
            for (WebElement t : driver.findElements(loc)) {
                try {
                    if (t.isDisplayed()) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", t);
                        try {
                            safeClick(driver, t);
                        } catch (Exception e) {
                            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", t);
                        }
                        waitForPageFullyLoaded(driver);
                        return;
                    }
                } catch (Exception ignored) {
                    // next
                }
            }
        }
    }

    private static void proceedToCheckoutFromCart(WebDriver driver, WebDriverWait wait) {
        waitForCheckoutSpinnersGone(driver);
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
        try {
            safeClick(driver, wait, By.cssSelector("button[data-role='proceed-to-checkout'], "
                    + "li.item a.checkout-button, a.action.primary.checkout"));
        } catch (TimeoutException e) {
            safeClick(driver, wait, By.xpath(
                    "//span[normalize-space()='Proceed to Checkout']/ancestor::a | "
                            + "//span[normalize-space()='Proceed to Checkout']/ancestor::button"));
        }
        waitForPageFullyLoaded(driver);
    }

    /**
     * Magento checkout inputs are often read-only until focused or bound with Knockout — {@link WebElement#clear()}
     * can throw {@link InvalidElementStateException}. Sets value via JS and dispatches input/change.
     */
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

    private static void waitForCheckoutSpinnersGone(WebDriver driver) {
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(60));
        w.until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                "return typeof jQuery === 'undefined' || jQuery.active === 0")));
        w.until(d -> {
            List<WebElement> spinners = d.findElements(By.cssSelector(
                    ".loading-mask, #checkout-step-shipping .loading-mask, .field._field-loading .spinner, "
                            + ".opc-block-shipping-information .loading-mask"));
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
     * After shipping address is complete, waits for Magento to finish loading methods (no masks, AJAX idle)
     * and for at least one shipping option to appear before screenshots.
     */
    private static void waitForShippingRatesReady(WebDriver driver, WebDriverWait wait) {
        waitForCheckoutSpinnersGone(driver);
        WebDriverWait rateWait = new WebDriverWait(driver, Duration.ofSeconds(50));
        try {
            rateWait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector("#checkout-shipping-method-load input[type='radio']")),
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector("#checkout-shipping-method-load .table-checkout-shipping-method")),
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector("#checkout-shipping-method-load .row[class*='method']"))));
        } catch (TimeoutException e) {
            // rates may still render; continue with spinner/mask checks below
        }
        rateWait.until(d -> {
            List<WebElement> masks = d.findElements(
                    By.cssSelector("#checkout-shipping-method-load .loading-mask, #shipping-method-buttons-container .loading-mask"));
            for (WebElement m : masks) {
                try {
                    if (m.isDisplayed()) {
                        return false;
                    }
                } catch (StaleElementReferenceException ignored) {
                    return false;
                }
            }
            return true;
        });
        waitForCheckoutSpinnersGone(driver);
        ((JavascriptExecutor) driver).executeScript(
                "var el=document.querySelector('#checkout-shipping-method-load');"
                        + "if(el) el.scrollIntoView({block:'center'});");
        try {
            Thread.sleep(1800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitForPaymentMethodsLoaded(WebDriver driver) throws InterruptedException {
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(45));
        waitForCheckoutSpinnersGone(driver);
        w.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("#checkout-step-payment, #checkout-payment-method-load")));
        WebDriverWait payMaskWait = new WebDriverWait(driver, Duration.ofSeconds(45));
        payMaskWait.until(d -> {
            List<WebElement> masks = d.findElements(By.cssSelector(
                    "#checkout-step-payment .loading-mask, #checkout-payment-method-load .loading-mask, "
                            + ".opc-payment .loading-mask"));
            for (WebElement m : masks) {
                try {
                    if (m.isDisplayed()) {
                        return false;
                    }
                } catch (StaleElementReferenceException e) {
                    return false;
                }
            }
            return true;
        });
        waitForCheckoutSpinnersGone(driver);
        Thread.sleep(2000);
    }

    private static String inputValue(WebDriver driver, WebElement input) {
        Object v = ((JavascriptExecutor) driver).executeScript("return arguments[0].value;", input);
        return v != null ? String.valueOf(v) : "";
    }

    private static void ensureShippingTextFilled(WebDriver driver, WebElement el, String text) {
        if (el == null || text == null) {
            return;
        }
        if (inputValue(driver, el).trim().isEmpty()) {
            fillTextInput(driver, el, text);
        }
    }

    private static final By SHIPPING_NEW_ADDRESS_FORM = By.id("shipping-new-address-form");

    private static WebElement shippingFormInput(WebDriver driver, WebDriverWait wait, String nameAttr) {
        return wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("#shipping-new-address-form input[name='" + nameAttr + "']")));
    }

    private static WebElement shippingFormSelect(WebDriver driver, WebDriverWait wait, String nameAttr) {
        return wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("#shipping-new-address-form select[name='" + nameAttr + "']")));
    }

    private static void fillGuestCheckoutEmailAndShipping(WebDriver driver, WebDriverWait wait) {
        WebElement customerEmail = wait.until(ExpectedConditions.elementToBeClickable(By.id("customer-email")));
        safeClick(driver, customerEmail);
        fillTextInput(driver, customerEmail, CHECKOUT_EMAIL);
        customerEmail.sendKeys(Keys.TAB);
        waitForCheckoutSpinnersGone(driver);

        wait.until(ExpectedConditions.visibilityOfElementLocated(SHIPPING_NEW_ADDRESS_FORM));

        WebElement country = shippingFormSelect(driver, wait, "country_id");
        new Select(country).selectByValue("US");
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));", country);
        waitForCheckoutSpinnersGone(driver);

        WebElement region = shippingFormSelect(driver, wait, "region_id");
        try {
            new Select(region).selectByValue(SHIP_REGION_ID);
        } catch (Exception e) {
            try {
                new Select(region).selectByVisibleText("Missouri");
            } catch (Exception e2) {
                new Select(region).selectByIndex(1);
            }
        }
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));", region);
        waitForCheckoutSpinnersGone(driver);

        fillShippingInput(driver, wait, "firstname", SHIP_FIRSTNAME);
        fillShippingInput(driver, wait, "lastname", SHIP_LASTNAME);
        fillShippingInput(driver, wait, "company", SHIP_COMPANY);
        fillShippingInput(driver, wait, "street[0]", SHIP_STREET);
        fillShippingInput(driver, wait, "city", SHIP_CITY);
        fillShippingInput(driver, wait, "postcode", SHIP_POSTCODE);
        fillShippingInput(driver, wait, "telephone", SHIP_PHONE);

        waitForCheckoutSpinnersGone(driver);

        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "firstname"), SHIP_FIRSTNAME);
        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "lastname"), SHIP_LASTNAME);
        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "company"), SHIP_COMPANY);
        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "street[0]"), SHIP_STREET);
        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "city"), SHIP_CITY);
        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "postcode"), SHIP_POSTCODE);
        ensureShippingTextFilled(driver, shippingFormInput(driver, wait, "telephone"), SHIP_PHONE);

        List<WebElement> shipRadios = driver.findElements(
                By.cssSelector("#checkout-shipping-method-load input[type='radio']"));
        for (WebElement r : shipRadios) {
            try {
                if (r.isDisplayed() && r.isEnabled()) {
                    safeClick(driver, r);
                    waitForCheckoutSpinnersGone(driver);
                    break;
                }
            } catch (Exception ignored) {
                // try next
            }
        }
    }

    private static void fillShippingInput(WebDriver driver, WebDriverWait wait, String nameAttr, String value) {
        WebElement el = shippingFormInput(driver, wait, nameAttr);
        safeClick(driver, el);
        fillTextInput(driver, el, value);
    }

    private static void applyConfigurableSelectionsIfNeeded(WebDriver driver, WebDriverWait wait) {
        List<WebElement> selects = driver.findElements(
                By.cssSelector("#product-options-wrapper select.super-attribute-select, #product-options-wrapper select"));
        for (WebElement sel : selects) {
            try {
                if (!sel.isDisplayed()) {
                    continue;
                }
                Select s = new Select(sel);
                if (s.getOptions().size() > 1) {
                    s.selectByIndex(1);
                    waitForPageFullyLoaded(driver);
                }
            } catch (Exception ignored) {
                // optional options
            }
        }
    }

    private static void waitForAddToCartSettled(WebDriver driver) {
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(20));
        try {
            w.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".message-success")),
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".minicart-wrapper .counter-number"))));
        } catch (TimeoutException ignored) {
            waitForPageFullyLoaded(driver);
        }
    }

    private static void safeClick(WebDriver driver, WebElement element) {
        try {
            element.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    private static void safeClick(WebDriver driver, WebDriverWait wait, By locator) {
        safeClick(driver, wait.until(ExpectedConditions.elementToBeClickable(locator)));
    }

    /**
     * Lets autocomplete / AJAX search results render before capturing. Tries common Magento + Algolia containers;
     * if none appear in time, falls back to a fixed delay.
     */
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
                        // retry other candidates
                    }
                }
                return false;
            });
        } catch (TimeoutException e) {
            Thread.sleep(2500);
        }
        Thread.sleep(500);
    }

    /** Wait until the document is complete and any jQuery AJAX has settled (no-op if jQuery is absent). */
    private static void waitForPageFullyLoaded(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(d -> "complete".equals(
                ((JavascriptExecutor) d).executeScript("return document.readyState")));
        wait.until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                "return typeof jQuery === 'undefined' || jQuery.active === 0")));
    }

    // ------------------ Screenshot + Upload + CSV + HTML ------------------
    private static void takeScreenshot(WebDriver driver, String title) throws IOException {
        takeScreenshot(driver, title, true, "Step completed successfully");
    }

    /**
     * Captures the page (full scroll height on {@link ChromeDriver} via CDP), then saves a smaller file:
     * optional max-width resize + JPEG (see {@link #SCREENSHOT_SAVE_AS_JPEG}).
     */
    private static void takeScreenshot(WebDriver driver, String title, boolean isPass, String details) throws IOException {
        totalSteps++;
        if (isPass) passedSteps++;
        else failedSteps++;

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String statusPrefix = isPass ? "SUCCESS_" : "ERROR_";
        String ext = SCREENSHOT_SAVE_AS_JPEG ? ".jpg" : ".png";
        String fileName = statusPrefix + title + "_" + timestamp + ext;

        File folder = new File(SS_DIR);
        if (!folder.exists()) folder.mkdirs();

        File outputFile = new File(folder, fileName);
        boolean fullPage = false;
        byte[] rawPng;
        if (driver instanceof ChromeDriver) {
            Map<String, Object> params = new HashMap<>();
            params.put("format", "png");
            params.put("captureBeyondViewport", true);
            params.put("fromSurface", true);
            Map<String, Object> result = ((ChromeDriver) driver).executeCdpCommand("Page.captureScreenshot", params);
            String data = (String) result.get("data");
            rawPng = Base64.getDecoder().decode(data);
            fullPage = true;
        } else {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            rawPng = Files.readAllBytes(src.toPath());
        }

        saveProcessedScreenshot(rawPng, outputFile);

        String sizeNote = SCREENSHOT_SAVE_AS_JPEG
                ? " — JPEG q=" + String.format("%.2f", SCREENSHOT_JPEG_QUALITY)
                        + (SCREENSHOT_MAX_WIDTH > 0 ? ", maxW=" + SCREENSHOT_MAX_WIDTH : "")
                : " — PNG";
        String reportDetails = fullPage
                ? details + " — full page" + sizeNote
                : details + " — viewport (non-Chrome)" + sizeNote;
        long kb = outputFile.length() / 1024;
        System.out.println("Screenshot saved: " + outputFile.getAbsolutePath()
                + (fullPage ? " (full page)" : "") + " ~" + kb + " KB");

        String uploadedUrl = "Upload failed/skipped";
        try {
            uploadedUrl = uploadToImgbb(outputFile);
            System.out.println("Uploaded URL: " + uploadedUrl);
        } catch (Exception e) {
            System.out.println("Could not upload to Imgbb: " + e.getMessage());
        }

        writeCsv(timestamp, title, uploadedUrl, outputFile.getName());
        writeHtmlReport(timestamp, title, outputFile.getName(), uploadedUrl, isPass, reportDetails);
    }

    private static void saveProcessedScreenshot(byte[] rawPngBytes, File outputFile) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(rawPngBytes));
        if (image == null) {
            Files.write(outputFile.toPath(), rawPngBytes);
            return;
        }
        BufferedImage processed = maybeScaleDownImage(image, SCREENSHOT_MAX_WIDTH);
        if (SCREENSHOT_SAVE_AS_JPEG) {
            writeJpeg(processed, outputFile, SCREENSHOT_JPEG_QUALITY);
        } else {
            ImageIO.write(processed, "png", outputFile);
        }
    }

    private static BufferedImage maybeScaleDownImage(BufferedImage src, int maxWidth) {
        if (maxWidth <= 0 || src.getWidth() <= maxWidth) {
            return src;
        }
        int w = maxWidth;
        int h = (int) Math.round(src.getHeight() * (w / (double) src.getWidth()));
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return scaled;
    }

    private static void writeJpeg(BufferedImage image, File file, float quality) throws IOException {
        BufferedImage rgb = image.getType() == BufferedImage.TYPE_INT_RGB ? image : copyToRgb(image);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG ImageWriter available");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.min(1f, Math.max(0.05f, quality)));
            }
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static BufferedImage copyToRgb(BufferedImage src) {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
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
            out.println("    <title>Old Chevy Trucks Automation - Test Report</title>");
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
            out.println("            <h1>Old Chevy Trucks (Jim Carter)</h1>");
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
            out.println("            <p>Generated by Old Chevy Trucks automation</p>");
            out.println("        </div>");
            out.println("    </div>");
            out.println("</body>");
            out.println("</html>");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
