package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * All Selenium interactions for the shopping journey: category picking, PDP opens, add to cart, minicart, full cart.
 *
 * <p><b>Typical run (see {@link Main})</b>
 * <ol>
 *   <li>{@link #resolveNonEmptyCategoryUrl} — try sitemap category URLs until the <i>main column</i> shows products</li>
 *   <li>{@link #addDynamicProductsAndCompleteCartFlow} — for each slot: open PDP from category grid first,
 *       then sitemap URLs; {@link #addCurrentProductToCartFromPdp} handles simple vs grouped products</li>
 *   <li>Open minicart → “View shopping cart” → {@link #updateCartQtyAndClickUpdateShoppingCart}</li>
 * </ol>
 *
 * <p><b>Helpers worth knowing</b>
 * <ul>
 *   <li>{@link #tryOpenPurchasableProductPage} — skip bad URLs with failure screenshots, no exception</li>
 *   <li>Category “empty” detection is scoped to {@code #maincontent} so header widgets don’t fake empty state</li>
 * </ul>
 */
public final class StorefrontFlow {

    private StorefrontFlow() {}

    // ====================================================================================
    // Optional one-off navigators (e.g. manual tests) — not used by Main’s default flow
    // ====================================================================================

    /** Open a category URL and screenshot (does not check empty vs full). */
    public static void navigateToUrlAsCategory(WebDriver driver, String url) throws IOException, InterruptedException {
        driver.get(url);
        BrowserWaits.waitForPageFullyLoaded(driver);
        ScreenshotReporter.takeFullPageScreenshot(
                driver, "category_from_sitemap", true, "Category (sitemap): " + url);
    }

    public static void navigateToUrlAsProduct(WebDriver driver, String url) throws IOException, InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        driver.get(url);
        BrowserWaits.waitForPageFullyLoaded(driver);
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(
                    "body.catalog-product-view #maincontent, body.catalog-product-view .product-info-main")));
        } catch (TimeoutException e) {
            ScreenshotReporter.takeFullPageScreenshot(
                    driver,
                    "product_page_not_pdp",
                    false,
                    "Expected catalog product page (body.catalog-product-view) but did not find PDP layout. URL: "
                            + url
                            + " — may be CMS/category. Prefer product sitemap URLs or adjust SitemapService.");
            throw new IllegalStateException(
                    "Not a product detail page (missing body.catalog-product-view): " + url, e);
        }
        ScreenshotReporter.takeFullPageScreenshot(driver, "product_detail_page", true, "Product (sitemap): " + url);
    }

    // ====================================================================================
    // Category: find one URL that actually lists products (skip & error-screenshot true empties)
    // ====================================================================================

    /**
     * Visits shuffled category URLs until one lists products in {@code #maincontent}. True empties get
     * {@code category_empty_error} (failed step). Non-empty gets {@code category_from_sitemap} (pass) with
     * a note that add-to-cart will use listing links first.
     */
    public static String resolveNonEmptyCategoryUrl(WebDriver driver, List<SitemapPage> pages)
            throws IOException, InterruptedException {
        List<String> categories = SitemapService.allCategoryUrlsShuffled(pages);
        if (categories.isEmpty()) {
            throw new IllegalStateException("No category URLs in sitemap");
        }
        Set<String> tried = new HashSet<>();
        for (String cat : categories) {
            if (!tried.add(cat)) {
                continue;
            }
            driver.get(cat);
            BrowserWaits.waitForPageFullyLoaded(driver);
            Thread.sleep(500);
            scrollCategoryPageForLazyLoad(driver);
            waitUntilCategoryListingSettles(driver, Duration.ofSeconds(18));
            if (isCategoryEmpty(driver)) {
                System.out.println("Category has no products (confirmed) — error screenshot, try next: " + cat);
                ScreenshotReporter.takeFullPageScreenshot(
                        driver,
                        "category_empty_error",
                        false,
                        "ERROR: Category has no products (listing empty in main content) — " + cat);
                continue;
            }
            ScreenshotReporter.takeFullPageScreenshot(
                    driver,
                    "category_from_sitemap",
                    true,
                    "SUCCESS: Products found in category — add to cart will open products from this listing — "
                            + cat);
            System.out.println("Using non-empty category: " + cat);
            return cat;
        }
        throw new IllegalStateException("No non-empty category after trying " + tried.size() + " URLs");
    }

    private static void scrollCategoryPageForLazyLoad(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
        try {
            Thread.sleep(700);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        js.executeScript("window.scrollTo(0, 0);");
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Wait until main content shows product tiles/links or an explicit Magento “empty category” message. */
    private static void waitUntilCategoryListingSettles(WebDriver driver, Duration timeout) {
        WebDriverWait w = new WebDriverWait(driver, timeout);
        try {
            w.until(d -> countProductsInCategoryMain(d) > 0 || isExplicitEmptyCategoryInMain(d));
        } catch (TimeoutException e) {
            System.out.println("Warning: category listing wait timed out — classifying from current DOM.");
        }
    }

    /**
     * Product count scoped to category main only (avoids header/minicart false “empty” classes).
     * If this is &gt; 0, the category is not empty.
     */
    private static int countProductsInCategoryMain(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Number n = (Number) js.executeScript(
                "var root = document.querySelector('#maincontent') || document.querySelector('main') "
                        + "|| document.querySelector('.column.main'); "
                        + "if (!root) return 0; "
                        + "var q = root.querySelectorAll('.products .product-item, .product-items .product-item, "
                        + "li.item.product.product-item, .products.list .product-item'); "
                        + "if (q.length > 0) return q.length; "
                        + "return root.querySelectorAll('a.product-item-link[href*=\".html\"]').length;");
        return n != null ? n.intValue() : 0;
    }

    /** Magento empty category message only inside main (not global .empty / unrelated widgets). */
    private static boolean isExplicitEmptyCategoryInMain(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Boolean b = (Boolean) js.executeScript(
                "var root = document.querySelector('#maincontent') || document.querySelector('main') "
                        + "|| document.querySelector('.column.main'); "
                        + "if (!root) return false; "
                        + "var el = root.querySelector('.category-empty, .message.info.empty'); "
                        + "return !!(el && el.offsetParent !== null);");
        return Boolean.TRUE.equals(b);
    }

    /**
     * Empty only when main has no product tiles, no listing PDP links, and an explicit empty message (or no
     * listing at all after wait). Any visible grid link or tile → not empty (avoids false “empty” reports).
     */
    private static boolean isCategoryEmpty(WebDriver driver) {
        if (countProductsInCategoryMain(driver) > 0) {
            return false;
        }
        if (!collectProductLinksFromCategoryListing(driver).isEmpty()) {
            return false;
        }
        if (isExplicitEmptyCategoryInMain(driver)) {
            return true;
        }
        return true;
    }

    /** PDP URLs from the current category grid (same tab), stable order, deduped. */
    private static List<String> collectProductLinksFromCategoryListing(WebDriver driver) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        List<By> linkSelectors = List.of(
                By.cssSelector("#maincontent .product-item a.product-item-link"),
                By.cssSelector("#maincontent .product-item a.product-item-photo"),
                By.cssSelector(".products .product-item a.product-item-link"),
                By.cssSelector(".product-items .product-item a[href*=\".html\"]"));
        for (By by : linkSelectors) {
            for (WebElement a : driver.findElements(by)) {
                try {
                    String href = a.getAttribute("href");
                    if (href == null || href.isBlank()) {
                        continue;
                    }
                    String base = href.split("#")[0].split("\\?")[0];
                    if (base.endsWith(".html")
                            && !base.contains("/checkout/")
                            && !base.contains("/customer/")) {
                        urls.add(base);
                    }
                } catch (StaleElementReferenceException e) {
                    // skip stale
                }
            }
        }
        return new ArrayList<>(urls);
    }

    // ====================================================================================
    // Multi-product add + cart checkout UI (used by Main)
    // ====================================================================================

    /**
     * Adds {@code howMany} products (qty 1 each): first opens PDPs from the {@code categoryUrlUsed} listing
     * (same flow as a shopper), then fills the rest from the sitemap if needed. Then minicart → cart → update
     * qty.
     */
    public static void addDynamicProductsAndCompleteCartFlow(
            WebDriver driver, List<SitemapPage> pages, String categoryUrlUsed, int howMany)
            throws IOException, InterruptedException {
        Set<String> listingAttempted = new HashSet<>();
        int added = 0;

        // Phase A — reuse category page: grab product links from grid, open each PDP by URL
        if (categoryUrlUsed != null) {
            while (added < howMany) {
                driver.get(categoryUrlUsed);
                BrowserWaits.waitForPageFullyLoaded(driver);
                Thread.sleep(400);
                scrollCategoryPageForLazyLoad(driver);
                BrowserWaits.waitOptional(
                        driver,
                        Duration.ofSeconds(10),
                        d -> countProductsInCategoryMain(d) > 0
                                || !collectProductLinksFromCategoryListing(d).isEmpty(),
                        "category listing for add-from-grid");

                String nextPdp = null;
                for (String link : collectProductLinksFromCategoryListing(driver)) {
                    if (!listingAttempted.contains(link)) {
                        nextPdp = link;
                        break;
                    }
                }
                if (nextPdp == null) {
                    break;
                }
                listingAttempted.add(nextPdp);
                if (!tryOpenPurchasableProductPage(driver, nextPdp)) {
                    continue;
                }
                if (added == 0) {
                    ScreenshotReporter.takeFullPageScreenshot(
                            driver,
                            "product_detail_first",
                            true,
                            "First purchasable PDP from category listing: " + nextPdp);
                }
                addCurrentProductToCartFromPdp(driver, 1, added + 1, nextPdp);
                added++;
            }
        }

        // Phase B — still need more lines? pull random product URLs from sitemap (excluding category + already tried)
        Set<String> exclude = new HashSet<>();
        if (categoryUrlUsed != null) {
            exclude.add(categoryUrlUsed);
        }
        exclude.addAll(listingAttempted);
        List<String> sitemapCandidates = SitemapService.allProductUrlsShuffled(pages, exclude);
        for (String productUrl : sitemapCandidates) {
            if (added >= howMany) {
                break;
            }
            if (listingAttempted.contains(productUrl)) {
                continue;
            }
            listingAttempted.add(productUrl);
            if (!tryOpenPurchasableProductPage(driver, productUrl)) {
                continue;
            }
            if (added == 0) {
                ScreenshotReporter.takeFullPageScreenshot(
                        driver,
                        "product_detail_first",
                        true,
                        "First purchasable PDP (sitemap): " + productUrl);
            }
            addCurrentProductToCartFromPdp(driver, 1, added + 1, productUrl);
            added++;
        }

        if (added < howMany) {
            ScreenshotReporter.takeFullPageScreenshot(
                    driver,
                    "multi_add_incomplete",
                    false,
                    "Added only " + added + " of " + howMany + " products (listing + sitemap exhausted)");
            throw new IllegalStateException(
                    "Added only " + added + " of " + howMany + " products; exhausted or skipped candidates");
        }

        clickMinicartAndTakeScreenshot(driver);
        viewShoppingCartFromMinicart(driver);
        updateCartQtyAndClickUpdateShoppingCart(driver, 2);
    }

    // ====================================================================================
    // PDP gate + add to cart (grouped vs simple product)
    // ====================================================================================

    /**
     * {@code driver.get(url)} then verify Magento PDP + enabled Add to Cart.
     *
     * @return {@code false} if not a PDP or button missing/disabled (failure screenshot already taken)
     */
    public static boolean tryOpenPurchasableProductPage(WebDriver driver, String url)
            throws IOException, InterruptedException {
        driver.get(url);
        BrowserWaits.waitForPageFullyLoaded(driver);
        Thread.sleep(400);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("body.catalog-product-view")));
        } catch (TimeoutException e) {
            ScreenshotReporter.takeFullPageScreenshot(
                    driver,
                    "product_skip_not_pdp",
                    false,
                    "Not a catalog product page: " + url);
            return false;
        }
        if (!hasBuyableAddToCartButton(driver)) {
            ScreenshotReporter.takeFullPageScreenshot(
                    driver,
                    "product_skip_no_add_to_cart",
                    false,
                    "No enabled Add to Cart (#product-addtocart-button): " + url);
            return false;
        }
        return true;
    }

    /** Magento simple/configurable PDP primary ATC; must be displayed and enabled (not greyed out of stock). */
    private static boolean hasBuyableAddToCartButton(WebDriver driver) {
        List<WebElement> btns = driver.findElements(By.id("product-addtocart-button"));
        if (btns.isEmpty()) {
            return false;
        }
        WebElement b = btns.get(0);
        try {
            return b.isDisplayed() && b.isEnabled();
        } catch (StaleElementReferenceException e) {
            return false;
        }
    }

    /**
     * Preconditions: already on a PDP that passed {@link #tryOpenPurchasableProductPage}.
     * Sets qty, clicks Add to Cart, waits for success message, then ~6s settle + screenshot.
     */
    private static void addCurrentProductToCartFromPdp(
            WebDriver driver, int quantity, int addSequence, String productUrl)
            throws IOException, InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
        JavascriptExecutor js = (JavascriptExecutor) driver;
        String qtyStr = String.valueOf(quantity);

        // Grouped product: first super_group qty row; otherwise single #qty
        WebElement qtyInput;
        List<WebElement> groupedTables = driver.findElements(By.id("super-product-table"));
        if (!groupedTables.isEmpty()) {
            WebElement table = groupedTables.get(0);
            wait.until(ExpectedConditions.visibilityOf(table));
            js.executeScript("arguments[0].scrollIntoView({block:'center'});", table);
            List<WebElement> inputs = table.findElements(By.cssSelector("input.input-text.qty[name^='super_group']"));
            if (inputs.isEmpty()) {
                ScreenshotReporter.takeFullPageScreenshot(
                        driver, "grouped_qty_inputs_missing", false,
                        "super-product-table has no super_group qty — " + productUrl);
                throw new IllegalStateException("No qty inputs in grouped product table: " + productUrl);
            }
            qtyInput = inputs.get(0);
        } else {
            qtyInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("qty")));
            js.executeScript("arguments[0].scrollIntoView({block:'center'});", qtyInput);
        }

        qtyInput.click();
        qtyInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        qtyInput.sendKeys(qtyStr);
        js.executeScript(
                "var el=arguments[0]; el.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "el.dispatchEvent(new Event('change',{bubbles:true}));",
                qtyInput);
        Thread.sleep(400);

        WebElement addBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("product-addtocart-button")));
        js.executeScript("arguments[0].scrollIntoView({block:'center'});", addBtn);
        Thread.sleep(200);
        try {
            addBtn.click();
        } catch (ElementClickInterceptedException e) {
            js.executeScript("arguments[0].click();", addBtn);
        }

        BrowserWaits.waitForPageFullyLoaded(driver);
        try {
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".message-success")),
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector(".message-notice:not(.message-error)"))));
        } catch (TimeoutException e) {
            ScreenshotReporter.takeFullPageScreenshot(
                    driver, "pdp_add_to_cart_no_message", false,
                    "No success/notice after Add to Cart — " + productUrl);
            throw new IllegalStateException("Add to Cart: no success message — " + productUrl, e);
        }
        if (!driver.findElements(By.cssSelector(".message-error")).isEmpty()) {
            ScreenshotReporter.takeFullPageScreenshot(
                    driver, "pdp_add_to_cart_error", false, "Error after Add to Cart — " + productUrl);
            throw new IllegalStateException("Add to Cart failed (.message-error) — " + productUrl);
        }

        // Let minicart / toast / layout finish before full-page capture (avoids overlapping UI in PNG)
        GreatCellConfig.sleepBeforeAddToCartScreenshot();
        ScreenshotReporter.takeFullPageScreenshot(
                driver,
                "after_add_to_cart_from_pdp",
                true,
                "Add #" + addSequence + ", qty " + qtyStr + " — " + productUrl);
    }

    // ====================================================================================
    // Full cart: minicart → cart page → update line quantity (TestRigor-style labels)
    // ====================================================================================

    /** From open minicart panel, click “View shopping cart” and wait for {@code /checkout/cart}. */
    private static void viewShoppingCartFromMinicart(WebDriver driver)
            throws IOException, InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        By scoped = By.xpath(
                "//*[contains(@class,'block-minicart') or contains(@class,'minicart') "
                        + "or @id='minicart-content-wrapper']"
                        + "//a[contains(normalize-space(.),'View shopping cart') "
                        + "or .//span[contains(normalize-space(),'View shopping cart')]]");
        WebElement viewCartLink;
        try {
            viewCartLink = wait.until(ExpectedConditions.elementToBeClickable(scoped));
        } catch (TimeoutException e) {
            viewCartLink = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[contains(normalize-space(.),'View shopping cart')]")));
        }

        js.executeScript("arguments[0].scrollIntoView({block:'center'});", viewCartLink);
        Thread.sleep(200);
        try {
            viewCartLink.click();
        } catch (ElementClickInterceptedException e) {
            js.executeScript("arguments[0].click();", viewCartLink);
        }

        BrowserWaits.waitForPageFullyLoaded(driver);
        wait.until(d -> {
            String u = d.getCurrentUrl();
            return u != null && u.contains("/checkout/cart");
        });

        ScreenshotReporter.takeFullPageScreenshot(
                driver, "shopping_cart_page", true,
                "Minicart: View shopping cart (multi-product cart)");
    }

    /**
     * TestRigor-style cart: set line {@code Qty}, then click {@code Update Shopping Cart}. Uses label/column
     * {@code Qty} when present, else Magento {@code #shopping-cart-table} qty inputs.
     */
    public static void updateCartQtyAndClickUpdateShoppingCart(WebDriver driver, int newQty)
            throws IOException, InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        wait.until(d -> {
            String u = d.getCurrentUrl();
            return u != null && u.contains("/checkout/cart");
        });
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#shopping-cart-table, form#form-validate, .cart.table-wrapper")));

        WebElement qtyInput = findCartQtyInput(driver, wait);
        js.executeScript("arguments[0].scrollIntoView({block:'center'});", qtyInput);
        Thread.sleep(200);
        wait.until(ExpectedConditions.visibilityOf(qtyInput));
        String qtyStr = String.valueOf(newQty);
        qtyInput.click();
        qtyInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        qtyInput.sendKeys(qtyStr);
        js.executeScript(
                "var el=arguments[0]; el.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "el.dispatchEvent(new Event('change',{bubbles:true}));",
                qtyInput);
        Thread.sleep(300);

        WebElement updateBtn = findUpdateShoppingCartButton(driver);
        js.executeScript("arguments[0].scrollIntoView({block:'center'});", updateBtn);
        Thread.sleep(200);
        try {
            updateBtn.click();
        } catch (ElementClickInterceptedException e) {
            js.executeScript("arguments[0].click();", updateBtn);
        }

        BrowserWaits.waitForPageFullyLoaded(driver);
        Thread.sleep(500);
        ScreenshotReporter.takeFullPageScreenshot(
                driver,
                "shopping_cart_after_qty_update",
                true,
                "TestRigor: Qty → " + qtyStr + ", Update Shopping Cart");
    }

    /** First matching visible qty input on cart page (label “Qty” or Magento table). */
    private static WebElement findCartQtyInput(WebDriver driver, WebDriverWait wait) {
        List<By> candidates = List.of(
                By.xpath(
                        "//label[contains(normalize-space(.),'Qty')]/following::input[contains(@class,'input-text') or @type='number'][1]"),
                By.xpath("//th[contains(normalize-space(.),'Qty')]/following::tbody//input[1]"),
                By.xpath("//span[normalize-space()='Qty']/ancestor::th/following-sibling::td//input"),
                By.cssSelector("#shopping-cart-table tbody tr:first-child input.input-text.qty"),
                By.cssSelector("#shopping-cart-table tbody tr input.input-text.qty"),
                By.cssSelector(".cart.table-wrapper tbody input.input-text.qty"),
                By.cssSelector("form#form-validate input.input-text.qty[name^='cart']"));

        return wait.until(d -> {
            for (By by : candidates) {
                for (WebElement el : d.findElements(by)) {
                    try {
                        if (el.isDisplayed() && el.isEnabled()) {
                            return el;
                        }
                    } catch (StaleElementReferenceException e) {
                        break;
                    }
                }
            }
            return null;
        });
    }

    /** Magento “Update Shopping Cart” submit button (several themes / label variants). */
    private static WebElement findUpdateShoppingCartButton(WebDriver driver) {
        List<By> candidates = List.of(
                By.xpath("//button[contains(normalize-space(.),'Update Shopping Cart')]"),
                By.xpath("//span[contains(normalize-space(.),'Update Shopping Cart')]/ancestor::button[1]"),
                By.cssSelector("button[name='update_cart_action'][value='update_qty']"),
                By.cssSelector("button[name='update_cart_action'].action.update"),
                By.cssSelector("button.action.update[name='update_cart_action']"));

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(8));
        for (By by : candidates) {
            try {
                WebElement el = wait.until(ExpectedConditions.elementToBeClickable(by));
                if (el.isDisplayed()) {
                    return el;
                }
            } catch (TimeoutException ignored) {
                // next
            }
        }
        throw new IllegalStateException("Cart: could not find Update Shopping Cart button");
    }

    /** Header minicart toggle for this theme (rd-navbar + showcart), then settle wait + screenshot. */
    private static void clickMinicartAndTakeScreenshot(WebDriver driver)
            throws IOException, InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        By minicartLabel = By.xpath(
                "//a[contains(@class,'showcart') and contains(@class,'rd-navbar-cart-toggle')]"
                        + "//span[@class='text']");
        WebElement toggle = wait.until(ExpectedConditions.elementToBeClickable(minicartLabel));
        js.executeScript("arguments[0].scrollIntoView({block:'center'});", toggle);
        Thread.sleep(300);
        try {
            toggle.click();
        } catch (ElementClickInterceptedException e) {
            WebElement cartLink = driver.findElement(By.xpath(
                    "//a[contains(@class,'showcart') and contains(@class,'rd-navbar-cart-toggle')]"));
            js.executeScript("arguments[0].click();", cartLink);
        }

        Thread.sleep(700);
        BrowserWaits.waitOptional(
                driver,
                Duration.ofSeconds(5),
                d -> !d.findElements(By.cssSelector(".block-minicart, #minicart-content-wrapper")).isEmpty(),
                "minicart panel");

        GreatCellConfig.sleepBeforeAddToCartScreenshot();
        ScreenshotReporter.takeFullPageScreenshot(
                driver, "minicart_after_add", true,
                "Opened minicart after multi-product adds");
    }
}
