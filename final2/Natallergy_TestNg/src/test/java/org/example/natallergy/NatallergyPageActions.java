package org.example.natallergy;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * <p><b>Building blocks for pages</b> — not a “test” itself. {@link NatallergyFlow} calls these methods while telling
 * the story. Grouping for readers:</p>
 * <ul>
 *   <li><b>Clicks / typing</b> — {@link #safeClick}, {@link #fillTextInput} (Magento checkout fields need JS typing).</li>
 *   <li><b>Waits</b> — {@link #waitForPageFullyLoaded}, {@link #waitForCheckoutSpinnersGone}, {@link #waitForPaymentMethodsLoaded}.</li>
 *   <li><b>Checkout shipping</b> — {@link #fillShippingNewAddressForm} and helpers.</li>
 *   <li><b>Login</b> — {@link #navigateToCustomerLogin}, {@link #submitCustomerLoginForm}, {@link #openLoginPageAndSubmitConfiguredCredentials},
 *       {@link #performCustomerLogout(WebDriver)}, {@link #performCustomerLogout(WebDriver, boolean)}, and {@link #performCustomerLogin}
 *       (dashboard screenshot + return to home).</li>
 *   <li><b>Search</b> — {@link #waitForSearchSuggestionsVisibleOrSettle}.</li>
 *   <li><b>Cart / coupon</b> — {@link #navigateToShoppingCartUrl}, {@link #openShoppingCartPageFromMiniCart},
 *       {@link #clearShoppingCartViaUiButtonAndConfirmOkIfPresent}, {@link #emptyShoppingCartBestEffort}, {@link #countCartLineItems},
 *       {@link #clearShoppingCartAllItems},
 *       {@link #applyDiscountCouponOnCartPage}, {@link #removeAppliedCouponOnCartPageIfPresent}.</li>
 * </ul>
 */
public final class NatallergyPageActions {

    private static final By SHIPPING_NEW_ADDRESS_FORM = By.id("shipping-new-address-form");

    public static void safeClick(WebDriver driver, WebElement element) {
        try {
            element.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    public static void safeClick(WebDriver driver, WebDriverWait wait, By locator) {
        safeClick(driver, wait.until(ExpectedConditions.elementToBeClickable(locator)));
    }

    /**
     * Magento checkout inputs are often read-only until focused or bound with Knockout — {@link WebElement#clear()}
     * can throw {@link org.openqa.selenium.InvalidElementStateException}. Sets value via JS and dispatches input/change.
     */
    public static void fillTextInput(WebDriver driver, WebElement element, String text) {
        ((JavascriptExecutor) driver).executeScript(
                "var e=arguments[0], t=arguments[1];"
                        + "e.removeAttribute('readonly'); e.removeAttribute('disabled');"
                        + "e.focus(); e.value=t;"
                        + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "e.dispatchEvent(new Event('change',{bubbles:true}));"
                        + "e.dispatchEvent(new Event('blur',{bubbles:true}));",
                element, text);
    }

    public static void waitForCheckoutSpinnersGone(WebDriver driver) {
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

    public static void waitForPaymentMethodsLoaded(WebDriver driver) throws InterruptedException {
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
        Thread.sleep(3000);
    }

    public static String inputValue(WebDriver driver, WebElement input) {
        Object v = ((JavascriptExecutor) driver).executeScript("return arguments[0].value;", input);
        return v != null ? String.valueOf(v) : "";
    }

    public static void ensureShippingTextFilled(WebDriver driver, WebElement el, String text) {
        if (el == null || text == null) {
            return;
        }
        if (inputValue(driver, el).trim().isEmpty()) {
            fillTextInput(driver, el, text);
        }
    }

    public static WebElement shippingFormInput(WebDriver driver, WebDriverWait wait, String nameAttr) {
        return wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("#shipping-new-address-form input[name='" + nameAttr + "']")));
    }

    public static WebElement shippingFormSelect(WebDriver driver, WebDriverWait wait, String nameAttr) {
        return wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("#shipping-new-address-form select[name='" + nameAttr + "']")));
    }

    /**
     * Fills {@code #shipping-new-address-form} (Knockout shipping address). Country/region first so AJAX does not
     * clear text fields. Line 1 must be a street address (site blocks PO boxes).
     */
    public static void fillShippingNewAddressForm(WebDriver driver, WebDriverWait wait) {
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

    private static void fillShippingInput(WebDriver driver, WebDriverWait wait, String nameAttr, String value) {
        WebElement el = shippingFormInput(driver, wait, nameAttr);
        safeClick(driver, el);
        fillTextInput(driver, el, value);
    }

    public static void warnIfLoginHostDiffersFromStorefront() {
        try {
            URI loginUri = URI.create(NatallergyConfig.CUSTOMER_LOGIN_URL.trim());
            URI siteUri = URI.create(NatallergyConfig.SITE_BASE);
            String lh = loginUri.getHost();
            String sh = siteUri.getHost();
            if (lh != null && sh != null && !lh.equalsIgnoreCase(sh)) {
                System.out.println("[Natallergy] WARN: login URL host (" + lh + ") differs from SITE_BASE host (" + sh
                        + "). Cookies from login will not apply to the storefront until you set "
                        + "-Dnatallergy.site.base to the same origin as the login page.");
            }
        } catch (Exception ignored) {
            // best-effort warning only
        }
    }

    /** Opens the configured customer login page and waits for the login form document to settle. */
    public static void navigateToCustomerLogin(WebDriver driver) {
        driver.get(NatallergyConfig.CUSTOMER_LOGIN_URL);
        waitForPageFullyLoaded(driver);
    }

    /**
     * Fills the Magento login fieldset on the <b>current</b> page and clicks Sign In (staging reCAPTCHA hook included).
     * Does not wait for success or failure — callers use {@link #waitUntilPastLoginPage} or {@link #waitForLoginFailureSettled}.
     */
    public static void submitCustomerLoginForm(WebDriver driver, String email, String password) throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(45));
        WebElement form = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("fieldset.fieldset.login")));
        WebElement emailEl = form.findElement(By.id("email"));
        WebElement passEl = form.findElement(By.id("pass"));
        safeClick(driver, emailEl);
        emailEl.sendKeys(Keys.chord(Keys.CONTROL, "a"), email);
        safeClick(driver, passEl);
        passEl.sendKeys(Keys.chord(Keys.CONTROL, "a"), password);

        JavascriptExecutor js = (JavascriptExecutor) driver;
        try {
            js.executeScript(
                    "if (typeof jQuery !== 'undefined') {"
                            + " jQuery('[name=\"recaptcha-validate-\"]').prop('checked', true);"
                            + " jQuery('.g-recaptcha-response').val('dassfsd');"
                            + "}");
        } catch (Exception e) {
            System.out.println("[Natallergy] reCAPTCHA staging hook skipped: " + e.getMessage());
        }
        Thread.sleep(1500);

        WebElement signIn = form.findElement(By.xpath(".//button[contains(normalize-space(.),'Sign In')]"));
        safeClick(driver, signIn);
    }

    /** True when the browser URL still points at the Magento customer login route. */
    public static boolean isLikelyOnLoginPage(WebDriver driver) {
        String url = driver.getCurrentUrl();
        return url != null && url.toLowerCase().contains("/customer/account/login");
    }

    /** Magento commonly surfaces failed sign-in as {@code .message-error} or {@code .message.error}. */
    public static boolean hasLoginErrorBanner(WebDriver driver) {
        List<By> candidates = List.of(
                By.cssSelector(".message-error"),
                By.cssSelector(".message.error"),
                By.cssSelector("div.mage-error"),
                By.cssSelector(".messages .error"));
        for (By by : candidates) {
            for (WebElement el : driver.findElements(by)) {
                try {
                    if (el.isDisplayed()) {
                        return true;
                    }
                } catch (StaleElementReferenceException ignored) {
                    // try next
                }
            }
        }
        return false;
    }

    /**
     * After submitting wrong credentials, waits until either the URL is still the login page or an error banner appears.
     */
    public static void waitForLoginFailureSettled(WebDriver driver) {
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(20));
        w.until(d -> isLikelyOnLoginPage(d) || hasLoginErrorBanner(d));
    }

    /** Waits until navigation leaves {@code /customer/account/login} (successful sign-in). */
    public static void waitUntilPastLoginPage(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(45));
        try {
            wait.until(d -> {
                String url = d.getCurrentUrl();
                return url != null && !url.toLowerCase().contains("/customer/account/login");
            });
        } catch (TimeoutException e) {
            throw new IllegalStateException("Login did not leave the login page within timeout. URL="
                    + driver.getCurrentUrl(), e);
        }
    }

    /**
     * Full happy-path sign-in using {@link NatallergyConfig#LOGIN_EMAIL} / {@link NatallergyConfig#LOGIN_PASSWORD}
     * (navigate → submit → wait until past login).
     */
    public static void openLoginPageAndSubmitConfiguredCredentials(WebDriver driver) throws InterruptedException {
        navigateToCustomerLogin(driver);
        submitCustomerLoginForm(driver, NatallergyConfig.LOGIN_EMAIL, NatallergyConfig.LOGIN_PASSWORD);
        waitUntilPastLoginPage(driver);
    }

    /**
     * Opens the shopping cart URL and clicks each line’s delete/remove control until no cart rows remain (best-effort
     * for Magento 2 Luma-style markup).
     */
    public static void clearShoppingCartAllItems(WebDriver driver) throws InterruptedException {
        String cur = driver.getCurrentUrl();
        if (cur == null || !cur.toLowerCase().contains("checkout/cart")) {
            navigateToShoppingCartUrl(driver);
            waitForPageFullyLoaded(driver);
        }
        WebDriverWait waitLong = new WebDriverWait(driver, Duration.ofSeconds(25));
        for (int round = 0; round < 40 && countCartLineItems(driver) > 0; round++) {
            int before = countCartLineItems(driver);
            WebElement deleteLink = null;
            List<By> removeLocators = List.of(
                    By.cssSelector("tr.item-info a.action-delete"),
                    By.cssSelector(".cart.item a.action-delete"),
                    By.cssSelector("a.action.action-delete"),
                    By.xpath("//a[contains(@class,'action-delete') and contains(@data-post,'checkout/cart/remove')]"));
            outer:
            for (By by : removeLocators) {
                for (WebElement el : driver.findElements(by)) {
                    try {
                        if (el.isDisplayed()) {
                            deleteLink = el;
                            break outer;
                        }
                    } catch (StaleElementReferenceException ignored) {
                        // try next element
                    }
                }
            }
            if (deleteLink == null) {
                System.out.println("[Natallergy] No cart delete link found; stopping clear (lines=" + before + ").");
                break;
            }
            safeClick(driver, deleteLink);
            try {
                waitLong.until(d -> countCartLineItems(d) < before);
            } catch (TimeoutException e) {
                waitForPageFullyLoaded(driver);
                Thread.sleep(1200);
            }
        }
    }

    /**
     * Hits Magento’s logout route. When {@code clearCartFirst} is {@code true} (default in {@link #performCustomerLogout(WebDriver)}),
     * {@linkplain #emptyShoppingCartBestEffort empties the cart} (UI clear + line-delete fallback) before signing out.
     */
    public static void performCustomerLogout(WebDriver driver, boolean clearCartFirst) {
        if (clearCartFirst) {
            try {
                emptyShoppingCartBestEffort(driver);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[Natallergy] Cart clear before logout interrupted.");
            } catch (Exception e) {
                System.out.println("[Natallergy] Cart clear before logout skipped: " + e.getMessage());
            }
        }
        String base = NatallergyConfig.SITE_BASE.replaceAll("/+$", "");
        driver.get(base + "/customer/account/logout/");
        waitForPageFullyLoaded(driver);
    }

    /**
     * Same as {@link #performCustomerLogout(WebDriver, boolean) performCustomerLogout(driver, true)} — {@link #emptyShoppingCartBestEffort}
     * then the logout URL.
     */
    public static void performCustomerLogout(WebDriver driver) {
        performCustomerLogout(driver, true);
    }

    /**
     * Signs in with {@link NatallergyConfig#LOGIN_EMAIL} / {@link NatallergyConfig#LOGIN_PASSWORD}, then opens the
     * account dashboard for a report screenshot, then returns to the storefront home so the flow can continue.
     */
    public static void performCustomerLogin(WebDriver driver) throws IOException, InterruptedException {
        openLoginPageAndSubmitConfiguredCredentials(driver);

        driver.get(NatallergyConfig.CUSTOMER_ACCOUNT_DASHBOARD_URL);
        waitForPageFullyLoaded(driver);
        NatallergyReporting.takeScreenshot(driver, "after_login_customer_dashboard", true,
                "Customer account dashboard after login", driver.getCurrentUrl());

        driver.get(NatallergyConfig.SITE_BASE + "/");
        waitForPageFullyLoaded(driver);
        System.out.println("[Natallergy] Customer login finished; dashboard captured; storefront: " + NatallergyConfig.SITE_BASE + "/");
    }

    public static void waitForSearchSuggestionsVisibleOrSettle(WebDriver driver) throws InterruptedException {
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

    public static void waitForPageFullyLoaded(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(d -> "complete".equals(
                ((JavascriptExecutor) d).executeScript("return document.readyState")));
        wait.until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                "return typeof jQuery === 'undefined' || jQuery.active === 0")));
    }

    /** Direct GET to Magento’s cart route (most reliable after add-to-cart). */
    public static void navigateToShoppingCartUrl(WebDriver driver) {
        String base = NatallergyConfig.SITE_BASE.replaceAll("/+$", "");
        driver.get(base + "/checkout/cart/");
    }

    /**
     * Tries mini-cart → “view cart” (several selectors / labels), then falls back to {@link #navigateToShoppingCartUrl}
     * if the flyout markup differs (Hyvä, custom themes, or no “View and Edit Cart” span).
     */
    public static void openShoppingCartPageFromMiniCart(WebDriver driver) throws InterruptedException {
        if (tryNavigateToCartViaMiniCart(driver)) {
            waitForPageFullyLoaded(driver);
            return;
        }
        System.out.println("[Natallergy] Minicart → full cart not available in time; opening /checkout/cart/ directly.");
        navigateToShoppingCartUrl(driver);
        waitForPageFullyLoaded(driver);
    }

    private static boolean tryNavigateToCartViaMiniCart(WebDriver driver) throws InterruptedException {
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
        Thread.sleep(450);
        List<By> showCartTriggers = List.of(
                By.cssSelector("a.action.showcart"),
                By.cssSelector(".minicart-wrapper a.showcart"),
                By.cssSelector(".minicart-wrapper .action.showcart"),
                By.xpath("//a[contains(concat(' ', normalize-space(@class), ' '), ' showcart ')]"));
        boolean openedBag = false;
        for (By by : showCartTriggers) {
            for (WebElement el : driver.findElements(by)) {
                try {
                    if (el.isDisplayed()) {
                        safeClick(driver, el);
                        openedBag = true;
                        break;
                    }
                } catch (StaleElementReferenceException ignored) {
                    // next element
                }
            }
            if (openedBag) {
                break;
            }
        }
        if (!openedBag) {
            return false;
        }
        Thread.sleep(600);
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("#minicart-content-wrapper, .block-minicart, .minicart-items-wrapper, .ui-dialog-content")));
        } catch (TimeoutException ignored) {
            // panel may be minimal; still try view-cart links
        }
        List<By> toCartLinks = List.of(
                By.cssSelector(".minicart-wrapper.active a.action.viewcart"),
                By.cssSelector(".block-minicart a.action.viewcart"),
                By.cssSelector("a.action.viewcart"),
                By.xpath("//div[contains(@class,'minicart')]//a[contains(@href,'checkout/cart') and not(contains(@href,'remove'))]"),
                By.xpath("//span[normalize-space()='View and Edit Cart']/ancestor::a[1]"),
                By.xpath("//a[.//span[normalize-space()='View and Edit Cart']]"),
                By.xpath("//div[contains(@class,'minicart')]//a[contains(normalize-space(.),'View and Edit Cart')]"),
                By.xpath("//div[contains(@class,'minicart')]//a[contains(normalize-space(.),'View Cart')]"),
                By.xpath("//div[contains(@class,'minicart')]//a[contains(normalize-space(.),'Shopping Cart')]"));
        for (By by : toCartLinks) {
            for (WebElement link : driver.findElements(by)) {
                try {
                    if (!link.isDisplayed()) {
                        continue;
                    }
                    safeClick(driver, link);
                    Thread.sleep(700);
                    String u = driver.getCurrentUrl();
                    if (u != null && (u.contains("/checkout/cart") || u.toLowerCase().contains("shopping_cart"))) {
                        return true;
                    }
                    waitForPageFullyLoaded(driver);
                    u = driver.getCurrentUrl();
                    if (u != null && (u.contains("/checkout/cart") || u.toLowerCase().contains("shopping_cart"))) {
                        return true;
                    }
                } catch (StaleElementReferenceException ignored) {
                    // next candidate
                }
            }
        }
        return false;
    }

    /**
     * Clicks {@code //span[normalize-space()='Clear Shopping Cart']} when visible, then polls for a confirm
     * {@code OK} / {@code action-accept} button (several Magento / theme layouts — no single {@code .modal-inner-wrap} requirement).
     *
     * @return {@code true} if the clear action ran; {@code false} if the control was missing or OK could not be found
     */
    public static boolean clearShoppingCartViaUiButtonAndConfirmOkIfPresent(WebDriver driver) throws InterruptedException {
        String u = driver.getCurrentUrl();
        if (u == null || !u.toLowerCase().contains("checkout/cart")) {
            navigateToShoppingCartUrl(driver);
            waitForPageFullyLoaded(driver);
        }
        WebElement clearTrigger = null;
        for (WebElement span : driver.findElements(By.xpath("//span[normalize-space()='Clear Shopping Cart']"))) {
            try {
                if (span.isDisplayed()) {
                    clearTrigger = span;
                    break;
                }
            } catch (StaleElementReferenceException ignored) {
                // next
            }
        }
        if (clearTrigger == null) {
            System.out.println("[Natallergy] “Clear Shopping Cart” not shown — cart likely empty; skipping clear modal.");
            return false;
        }
        safeClick(driver, clearTrigger);
        Thread.sleep(500);
        WebElement ok = pollForClearCartConfirmOk(driver, 22);
        if (ok == null) {
            System.out.println("[Natallergy] Confirm OK for “Clear Shopping Cart” not found — modal markup may differ.");
            dismissTopModalIfPresent(driver);
            return false;
        }
        safeClick(driver, ok);
        try {
            new WebDriverWait(driver, Duration.ofSeconds(12)).until(ExpectedConditions.invisibilityOfElementLocated(
                    By.cssSelector(".modal-popup._show, .modals-wrapper._show")));
        } catch (TimeoutException e) {
            Thread.sleep(600);
        }
        waitForPageFullyLoaded(driver);
        return true;
    }

    /** Polls for Magento clear-cart confirm OK (no dependency on a specific {@code .modal-inner-wrap} wrapper). */
    private static WebElement pollForClearCartConfirmOk(WebDriver driver, int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        List<By> okLocators = List.of(
                By.cssSelector(".modal-footer button.action-primary.action-accept"),
                By.cssSelector(".modal-popup .modal-footer button.action-primary"),
                By.cssSelector("aside.modal-popup button.action-accept"),
                By.cssSelector(".modals-wrapper .modal-popup button.action-primary.action-accept"),
                By.cssSelector(".modal-popup._show button.action-primary"),
                By.xpath("//div[contains(@class,'modal-popup')]//footer//button[contains(@class,'action-accept')]"),
                By.xpath("//div[contains(@class,'modal-inner-wrap')]//button[.//span[normalize-space()='OK']]"),
                By.xpath("//div[contains(@class,'modal-footer')]//button[.//span[normalize-space()='OK']]"),
                By.xpath("//button[@type='button' and .//span[normalize-space()='OK'] and ancestor::*[self::aside or contains(@class,'modal')]]"));
        while (System.currentTimeMillis() < deadline) {
            for (By by : okLocators) {
                for (WebElement el : driver.findElements(by)) {
                    try {
                        if (el.isDisplayed() && el.isEnabled()) {
                            return el;
                        }
                    } catch (StaleElementReferenceException ignored) {
                        // next element
                    }
                }
            }
            Thread.sleep(350);
        }
        return null;
    }

    private static void dismissTopModalIfPresent(WebDriver driver) {
        for (By by : List.of(
                By.cssSelector(".modal-popup._show .action-close"),
                By.cssSelector(".modal-popup .action-close"),
                By.cssSelector("button.action-close[data-role='closeBtn']"))) {
            for (WebElement el : driver.findElements(by)) {
                try {
                    if (el.isDisplayed()) {
                        safeClick(driver, el);
                        return;
                    }
                } catch (StaleElementReferenceException ignored) {
                    // next
                }
            }
        }
    }

    /**
     * Empties the cart for logout/teardown: tries {@link #clearShoppingCartViaUiButtonAndConfirmOkIfPresent} first, then
     * line-by-line {@link #clearShoppingCartAllItems} only if rows remain (e.g. theme has no “Clear Shopping Cart”).
     */
    public static void emptyShoppingCartBestEffort(WebDriver driver) throws InterruptedException {
        try {
            clearShoppingCartViaUiButtonAndConfirmOkIfPresent(driver);
        } catch (Exception e) {
            System.out.println("[Natallergy] UI clear-all path failed, will use line-delete fallback if needed: " + e.getMessage());
        }
        if (countCartLineItems(driver) > 0) {
            clearShoppingCartAllItems(driver);
        }
    }

    /**
     * Counts visible cart line blocks ({@code tbody.cart.item} in Magento 2, with a fallback for older table markup).
     */
    public static int countCartLineItems(WebDriver driver) {
        List<WebElement> rows = driver.findElements(By.cssSelector("table.cart.items tbody.cart.item"));
        long n = rows.stream().filter(e -> {
            try {
                return e.isDisplayed();
            } catch (Exception ex) {
                return false;
            }
        }).count();
        if (n > 0) {
            return (int) n;
        }
        return (int) driver.findElements(By.cssSelector("#shopping-cart-table tbody tr")).stream()
                .filter(e -> {
                    try {
                        if (!e.isDisplayed()) {
                            return false;
                        }
                        String cls = e.getAttribute("class");
                        return cls != null && (cls.contains("item") || cls.contains("cart"));
                    } catch (Exception ex) {
                        return false;
                    }
                }).count();
    }

    /** Enters a code on the cart page and clicks Apply Discount. */
    public static void applyDiscountCouponOnCartPage(WebDriver driver, String couponCode) throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        WebElement couponInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("coupon_code")));
        safeClick(driver, couponInput);
        couponInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), couponCode);
        safeClick(driver, wait, By.xpath("//span[normalize-space()=\"Apply Discount\"]"));
        waitForPageFullyLoaded(driver);
    }

    /**
     * Clicks the Magento “Cancel coupon” / remove control when visible. Safe to call when no coupon is applied (no-op).
     */
    public static void removeAppliedCouponOnCartPageIfPresent(WebDriver driver) throws InterruptedException {
        List<By> cancelOrRemove = List.of(
                By.cssSelector("#discount-coupon-form button.action-cancel"),
                By.cssSelector("form#discount-coupon-form .action-cancel"),
                By.xpath("//form[@id='discount-coupon-form']//button[contains(@class,'cancel')]"),
                By.cssSelector(".block.discount .action.cancel"),
                By.cssSelector(".applied-coupon .action-remove"),
                By.xpath("//a[contains(@class,'action-remove') and ancestor::*[contains(@class,'discount')]]"));
        for (By by : cancelOrRemove) {
            for (WebElement el : driver.findElements(by)) {
                try {
                    if (el.isDisplayed()) {
                        safeClick(driver, el);
                        waitForPageFullyLoaded(driver);
                        Thread.sleep(600);
                        return;
                    }
                } catch (StaleElementReferenceException ignored) {
                    // try next candidate
                }
            }
        }
    }

    /** {@code true} when Magento still shows a cancel/remove control for an applied cart coupon. */
    public static boolean isCartCancelCouponControlPresent(WebDriver driver) {
        List<By> locators = List.of(
                By.cssSelector("#discount-coupon-form button.action-cancel"),
                By.cssSelector("form#discount-coupon-form .action-cancel"),
                By.cssSelector(".block.discount .action.cancel"),
                By.cssSelector(".applied-coupon .action-remove"));
        for (By by : locators) {
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
        return false;
    }

    private NatallergyPageActions() {
    }
}
