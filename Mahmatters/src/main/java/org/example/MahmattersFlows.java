package org.example;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end Magento storefront automation for Mahj MATters.
 * <ul>
 *   <li>{@link #checkLogin} — optional account login (env {@code MAH_LOGIN_EMAIL} / {@code MAH_LOGIN_PASSWORD}).</li>
 *   <li>{@link #runShoppingAndCheckout} — PDP → cart → {@link #verifyLoginBeforeCheckout} → checkout → payment
 *       → cart cleanup ({@link MahmattersConfig#CART_REMOVE_ITEM}).</li>
 * </ul>
 * Locators and URLs live in {@link MahmattersConfig}; screenshots in {@link TestReporter}; DOM helpers in {@link WebDriverUtils}.
 */
public final class MahmattersFlows {

    private MahmattersFlows() {
    }

    // -------------------------------------------------------------------------
    // Orchestration
    // -------------------------------------------------------------------------

    /**
     * Opens the customer login page, fills credentials if configured, submits, and records success/error/timeout.
     * No-op when email or password is blank (after env defaults).
     */
    public static void checkLogin(WebDriver driver, AutomationRun run) throws IOException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        driver.get(MahmattersConfig.CUSTOMER_LOGIN_URL);
        WebDriverUtils.waitForPageFullyLoaded(driver);

        // Logged-in session: Magento often skips the login form and lands on the account area.
        String urlAfterLoad = driver.getCurrentUrl();
        if (urlAfterLoad.contains("/customer/account/") && !urlAfterLoad.contains("/login")) {
            TestReporter.takeScreenshot(driver, run, "login_already_signed_in", true,
                    "Already authenticated; login URL redirected to customer account (no form).");
            return;
        }

        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(MahmattersConfig.LOGIN_FORM));
        } catch (TimeoutException e) {
            wait.until(ExpectedConditions.visibilityOfElementLocated(MahmattersConfig.LOGIN_FORM_FALLBACK));
        }
        // Prefer first *visible* match (Magento can duplicate nodes in templates).
        WebElement emailField = WebDriverUtils.firstDisplayedMatching(driver, wait, MahmattersConfig.LOGIN_EMAIL_FIELD);
        WebElement passField = WebDriverUtils.firstDisplayedMatching(driver, wait, MahmattersConfig.LOGIN_PASSWORD_FIELD);
        WebElement submitBtn = WebDriverUtils.firstDisplayedMatching(driver, wait, MahmattersConfig.LOGIN_SUBMIT_BUTTON);
        if (emailField == null || passField == null || submitBtn == null) {
            TestReporter.takeScreenshot(driver, run, "login_fields_not_found", false,
                    "Missing visible login control (email=" + (emailField != null) + ", password=" + (passField != null)
                            + ", signIn=" + (submitBtn != null) + ").");
            return;
        }
        WebDriverUtils.scrollIntoView(driver, emailField);

        String email = WebDriverUtils.firstNonBlank(System.getenv("MAH_LOGIN_EMAIL"), MahmattersConfig.loginTestEmail);
        String password = WebDriverUtils.firstNonBlank(System.getenv("MAH_LOGIN_PASSWORD"), MahmattersConfig.loginTestPassword);
        boolean willSubmit = !email.isBlank() && !password.isBlank();

        TestReporter.takeScreenshot(driver, run, "login_page_form_ready", true,
                "Login page: email, password, Sign In at " + MahmattersConfig.CUSTOMER_LOGIN_URL + ". "
                        + (willSubmit ? "Will submit (env overrides defaults if set)." : "No credentials configured."));

        if (!willSubmit) {
            return;
        }

        emailField.clear();
        emailField.sendKeys(email);
        passField.clear();
        passField.sendKeys(password);
        WebDriverUtils.scrollIntoView(driver, submitBtn);
        WebDriverUtils.safeClick(driver, submitBtn);

        WebDriverWait outcomeWait = new WebDriverWait(driver, Duration.ofSeconds(25));
        try {
            outcomeWait.until(webDriver -> {
                String u = webDriver.getCurrentUrl();
                if (u.contains("/customer/account/") && !u.contains("/login")) {
                    return true;
                }
                for (WebElement el : webDriver.findElements(
                        By.cssSelector(".message-error, .messages .message-error"))) {
                    try {
                        if (el.isDisplayed()) {
                            return true;
                        }
                    } catch (StaleElementReferenceException e) {
                        // next
                    }
                }
                return false;
            });
        } catch (TimeoutException e) {
            TestReporter.takeScreenshot(driver, run, "login_submit_timeout", false,
                    "Login submit: no redirect away from login and no visible error within timeout.");
            return;
        }

        String urlAfter = driver.getCurrentUrl();
        boolean errorVisible = false;
        for (WebElement el : driver.findElements(By.cssSelector(".message-error, .messages .message-error"))) {
            try {
                if (el.isDisplayed()) {
                    errorVisible = true;
                    break;
                }
            } catch (StaleElementReferenceException ignored) {
                // next
            }
        }
        boolean success = urlAfter.contains("/customer/account/") && !urlAfter.contains("/login");

        if (success && !errorVisible) {
            TestReporter.takeScreenshot(driver, run, "login_success", true,
                    "Signed in: left login page for customer account area.");
        } else if (errorVisible) {
            TestReporter.takeScreenshot(driver, run, "login_error_message", false,
                    "Login page shows an error after submit (check credentials or captcha).");
        } else {
            TestReporter.takeScreenshot(driver, run, "login_submit_unknown_state", true, "After submit URL: " + urlAfter);
        }
    }

    /**
     * Opens the login page and runs {@link #checkLogin}, then always returns to the cart so
     * “Proceed to checkout” still runs on the full cart. Use right before checkout when you want
     * login verified after add-to-cart (session/cart alignment).
     */
    public static void verifyLoginBeforeCheckout(WebDriver driver, AutomationRun run) throws IOException {
        try {
            checkLogin(driver, run);
        } catch (Exception e) {
            TestReporter.takeScreenshot(driver, run, "pre_checkout_login_check_exception", false,
                    "Pre-checkout login check: " + e.getMessage());
        } finally {
            driver.get(MahmattersConfig.CART_PAGE_URL);
            WebDriverUtils.waitForPageFullyLoaded(driver);
            TestReporter.takeScreenshot(driver, run, "cart_after_pre_checkout_login_check", true,
                    "Back on cart after pre-checkout login verification; ready for Proceed to checkout.");
        }
    }

    /**
     * Main happy-path after the homepage. Each major block is wrapped in try/catch so a single failure
     * still produces a screenshot and later steps can run. Uses short {@link Thread#sleep} in a few
     * places where mini-cart / AJAX timing was flaky with waits alone.
     */
    public static void runShoppingAndCheckout(WebDriver driver, AutomationRun run) throws IOException, InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        // --- PDP: open configured product URL (mats / shufflers / etc.; env MAH_PDP_URL overrides) ---
        String productUrl = WebDriverUtils.firstNonBlank(System.getenv("MAH_PDP_URL"), MahmattersConfig.pdpProductUrl);
        driver.get(productUrl);
        WebDriverUtils.waitForPageFullyLoaded(driver);
        TestReporter.takeScreenshot(driver, run, "product_page", true, "Opened PDP: " + productUrl);

        // --- super_attribute[188] “Design” (mats & shufflers); bundle PDP uses a different UI ---
        if (driver.findElements(MahmattersConfig.DESIGN_SUPER_ATTRIBUTE_SELECT).isEmpty()) {
            TestReporter.takeScreenshot(driver, run, "no_design_dropdown", true,
                    "No single Design select (#attribute188) on this PDP (e.g. bundle uses bundle options).");
            return;
        }

        WebElement designSelect = wait.until(ExpectedConditions.elementToBeClickable(MahmattersConfig.DESIGN_SUPER_ATTRIBUTE_SELECT));
        Select design = new Select(designSelect);
        String designValue = WebDriverUtils.firstNonBlank(System.getenv("MAH_PDP_DESIGN_VALUE"), MahmattersConfig.pdpSuperAttributeValue);
        if (designValue.isBlank()) {
            designValue = firstNonEmptyOptionValue(design);
        }
        design.selectByValue(designValue);
        wait.until(ExpectedConditions.attributeToBe(MahmattersConfig.DESIGN_SUPER_ATTRIBUTE_SELECT, "value", designValue));
        String label = selectedOptionLabel(design);
        TestReporter.takeScreenshot(driver, run, "product_design_selected", true,
                "Design super_attribute[188] value=" + designValue + (label.isEmpty() ? "" : " (" + label + ")."));

        WebElement addToCartButton = wait.until(ExpectedConditions.elementToBeClickable(MahmattersConfig.ADD_TO_CART_BUTTON));
        WebDriverUtils.safeClick(driver, addToCartButton);
        WebDriverUtils.waitForPageFullyLoaded(driver);
        Thread.sleep(2000); // allow mini-cart / fragments to settle after add-to-cart
        TestReporter.takeScreenshot(driver, run, "add_to_cart", true, "Clicked Add to Cart.");

        Thread.sleep(2000);
        // --- Mini-cart → “Shopping cart” link to full cart page ---
        try {
            WebElement cartButton = wait.until(ExpectedConditions.elementToBeClickable(MahmattersConfig.MINICART_SHOPPING_CART_LINK));
            WebDriverUtils.safeClick(driver, cartButton);
            WebDriverUtils.waitForPageFullyLoaded(driver);
            TestReporter.takeScreenshot(driver, run, "shopping_cart_clicked", true, "Clicked shopping cart from mini-cart.");
        } catch (Exception e) {
            TestReporter.takeScreenshot(driver, run, "shopping_cart_click_failed", false,
                    "Could not click shopping cart link: " + e.getMessage());
        }

        // --- Full cart: qty + update; direct URL fallback if still on mini-cart-only view ---
        try {
            WebElement qtyInput = WebDriverUtils.firstDisplayedCartQty(driver, wait);
            if (qtyInput == null) {
                driver.get(MahmattersConfig.CART_PAGE_URL);
                WebDriverUtils.waitForPageFullyLoaded(driver);
                qtyInput = WebDriverUtils.firstDisplayedCartQty(driver, new WebDriverWait(driver, Duration.ofSeconds(25)));
            }
            if (qtyInput == null) {
                throw new IllegalStateException("No visible cart qty input.");
            }
            WebDriverUtils.scrollIntoView(driver, qtyInput);
            qtyInput.clear();
            qtyInput.sendKeys("3");
            TestReporter.takeScreenshot(driver, run, "update_qty", true, "Set line quantity to 3.");

            WebElement updateCartButton = WebDriverUtils.firstDisplayedMatching(driver,
                    new WebDriverWait(driver, Duration.ofSeconds(15)), MahmattersConfig.UPDATE_SHOPPING_CART_BUTTON);
            if (updateCartButton == null) {
                throw new IllegalStateException("Update Shopping Cart button not found.");
            }
            WebDriverUtils.scrollIntoView(driver, updateCartButton);
            WebDriverUtils.safeClick(driver, updateCartButton);
            WebDriverUtils.waitForPageFullyLoaded(driver);
            TestReporter.takeScreenshot(driver, run, "update_shopping_cart", true, "Clicked Update Shopping Cart.");
        } catch (Exception e) {
            TestReporter.takeScreenshot(driver, run, "update_cart_failed", false, "Cart qty/update failed: " + e.getMessage());
        }

        // --- Coupon: expand discount block if collapsed, then Apply ---
        try {
            applyCartDiscount(driver, wait, MahmattersConfig.cartCouponCode);
            TestReporter.takeScreenshot(driver, run, "apply_discount_code", true,
                    "Applied coupon: " + MahmattersConfig.cartCouponCode);
        } catch (Exception e) {
            TestReporter.takeScreenshot(driver, run, "apply_discount_failed", false, "Discount failed: " + e.getMessage());
        }

        // --- Login again before checkout (re-check session; then return to cart for Proceed) ---
        try {
            verifyLoginBeforeCheckout(driver, run);
        } catch (Exception e) {
            TestReporter.takeScreenshot(driver, run, "verify_login_before_checkout_failed", false,
                    "Pre-checkout login verification failed: " + e.getMessage());
            driver.get(MahmattersConfig.CART_PAGE_URL);
            WebDriverUtils.waitForPageFullyLoaded(driver);
        }

        // --- Checkout entry: first visible enabled “Proceed to checkout” (Magento may duplicate nodes) ---
        try {
            WebElement proceedBtn = wait.until(webDriver -> {
                for (WebElement b : webDriver.findElements(MahmattersConfig.PROCEED_TO_CHECKOUT_BUTTON)) {
                    try {
                        if (b.isDisplayed() && b.isEnabled()) {
                            return b;
                        }
                    } catch (StaleElementReferenceException ignored) {
                        // next
                    }
                }
                return null;
            });
            WebDriverUtils.scrollIntoView(driver, proceedBtn);
            WebDriverUtils.safeClick(driver, proceedBtn);
            WebDriverUtils.waitForPageFullyLoaded(driver);
            TestReporter.takeScreenshot(driver, run, "proceed_to_checkout", true, "Clicked Proceed to Checkout.");
        } catch (Exception e) {
            TestReporter.takeScreenshot(driver, run, "proceed_to_checkout_failed", false,
                    "Proceed to checkout failed: " + e.getMessage());
        }

        // --- One-page checkout: email (guest), shipping form, then Continue to payment ---
        try {
            checkoutGuestEmail(driver, new WebDriverWait(driver, Duration.ofSeconds(25)), run);
        } catch (Exception e) {
            TestReporter.takeScreenshot(driver, run, "checkout_email_step_failed", false, "Checkout email failed: " + e.getMessage());
        }

        try {
            fillShippingAddress(driver, new WebDriverWait(driver, Duration.ofSeconds(30)), run);
        } catch (Exception e) {
            TestReporter.takeScreenshot(driver, run, "checkout_shipping_failed", false, "Shipping failed: " + e.getMessage());
        }

        try {
            continueToPayment(driver, new WebDriverWait(driver, Duration.ofSeconds(25)), run);
        } catch (Exception e) {
            TestReporter.takeScreenshot(driver, run, "checkout_continue_payment_failed", false,
                    "Continue / payment step failed: " + e.getMessage());
        }

        // --- After checkout: screenshot → full cart → Remove item(s) (TestRigor path / Magento delete) ---
        try {
            postCheckoutRemoveCartItems(driver, run);
        } catch (Exception e) {
            TestReporter.takeScreenshot(driver, run, "post_checkout_cart_cleanup_failed", false,
                    "Post-checkout cart cleanup: " + e.getMessage());
        }
    }

    /**
     * Captures checkout state, opens {@link MahmattersConfig#CART_PAGE_URL}, and removes each line via
     * {@link MahmattersConfig#CART_REMOVE_ITEM} until none remain or attempts exhausted.
     */
    private static void postCheckoutRemoveCartItems(WebDriver driver, AutomationRun run) throws IOException {
        driver.get(MahmattersConfig.CART_PAGE_URL);
        WebDriverUtils.waitForPageFullyLoaded(driver);

        int removed = 0;
        final int maxRemovals = 20;
        while (removed < maxRemovals) {
            WebElement remove = WebDriverUtils.firstDisplayedMatching(driver,
                    new WebDriverWait(driver, Duration.ofSeconds(12)), MahmattersConfig.CART_REMOVE_ITEM);
            if (remove == null) {
                break;
            }
            WebDriverUtils.scrollIntoView(driver, remove);
            WebDriverUtils.safeClick(driver, remove);
            try {
                new WebDriverWait(driver, Duration.ofSeconds(15))
                        .until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-mask._show")));
            } catch (Exception ignored) {
                // cart may update without mask
            }
            try {
                new WebDriverWait(driver, Duration.ofSeconds(8)).until(ExpectedConditions.stalenessOf(remove));
            } catch (Exception ignored) {
                // row may re-render without staleness in some themes
            }
            WebDriverUtils.waitForPageFullyLoaded(driver);
            removed++;
            // Report step must show cart state only after Remove click + update (not before).
            TestReporter.takeScreenshot(driver, run, "cart_after_remove_item_click_" + removed, true,
                    "Screenshot after Remove item click #" + removed + " (" + MahmattersConfig.CART_PAGE_URL + ").");
        }

        if (removed == 0) {
            TestReporter.takeScreenshot(driver, run, "cart_no_remove_control", true,
                    "No visible Remove item / action-delete control (cart may already be empty).");
        }
    }

    /** First {@code <option>} with a non-empty {@code value} (skips Magento “Choose an Option…” placeholder). */
    private static String firstNonEmptyOptionValue(Select select) {
        for (WebElement opt : select.getOptions()) {
            String v = opt.getDomAttribute("value");
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        throw new IllegalStateException("No Design option with a non-empty value (only placeholder?).");
    }

    private static String selectedOptionLabel(Select select) {
        try {
            WebElement o = select.getFirstSelectedOption();
            String t = o.getText().replaceAll("\\s+", " ").trim();
            return t == null ? "" : t;
        } catch (Exception e) {
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // Checkout substeps
    // -------------------------------------------------------------------------

    /**
     * Guest path: {@code #customer-email} + TAB. Logged-in customers often skip the guest email block entirely
     * ({@code form[data-role='email-with-possible-login']} not in DOM or hidden) — then we skip typing and continue.
     */
    private static void checkoutGuestEmail(WebDriver driver, WebDriverWait wait, AutomationRun run) throws IOException {
        WebDriverWait stepWait = new WebDriverWait(driver, Duration.ofSeconds(25));
        try {
            stepWait.until(MahmattersFlows::checkoutEmailOrShippingReady);
        } catch (TimeoutException e) {
            TestReporter.takeScreenshot(driver, run, "checkout_email_step_failed", false,
                    "Checkout did not show guest email UI or shipping step within 25s.");
            return;
        }

        WebElement emailField = WebDriverUtils.firstDisplayedMatching(driver,
                new WebDriverWait(driver, Duration.ofSeconds(5)), MahmattersConfig.CHECKOUT_CUSTOMER_EMAIL);
        boolean canTypeGuestEmail = emailField != null && emailField.isDisplayed() && emailField.isEnabled();

        if (!canTypeGuestEmail) {
            WebElement shipping = WebDriverUtils.firstDisplayedMatching(driver,
                    new WebDriverWait(driver, Duration.ofSeconds(5)), MahmattersConfig.CHECKOUT_SHIPPING_FORM);
            if (shipping != null) {
                TestReporter.takeScreenshot(driver, run, "checkout_guest_email_skipped_logged_in", true,
                        "Guest email field not visible/editable (typical when already logged in); shipping step is shown.");
                return;
            }
            TestReporter.takeScreenshot(driver, run, "checkout_email_step_unclear", false,
                    "No usable #customer-email and no visible shipping form.");
            return;
        }

        WebDriverUtils.clearAndType(driver, emailField, MahmattersConfig.checkoutGuestEmail);
        emailField.sendKeys(Keys.TAB);

        try {
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-mask._show")));
        } catch (Exception ignored) {
            // optional
        }
        WebDriverUtils.waitForPageFullyLoaded(driver);

        List<WebElement> pwd = driver.findElements(MahmattersConfig.CHECKOUT_CUSTOMER_PASSWORD);
        boolean loginVisible = !pwd.isEmpty() && pwd.get(0).isDisplayed();
        boolean loginButtonPresent = !driver.findElements(MahmattersConfig.CHECKOUT_LOGIN_BUTTON).isEmpty();

        TestReporter.takeScreenshot(driver, run, "checkout_guest_email_step", true,
                "Checkout email: " + MahmattersConfig.checkoutGuestEmail + ". "
                        + (loginVisible
                        ? "Existing customer UI visible; Login present=" + loginButtonPresent
                        : "Guest path."));
    }

    /** True when either guest email is on screen or shipping step is already active (logged-in checkout). */
    private static Boolean checkoutEmailOrShippingReady(WebDriver driver) {
        for (WebElement el : driver.findElements(MahmattersConfig.CHECKOUT_SHIPPING_FORM)) {
            try {
                if (el.isDisplayed()) {
                    return Boolean.TRUE;
                }
            } catch (StaleElementReferenceException ignored) {
                // next
            }
        }
        for (WebElement f : driver.findElements(MahmattersConfig.CHECKOUT_EMAIL_FORM)) {
            try {
                if (!f.isDisplayed()) {
                    continue;
                }
                for (WebElement em : driver.findElements(MahmattersConfig.CHECKOUT_CUSTOMER_EMAIL)) {
                    if (em.isDisplayed()) {
                        return Boolean.TRUE;
                    }
                }
            } catch (StaleElementReferenceException ignored) {
                // next
            }
        }
        for (WebElement em : driver.findElements(MahmattersConfig.CHECKOUT_CUSTOMER_EMAIL)) {
            try {
                if (em.isDisplayed()) {
                    return Boolean.TRUE;
                }
            } catch (StaleElementReferenceException ignored) {
                // next
            }
        }
        return null;
    }

    /**
     * Fills {@code #co-shipping-form} using {@link WebDriverUtils#setShippingInput}.
     * After country change, re-finds the form so region {@code select} is not stale.
     */
    private static void fillShippingAddress(WebDriver driver, WebDriverWait wait, AutomationRun run) throws IOException {
        WebElement shippingLi = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("shipping")));
        WebDriverUtils.scrollIntoView(driver, shippingLi);

        WebElement form = wait.until(ExpectedConditions.visibilityOfElementLocated(MahmattersConfig.CHECKOUT_SHIPPING_FORM));
        wait.until(ExpectedConditions.elementToBeClickable(form.findElement(By.name("firstname"))));

        WebDriverUtils.setShippingInput(driver, form, "firstname", MahmattersConfig.shipFirstName);
        WebDriverUtils.setShippingInput(driver, form, "lastname", MahmattersConfig.shipLastName);
        List<WebElement> companyFields = form.findElements(By.name("company"));
        if (!companyFields.isEmpty() && companyFields.get(0).isDisplayed()) {
            if (MahmattersConfig.shipCompany != null && !MahmattersConfig.shipCompany.isBlank()) {
                WebDriverUtils.setShippingInput(driver, form, "company", MahmattersConfig.shipCompany);
            } else {
                WebElement companyEl = companyFields.get(0);
                WebDriverUtils.clearAndType(driver, companyEl, "");
                companyEl.sendKeys(Keys.TAB);
            }
        }

        WebDriverUtils.setShippingInput(driver, form, "street[0]", MahmattersConfig.shipStreetLine1);
        for (String streetExtra : new String[] { "street[1]", "street[2]" }) {
            List<WebElement> extraLines = form.findElements(By.name(streetExtra));
            if (!extraLines.isEmpty() && extraLines.get(0).isDisplayed()) {
                WebDriverUtils.setShippingInput(driver, form, streetExtra, "");
            }
        }

        WebElement countrySelectEl = form.findElement(By.name("country_id"));
        WebDriverUtils.scrollIntoView(driver, countrySelectEl);
        new Select(countrySelectEl).selectByValue(MahmattersConfig.shipCountryId);

        wait.until(webDriver -> {
            try {
                List<WebElement> list = webDriver.findElements(By.cssSelector("#co-shipping-form select[name='region_id']"));
                if (list.isEmpty() || !list.get(0).isDisplayed()) {
                    return true;
                }
                return new Select(list.get(0)).getOptions().stream()
                        .anyMatch(o -> MahmattersConfig.shipRegionId.equals(o.getDomAttribute("value")));
            } catch (StaleElementReferenceException e) {
                return false;
            }
        });

        WebElement formAfterCountry = wait.until(ExpectedConditions.visibilityOfElementLocated(MahmattersConfig.CHECKOUT_SHIPPING_FORM));
        List<WebElement> regionSelects = formAfterCountry.findElements(By.name("region_id"));
        if (!regionSelects.isEmpty() && regionSelects.get(0).isDisplayed()) {
            WebElement rs = regionSelects.get(0);
            WebDriverUtils.scrollIntoView(driver, rs);
            new Select(rs).selectByValue(MahmattersConfig.shipRegionId);
        } else {
            List<WebElement> regionText = formAfterCountry.findElements(By.name("region"));
            if (!regionText.isEmpty() && regionText.get(0).isDisplayed()) {
                WebDriverUtils.setShippingInput(driver, formAfterCountry, "region", "California");
            }
        }

        WebElement formFinal = driver.findElement(MahmattersConfig.CHECKOUT_SHIPPING_FORM);
        WebDriverUtils.setShippingInput(driver, formFinal, "city", MahmattersConfig.shipCity);
        WebDriverUtils.setShippingInput(driver, formFinal, "postcode", MahmattersConfig.shipPostcode);
        WebDriverUtils.setShippingInput(driver, formFinal, "telephone", MahmattersConfig.shipTelephone);

        try {
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-mask._show")));
        } catch (Exception ignored) {
            // optional
        }
        WebDriverUtils.waitForPageFullyLoaded(driver);

        // Let carrier quotes render before the “address filled” screenshot (rates / shipping table).
        try {
            new WebDriverWait(driver, Duration.ofSeconds(MahmattersConfig.checkoutShippingRatesWaitSeconds))
                    .until(MahmattersFlows::checkoutShippingMethodRadioVisible);
        } catch (TimeoutException e) {
            // Rates may still be loading or unavailable; Next-button check below is the hard gate.
        }
        try {
            new WebDriverWait(driver, Duration.ofSeconds(MahmattersConfig.checkoutShippingContinueWaitSeconds))
                    .until(MahmattersFlows::checkoutShippingContinueVisible);
        } catch (TimeoutException e) {
            // fall through to explicit check + failed report
        }

        WebElement shippingNext = firstVisibleShippingContinueButton(driver);
        if (shippingNext == null) {
            TestReporter.takeScreenshot(driver, run, "checkout_shipping_next_not_visible", false,
                    "Shipping Continue/Next button not visible or not enabled after waiting up to "
                            + MahmattersConfig.checkoutShippingRatesWaitSeconds + "s for rates and "
                            + MahmattersConfig.checkoutShippingContinueWaitSeconds + "s for the button. "
                            + checkoutShippingStepErrorsSummary(driver));
            return;
        }

        TestReporter.takeScreenshot(driver, run, "checkout_shipping_address_filled", true,
                "Shipping: " + MahmattersConfig.shipFirstName + " " + MahmattersConfig.shipLastName + ", "
                        + MahmattersConfig.shipCity + " " + MahmattersConfig.shipPostcode + ". "
                        + "Rates wait " + MahmattersConfig.checkoutShippingRatesWaitSeconds + "s; "
                        + "shipping_method radios visible=" + checkoutShippingMethodRadioVisible(driver) + ".");
    }

    private static boolean checkoutShippingMethodRadioVisible(WebDriver driver) {
        for (WebElement r : driver.findElements(MahmattersConfig.CHECKOUT_SHIPPING_METHOD_RADIO)) {
            try {
                if (r.isDisplayed()) {
                    return true;
                }
            } catch (StaleElementReferenceException ignored) {
                // next
            }
        }
        return false;
    }

    private static Boolean checkoutShippingContinueVisible(WebDriver driver) {
        return firstVisibleShippingContinueButton(driver) != null ? Boolean.TRUE : null;
    }

    private static WebElement firstVisibleShippingContinueButton(WebDriver driver) {
        for (WebElement b : driver.findElements(MahmattersConfig.CHECKOUT_SHIPPING_CONTINUE)) {
            try {
                if (b.isDisplayed() && b.isEnabled()) {
                    return b;
                }
            } catch (StaleElementReferenceException ignored) {
                // next
            }
        }
        return null;
    }

    private static String checkoutShippingStepErrorsSummary(WebDriver driver) {
        StringBuilder sb = new StringBuilder();
        for (WebElement el : driver.findElements(MahmattersConfig.CHECKOUT_SHIPPING_STEP_ERRORS)) {
            try {
                if (el.isDisplayed()) {
                    String t = el.getText().replaceAll("\\s+", " ").trim();
                    if (!t.isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append(" | ");
                        }
                        sb.append(t.length() > 200 ? t.substring(0, 200) + "…" : t);
                    }
                }
            } catch (StaleElementReferenceException ignored) {
                // next
            }
        }
        return sb.length() == 0 ? "No visible error text in shipping/checkout area." : "Messages: " + sb;
    }

    /** Clicks shipping Continue, waits for loading mask, then for payment method UI + final screenshot. */
    private static void continueToPayment(WebDriver driver, WebDriverWait wait, AutomationRun run) throws IOException {
        WebElement continueBtn = wait.until(MahmattersFlows::firstVisibleShippingContinueButton);
        WebDriverUtils.scrollIntoView(driver, continueBtn);
        WebDriverUtils.safeClick(driver, continueBtn);

        WebDriverWait paymentWait = new WebDriverWait(driver, Duration.ofSeconds(45));
        try {
            paymentWait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-mask._show")));
        } catch (TimeoutException e) {
            // optional mask
        }

        paymentWait.until(MahmattersFlows::paymentMethodsVisible);

        try {
            paymentWait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-mask._show")));
        } catch (TimeoutException e) {
            // ignore
        }
        WebDriverUtils.waitForPageFullyLoaded(driver);

        int visibleRadios = countVisiblePaymentRadios(driver);
        TestReporter.takeScreenshot(driver, run, "checkout_payment_methods_loaded", true,
                "Shipping Continue clicked; payment methods visible (radios: " + visibleRadios + ").");
    }

    /** True when at least one payment radio or payment block is visible (Knockout-rendered). */
    private static boolean paymentMethodsVisible(WebDriver driver) {
        try {
            for (WebElement r : driver.findElements(By.cssSelector("input[name='payment[method]']"))) {
                if (r.isDisplayed()) {
                    return true;
                }
            }
            for (WebElement t : driver.findElements(By.cssSelector(
                    ".payment-method-title, .checkout-payment-method, #checkout-payment-method-load .payment-method"))) {
                if (t.isDisplayed()) {
                    return true;
                }
            }
        } catch (StaleElementReferenceException e) {
            return false;
        }
        return false;
    }

    /** Debug-friendly count for the payment-step screenshot caption. */
    private static int countVisiblePaymentRadios(WebDriver driver) {
        int n = 0;
        for (WebElement r : driver.findElements(By.cssSelector("input[name='payment[method]']"))) {
            try {
                if (r.isDisplayed()) {
                    n++;
                }
            } catch (StaleElementReferenceException ignored) {
                // skip
            }
        }
        return n;
    }

    /** Expands {@code #block-discount} when the coupon field is hidden, applies code, waits for cart AJAX. */
    private static void applyCartDiscount(WebDriver driver, WebDriverWait wait, String couponCode) {
        wait.until(ExpectedConditions.presenceOfElementLocated(MahmattersConfig.BLOCK_DISCOUNT));
        WebElement input = driver.findElement(MahmattersConfig.COUPON_CODE_INPUT);
        if (!input.isDisplayed()) {
            WebElement toggle = wait.until(ExpectedConditions.elementToBeClickable(MahmattersConfig.BLOCK_DISCOUNT_TOGGLE));
            WebDriverUtils.safeClick(driver, toggle);
            wait.until(ExpectedConditions.visibilityOfElementLocated(MahmattersConfig.COUPON_CODE_INPUT));
            wait.until(ExpectedConditions.elementToBeClickable(MahmattersConfig.COUPON_CODE_INPUT));
        }
        WebElement couponField = wait.until(ExpectedConditions.elementToBeClickable(MahmattersConfig.COUPON_CODE_INPUT));
        couponField.clear();
        couponField.sendKeys(couponCode);
        WebElement applyBtn = wait.until(ExpectedConditions.elementToBeClickable(MahmattersConfig.APPLY_DISCOUNT_BUTTON));
        WebDriverUtils.safeClick(driver, applyBtn);
        WebDriverWait ajaxWait = new WebDriverWait(driver, Duration.ofSeconds(25));
        try {
            ajaxWait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-mask._show")));
        } catch (Exception ignored) {
            try {
                ajaxWait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-mask")));
            } catch (Exception ignored2) {
                // cart may update without mask
            }
        }
        WebDriverUtils.waitForPageFullyLoaded(driver);
    }
}
