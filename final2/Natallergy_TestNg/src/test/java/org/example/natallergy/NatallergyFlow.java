package org.example.natallergy;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <p><b>Human summary:</b> this class is the “movie script” — one long {@link #run} method that you read from top to
 * bottom. It assumes {@code productUrls} is already filled (that happens in {@link NatallergyTest} before the test).</p>
 *
 * <p><b>Phases inside {@code run}:</b> (A) fill cart from random sitemap PDPs → (B) open cart (shared mini-cart / URL
 * helper), qty + coupon → (C) guest checkout through shipping until payment loads — <b>skipped when</b>
 * {@link NatallergyConfig#isCheckoutShippingStepEnabled()} is {@code false} → (D) optional
 * {@link NatallergyPageActions#emptyShoppingCartBestEffort(WebDriver)} then login + dashboard shot
 * → (E) home search.</p>
 */
public final class NatallergyFlow {

    /**
     * Executes the full shopper journey. Comments inside are grouped by phase so you can skim the file quickly.
     */
    public static void run(WebDriver driver, String[] args, List<String> productUrls) throws IOException, InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        // ---------- Phase A: pick random products from the list and add enough distinct in-stock lines to cart ----------
        int targetLines = ThreadLocalRandom.current().nextInt(
                NatallergyConfig.MIN_PRODUCTS_BEFORE_CHECKOUT,
                NatallergyConfig.MAX_PRODUCTS_BEFORE_CHECKOUT + 1);
        List<Integer> order = new ArrayList<>(productUrls.size());
        for (int i = 0; i < productUrls.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, shuffleRngFromArgs(args));

        Set<String> addedUrls = new LinkedHashSet<>();
        int maxVisits = Math.min(productUrls.size(), NatallergyConfig.MAX_SITEMAP_PRODUCT_TRIES);
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

        phaseOpenCartQtyCouponAndScreenshots(driver, wait);

        // ---------- Phase C: guest checkout through shipping (optional — off = no shipping step / no checkout HTML chunk) ----------
        if (NatallergyConfig.isCheckoutShippingStepEnabled()) {
            NatallergyPageActions.safeClick(driver, wait, By.xpath("//span[normalize-space()=\"Proceed to Checkout\"]"));
            NatallergyPageActions.waitForPageFullyLoaded(driver);
            NatallergyPageActions.waitForCheckoutSpinnersGone(driver);

            WebElement customerEmail = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//input[@id=\"customer-email\"]")));
            NatallergyPageActions.safeClick(driver, customerEmail);
            NatallergyPageActions.fillTextInput(driver, customerEmail, "deepak.maheshwari@exinent.com");
            NatallergyPageActions.waitForCheckoutSpinnersGone(driver);

            NatallergyPageActions.fillShippingNewAddressForm(driver, wait);

            By continueCheckout = By.xpath("//button[@class=\"button action continue primary\"]");
            NatallergyPageActions.waitForCheckoutSpinnersGone(driver);
            NatallergyPageActions.safeClick(driver, wait, continueCheckout);
            NatallergyPageActions.waitForPageFullyLoaded(driver);
            NatallergyPageActions.waitForPaymentMethodsLoaded(driver);
            NatallergyReporting.takeScreenshot(driver, "checkout_after_shipping_continue", true,
                    "Checkout — payment step after shipping", driver.getCurrentUrl());
        } else {
            System.out.println("[Natallergy] Checkout/shipping phase skipped (natallergy.checkout.shipping.enabled=false).");
            // Stay on cart from Phase B — home is opened once in Phase D when login is off (avoids duplicate GET /).
        }

        // ---------- Phase D: optional clear-all on cart, then login + dashboard; else one trip home for search ----------
        if (NatallergyConfig.isLoginEnabled()) {
            NatallergyPageActions.warnIfLoginHostDiffersFromStorefront();
            System.out.println("[Natallergy] Empty cart (UI clear + fallback), then customer login…");
            NatallergyPageActions.emptyShoppingCartBestEffort(driver);
            NatallergyReporting.takeScreenshot(driver, "cart_after_clear_before_login", true,
                    "Cart after clear-all (before login)", driver.getCurrentUrl());
            NatallergyPageActions.performCustomerLogin(driver);
        } else {
            driver.get(NatallergyConfig.SITE_BASE + "/");
            NatallergyPageActions.waitForPageFullyLoaded(driver);
        }

        // ---------- Phase E: simple smoke on home search (autocomplete / results) ----------
        WebElement searchInput = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//input[@id=\"search\"]")));
        NatallergyPageActions.safeClick(driver, searchInput);
        searchInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Allergy");
        NatallergyPageActions.waitForSearchSuggestionsVisibleOrSettle(driver);
        NatallergyReporting.takeScreenshot(driver, "search_input", true, "Homepage search", driver.getCurrentUrl());
    }

    /** Phase B only: one path to the cart page, quantity update, coupon — uses {@link NatallergyPageActions} (no duplicate minicart XPath here). */
    private static void phaseOpenCartQtyCouponAndScreenshots(WebDriver driver, WebDriverWait wait)
            throws IOException, InterruptedException {
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
        Thread.sleep(2000);
        NatallergyReporting.takeScreenshot(driver, "after_add_to_cart_top", true, "Cart area after PDP adds",
                driver.getCurrentUrl());

        NatallergyPageActions.openShoppingCartPageFromMiniCart(driver);
        NatallergyReporting.takeScreenshot(driver, "cart_page", true, "Shopping cart", driver.getCurrentUrl());

        WebElement qtyInput = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//th//span[normalize-space()='Qty']/ancestor::table//tbody//td[contains(@class,'qty')]//input")));
        NatallergyPageActions.safeClick(driver, qtyInput);
        qtyInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "3");

        NatallergyPageActions.safeClick(driver, wait, By.xpath("//span[normalize-space()=\"Update Shopping Cart\"]"));
        NatallergyPageActions.waitForPageFullyLoaded(driver);
        NatallergyReporting.takeScreenshot(driver, "cart_page_after_qty_change", true, "Cart after quantity update",
                driver.getCurrentUrl());

        WebElement couponInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("coupon_code")));
        NatallergyPageActions.safeClick(driver, couponInput);
        couponInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), NatallergyConfig.CART_COUPON_CODE);

        NatallergyPageActions.safeClick(driver, wait, By.xpath("//span[normalize-space()=\"Apply Discount\"]"));
        NatallergyPageActions.waitForPageFullyLoaded(driver);

        NatallergyReporting.takeScreenshot(driver, "cart_after_apply_discount", true, "Cart after coupon",
                driver.getCurrentUrl());
    }

    private static Random shuffleRngFromArgs(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            try {
                return new Random(Long.parseLong(args[0].trim()));
            } catch (NumberFormatException ignored) {
                // use random seed below
            }
        }
        return new Random(ThreadLocalRandom.current().nextLong());
    }

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
                    // continue
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
                // continue
            }
        }
        return false;
    }

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

    private static boolean waitForInStockAddToCartOrGiveUp(WebDriver driver) throws InterruptedException {
        long deadline = System.currentTimeMillis() + NatallergyConfig.ADD_TO_CART_STOCK_WAIT.toMillis();
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
                            NatallergyPageActions.waitForPageFullyLoaded(driver);
                            Thread.sleep(400);
                            break;
                        }
                    }
                } catch (StaleElementReferenceException | NoSuchElementException ignored) {
                    // next round refreshes the list
                }
            }
            if (acted == 0) {
                break;
            }
        }
    }

    /**
     * Walks {@code productUrls} in list order (up to {@code maxTries}) and adds the first in-stock salable PDP to the
     * cart. Used by focused cart tests so they do not duplicate PDP logic.
     *
     * @return {@code true} if at least one product was added
     */
    public static boolean addFirstSalableProductFromUrls(WebDriver driver, List<String> productUrls, int maxTries)
            throws IOException, InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        int cap = Math.min(maxTries, productUrls.size());
        cap = Math.min(cap, NatallergyConfig.MAX_SITEMAP_PRODUCT_TRIES);
        for (int i = 0; i < cap; i++) {
            if (visitProductAndAddToCartIfSalable(driver, wait, productUrls.get(i), i + 1)) {
                return true;
            }
        }
        return false;
    }

    /** Opens one PDP, handles options/stock, clicks Add to cart when possible. {@code candidate} is the full product URL. */
    private static boolean visitProductAndAddToCartIfSalable(WebDriver driver, WebDriverWait wait, String candidate,
            int visitOrdinal) throws IOException, InterruptedException {
        driver.get(candidate);
        NatallergyPageActions.waitForPageFullyLoaded(driver);
        NatallergyReporting.takeScreenshot(driver, "sitemap_product_visit" + visitOrdinal + "_" + urlSlugForScreenshots(candidate),
                true, "Product page (before options / add to cart)", candidate);

        selectConfigurableOptionsIfPresent(driver, wait);

        if (!waitForInStockAddToCartOrGiveUp(driver)) {
            System.out.println("Skipping (out of stock or add to cart unavailable): " + candidate);
            NatallergyReporting.takeScreenshot(driver, "sitemap_product_oos_" + urlSlugForScreenshots(candidate), false,
                    "Out of stock or add to cart stayed disabled — trying next sitemap product", candidate);
            return false;
        }

        NatallergyPageActions.safeClick(driver, wait, By.xpath("//button[@id=\"product-addtocart-button\"]"));
        System.out.println("Added to cart: " + candidate);
        Thread.sleep(1500);
        NatallergyReporting.takeScreenshot(driver, "after_add_line_" + visitOrdinal + "_" + urlSlugForScreenshots(candidate),
                true, "Product page after add to cart", candidate);
        return true;
    }

    private NatallergyFlow() {
    }
}
