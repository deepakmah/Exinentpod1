package org.example.natallergy;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * <p><b>One place for “settings”</b> — store URLs, login credentials, report folders, and limits. Most strings can be
 * overridden from the outside with Java {@code -Dname=value} (Maven/IDE VM options) or with {@code testng.xml}
 * parameters that {@link NatallergySuiteListener} copies into system properties.</p>
 *
 * <p>Think of this file as the “remote control labels”: change here (or via {@code -D}) instead of hunting through
 * the flow code for magic strings.</p>
 */
public final class NatallergyConfig {

    // --- When this JVM started the test (used in folder names and the HTML footer) ---
    public static final String RUN_DATE = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    public static final String RUN_TIME = new SimpleDateFormat("HH-mm-ss").format(new Date());
    public static final String START_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

    // --- Where screenshots, HTML report, and CSV are written (Windows paths; adjust if you move machines) ---
    public static final String SS_DIR =
            "C:\\Users\\deepa\\Documents\\Automation\\Natallergy\\screenshots\\" + RUN_DATE + "\\" + RUN_TIME;
    public static final String HTML_DIR =
            "C:\\Users\\deepa\\Documents\\Automation\\Natallergy\\html\\" + RUN_DATE + "\\" + RUN_TIME;
    public static final String CSV_PATH = "C:\\Users\\deepa\\Documents\\Automation\\Natallergy\\Natallergy.csv";

    // --- Which website we automate (no trailing slash) ---
    /**
     * Storefront used for browsing, checkout, and rewriting sitemap {@code loc} hosts when they differ (e.g. multi-store).
     */
    public static final String SITE_BASE = System.getProperty("natallergy.site.base", "https://natlallergy-dev.99stockpics.com")
            .replaceAll("/+$", "");

    /**
     * When true, {@link NatallergyFlow} runs {@link NatallergyPageActions#emptyShoppingCartBestEffort} (Clear-all UI + OK
     * when the modal matches; line-delete fallback), then {@link NatallergyPageActions#performCustomerLogin}.
     * Read at runtime (not a static constant) so {@code testng.xml} {@code <parameter>} values applied in {@code @BeforeSuite} take effect.
     * JVM wins if already set: {@code -Dnatallergy.login.enabled=false}.
     */
    public static boolean isLoginEnabled() {
        return Boolean.parseBoolean(System.getProperty("natallergy.login.enabled", "false"));
    }

    /**
     * When {@code false}, {@link NatallergyFlow} skips guest checkout (email, shipping form, continue to payment) so the
     * HTML report never records that “shipping / checkout” chapter (no {@code checkout_after_shipping_continue} step).
     * JVM or {@code testng.xml}: {@code natallergy.checkout.shipping.enabled=false}. Default {@code true}.
     */
    public static boolean isCheckoutShippingStepEnabled() {
        return Boolean.parseBoolean(System.getProperty("natallergy.checkout.shipping.enabled", "true"));
    }

    // --- Customer account (login page + dashboard after sign-in) ---
    public static final String CUSTOMER_LOGIN_URL = System.getProperty(
            "natallergy.login.url",
            SITE_BASE + "/customer/account/login/");

    /** Magento logged-in account landing (dashboard). Override with {@code -Dnatallergy.account.dashboard.url=...}. */
    public static final String CUSTOMER_ACCOUNT_DASHBOARD_URL = System.getProperty(
            "natallergy.account.dashboard.url",
            SITE_BASE + "/customer/account/");

    public static final String LOGIN_EMAIL = System.getProperty("natallergy.login.email", "deepak123@gmail.com");
    public static final String LOGIN_PASSWORD = System.getProperty("natallergy.login.password", "Admin@123");

    // --- Sitemap discovery (used only during setup, not in the browser) ---
    public static final String SITEMAP_URL = System.getProperty(
            "natallergy.sitemap.url",
            SITE_BASE + "/pub/sitemap.xml");

    public static final Pattern SITEMAP_LOC_PATTERN =
            Pattern.compile("<loc>\\s*([^<]+?)\\s*</loc>", Pattern.CASE_INSENSITIVE);

    // --- Cart building: how long to wait for stock UI, how many PDPs to try, how many lines before checkout ---
    public static final int MAX_SITEMAP_PRODUCT_TRIES = 50;
    public static final Duration ADD_TO_CART_STOCK_WAIT = Duration.ofSeconds(28);
    public static final int MIN_PRODUCTS_BEFORE_CHECKOUT = 3;
    public static final int MAX_PRODUCTS_BEFORE_CHECKOUT = 4;

    /** Cart coupon used in {@link NatallergyFlow} and cart behaviour tests. Override with {@code -Dnatallergy.cart.coupon=...}. */
    public static final String CART_COUPON_CODE = System.getProperty("natallergy.cart.coupon", "exitest");

    private NatallergyConfig() {
    }
}
