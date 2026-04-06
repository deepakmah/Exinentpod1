package org.example;

import org.openqa.selenium.By;

/**
 * Central place to change URLs, CSS/XPath locators, and test data without touching flow logic.
 * <p>
 * Flows read from here; {@link AutomationRun} only stores per-run output paths and counters.
 */
public final class MahmattersConfig {

    private MahmattersConfig() {
    }

    // region ImgBB (screenshot hosting for the HTML report links)
    public static String imgbbApiKey = "3b23b07a37fbcee41d4984d100162a10";
    // endregion

    // region URLs (Magento storefront)
    public static final String SITE_URL = "https://mahjmatters.com/";
    public static final String SHOP_URL = "https://mahjmatters.com/shop.html";
    /** Matches storefront canonical URL (trailing slash); see https://mahjmatters.com/customer/account/login/ */
    public static final String CUSTOMER_LOGIN_URL = "https://mahjmatters.com/customer/account/login/";
    public static final String CART_PAGE_URL = "https://mahjmatters.com/checkout/cart/";
    /** [Mahj MATters Mah Jongg Mats - M20](https://mahjmatters.com/mahj-matters-mah-jongg-mats.html) */
    public static final String PDP_URL_MAH_JONGG_MATS = "https://mahjmatters.com/mahj-matters-mah-jongg-mats.html";
    /** [Shufflers (Pair) - M21](https://mahjmatters.com/mahj-matters-mah-jongg-shufflers-pair.html) */
    public static final String PDP_URL_MAH_JONGG_SHUFFLERS = "https://mahjmatters.com/mahj-matters-mah-jongg-shufflers-pair.html";
    /**
     * Bundle uses a different checkout-style UI (two option groups), not a single {@code super_attribute[188]} select.
     * [Bundle M22](https://mahjmatters.com/mahj-matters-mah-jongg-bundle.html)
     */
    public static final String PDP_URL_MAH_JONGG_BUNDLE = "https://mahjmatters.com/mahj-matters-mah-jongg-bundle.html";
    // endregion

    // region Login page (XPath/CSS aligned with recorder / TestRigor-style paths)
    public static final By LOGIN_FORM = By.cssSelector("form#login-form");
    /** If {@link #LOGIN_FORM} is missing (older themes). */
    public static final By LOGIN_FORM_FALLBACK = By.cssSelector("form#customer-login-form, form.form-login");
    /** Rel XPath: {@code //input[@id="email"]} */
    public static final By LOGIN_EMAIL_FIELD = By.xpath("//input[@id=\"email\"]");
    /** Rel XPath: {@code //input[@id="password"]} */
    public static final By LOGIN_PASSWORD_FIELD = By.xpath("//input[@id=\"password\"]");
    /** Rel CSS: {@code #show-password} (“Show Password” checkbox; optional, not required to submit). */
    public static final By LOGIN_SHOW_PASSWORD = By.cssSelector("#show-password");
    /**
     * Sign In — Magento uses {@code #send2} with inner span; TestRigor path {@code "Sign In"} covered by span text.
     */
    public static final By LOGIN_SUBMIT_BUTTON = By.xpath(
            "//button[@id='send2']"
                    + " | //form[@id='login-form']//span[normalize-space()='Sign In']/ancestor::button[1]"
                    + " | //button[.//span[normalize-space()='Sign In']]");

    public static String loginTestEmail = "deepak.maheshwari@exinent.com";
    public static String loginTestPassword = "Admin@123";
    // endregion

    // region Product detail page (configurable product + add to cart)
    public static final By DESIGN_SUPER_ATTRIBUTE_SELECT =
            By.cssSelector("select#attribute188, select[name='super_attribute[188]']");
    /**
     * PDP to open for add-to-cart (mats or shufflers share the same Design dropdown pattern).
     * Override with env {@code MAH_PDP_URL} or CLI arg (see {@link #applyCliArgs(String[])}).
     */
    public static String pdpProductUrl = PDP_URL_MAH_JONGG_MATS;
    /**
     * Magento {@code option value} for Design (e.g. {@code 736} Peony, {@code 737} Cranes, {@code 738} Ring).
     * If blank, the flow picks the first real option (skips “Choose an Option…”).
     * Override with env {@code MAH_PDP_DESIGN_VALUE} or CLI.
     */
    public static String pdpSuperAttributeValue = "";
    public static final By ADD_TO_CART_BUTTON = By.xpath(
            "//*[normalize-space(text())='Add to Cart' or @title='Add to Cart' or contains(@class,'tocart')]");
    // endregion

    // region Shopping cart (full cart page; mini-cart is separate)
    public static final By BLOCK_DISCOUNT = By.id("block-discount");
    public static final By BLOCK_DISCOUNT_TOGGLE = By.cssSelector("#block-discount .title[data-role='title']");
    public static final By COUPON_CODE_INPUT = By.id("coupon_code");
    public static final By APPLY_DISCOUNT_BUTTON = By.cssSelector("#discount-coupon-form button.action.apply.primary");
    public static final By PROCEED_TO_CHECKOUT_BUTTON = By.cssSelector("button[data-role='proceed-to-checkout']");
    public static final By CART_QTY_INPUT = By.cssSelector(
            "#shopping-cart-table input.qty, "
                    + "#shopping-cart-table input.input-text.qty, "
                    + "table.cart.items tbody input.qty, "
                    + "table.cart.data.table tbody input[name*='[qty]'], "
                    + ".cart.table-wrapper input.qty, "
                    + "form#form-validate input.qty, "
                    + "form.form-cart input.qty, "
                    + "input[data-role='cart-item-qty']");
    public static final By UPDATE_SHOPPING_CART_BUTTON = By.cssSelector(
            "button[name='update_cart_action'][value='update_qty'], "
                    + "form#form-validate button.action.update, "
                    + "form.form-cart button.action.update, "
                    + "button.update[name='update_cart_action']");
    public static final By MINICART_SHOPPING_CART_LINK = By.xpath(
            "//*[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'shopping cart')]");
    /**
     * Remove line from full cart — TestRigor path {@code "Remove item"} plus Magento 2 {@code action-delete} / {@code data-role}.
     */
    public static final By CART_REMOVE_ITEM = By.xpath(
            "//a[normalize-space()='Remove item']"
                    + " | //span[normalize-space()='Remove item']/ancestor::a[1]"
                    + " | //button[normalize-space()='Remove item']"
                    + " | //a[contains(@class,'action-delete')]"
                    + " | //a[@data-role='cart-item-remove']");
    // endregion

    // region One-page checkout (Knockout / Magento)
    /** Guest checkout only; hidden/absent when the shopper is already logged in (see {@link MahmattersFlows}). */
    public static final By CHECKOUT_EMAIL_FORM =
            By.cssSelector("form.form-login[data-role='email-with-possible-login']");
    public static final By CHECKOUT_CUSTOMER_EMAIL = By.id("customer-email");
    public static final By CHECKOUT_CUSTOMER_PASSWORD = By.id("customer-password");
    public static final By CHECKOUT_LOGIN_BUTTON = By.cssSelector("button[data-action='checkout-method-login']");
    public static final By CHECKOUT_SHIPPING_FORM = By.id("co-shipping-form");
    public static final By CHECKOUT_SHIPPING_CONTINUE = By.cssSelector(".button.action.continue.primary");
    /** Shown when carriers return quotes; wait helps before the “address filled” screenshot. */
    public static final By CHECKOUT_SHIPPING_METHOD_RADIO = By.cssSelector("input[name='shipping_method']");
    /** Messages to include when shipping Next is missing (validation / API errors). */
    public static final By CHECKOUT_SHIPPING_STEP_ERRORS = By.cssSelector(
            ".message-error, .messages .message-error, #shipping .message.error, .field-error .message");
    /**
     * Max seconds to wait for shipping rates ({@link #CHECKOUT_SHIPPING_METHOD_RADIO}) after address entry,
     * before validating the Continue/Next button and taking {@code checkout_shipping_address_filled}.
     */
    public static int checkoutShippingRatesWaitSeconds = 40;
    /** Extra time to wait for the shipping Continue button to become visible and enabled. */
    public static int checkoutShippingContinueWaitSeconds = 25;
    // endregion

    // region Test data (edit here or pass CLI args where supported)
    public static String cartCouponCode = "exitest";
    public static String checkoutGuestEmail = "automation.test@example.com";
    public static String shipFirstName = "Automation";
    public static String shipLastName = "Test";
    public static String shipCompany = "";
    public static String shipStreetLine1 = "123 Test Street";
    public static String shipCountryId = "US";
    /** Magento region_id for California in default US list (numeric string). */
    public static String shipRegionId = "12";
    public static String shipCity = "Calistoga";
    public static String shipPostcode = "94515";
    public static String shipTelephone = "7075551234";
    // endregion

    /**
     * {@code args[0]} → {@link #cartCouponCode}; {@code args[1]} → {@link #checkoutGuestEmail};
     * {@code args[2]} → {@link #pdpProductUrl}; {@code args[3]} → {@link #pdpSuperAttributeValue}.
     * Login still uses {@link #loginTestEmail} / env {@code MAH_LOGIN_EMAIL} (see {@link MahmattersFlows#checkLogin}).
     */
    public static void applyCliArgs(String[] args) {
        if (args != null && args.length > 0 && !args[0].isBlank()) {
            cartCouponCode = args[0].trim();
        }
        if (args != null && args.length > 1 && !args[1].isBlank()) {
            checkoutGuestEmail = args[1].trim();
        }
        if (args != null && args.length > 2 && !args[2].isBlank()) {
            pdpProductUrl = args[2].trim();
        }
        if (args != null && args.length > 3 && !args[3].isBlank()) {
            pdpSuperAttributeValue = args[3].trim();
        }
    }
}
