package org.example.natallergy;

import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

/**
 * <p>Cart-focused checks: {@linkplain #removeCoupon_restoresStateAfterCancel coupon remove} and
 * {@linkplain #loggedIn_cartNotEmpty_beforeLogout line items after add, then logout (which clears the cart first)}.</p>
 */
@Listeners(NatallergySuiteListener.class)
public class NatallergyCartWebTests {

    private WebDriver driver;
    private List<String> productUrls;

    @BeforeClass(alwaysRun = true)
    public void setUp() throws IOException, InterruptedException {
        driver = NatallergyDriverFactory.createChromeDriver();
        driver.manage().window().maximize();
        driver.get(NatallergyConfig.SITE_BASE + "/");
        NatallergyPageActions.waitForPageFullyLoaded(driver);
        System.out.println("[Natallergy] Cart web tests: resolving product URLs from sitemap (may take a while)…");
        productUrls = NatallergySitemap.discoverProductUrlsFromSitemap();
        if (productUrls == null || productUrls.isEmpty()) {
            throw new SkipException("No product URLs from sitemap — cannot run cart tests.");
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    private void requireLoginConfigured() {
        if (NatallergyConfig.LOGIN_EMAIL == null || NatallergyConfig.LOGIN_EMAIL.isBlank()) {
            throw new SkipException("Set -Dnatallergy.login.email for logged-in cart test.");
        }
        if (NatallergyConfig.LOGIN_PASSWORD == null || NatallergyConfig.LOGIN_PASSWORD.isBlank()) {
            throw new SkipException("Set -Dnatallergy.login.password for logged-in cart test.");
        }
    }

    @Test(priority = 10, description = "Apply configured cart coupon, then remove it; line items must remain")
    public void removeCoupon_restoresStateAfterCancel() throws Exception {
        Assert.assertTrue(NatallergyFlow.addFirstSalableProductFromUrls(driver, productUrls, 30),
                "Could not add any salable product from sitemap for coupon test.");
        NatallergyPageActions.openShoppingCartPageFromMiniCart(driver);
        Assert.assertTrue(NatallergyPageActions.countCartLineItems(driver) >= 1,
                "Cart should list at least one line before applying a coupon.");
        NatallergyPageActions.applyDiscountCouponOnCartPage(driver, NatallergyConfig.CART_COUPON_CODE);
        NatallergyReporting.takeScreenshot(driver, "cart_coupon_applied_before_remove", true,
                "Cart after Apply Discount (" + NatallergyConfig.CART_COUPON_CODE + ")", driver.getCurrentUrl());
        Assert.assertTrue(NatallergyPageActions.isCartCancelCouponControlPresent(driver),
                "Expected Cancel/Remove coupon control after apply. Coupon=" + NatallergyConfig.CART_COUPON_CODE
                        + " — check it exists on this store.");
        NatallergyPageActions.removeAppliedCouponOnCartPageIfPresent(driver);
        NatallergyReporting.takeScreenshot(driver, "cart_after_remove_coupon", true,
                "Cart after coupon remove/cancel", driver.getCurrentUrl());
        Assert.assertFalse(NatallergyPageActions.isCartCancelCouponControlPresent(driver),
                "Cancel/remove coupon control should be gone after removal.");
        Assert.assertTrue(NatallergyPageActions.countCartLineItems(driver) >= 1,
                "Cart line items should still be present after coupon removal.");
    }

    @Test(priority = 20, description = "Logged-in cart shows line(s) after add; cart page is cleared; then logout")
    public void loggedIn_cartNotEmpty_beforeLogout() throws Exception {
        requireLoginConfigured();
        NatallergyPageActions.openLoginPageAndSubmitConfiguredCredentials(driver);
        Assert.assertTrue(NatallergyFlow.addFirstSalableProductFromUrls(driver, productUrls, 30),
                "Could not add any salable product after login.");
        NatallergyPageActions.openShoppingCartPageFromMiniCart(driver);
        int lines = NatallergyPageActions.countCartLineItems(driver);
        Assert.assertTrue(lines >= 1, "Cart should show at least one line before logout; lines=" + lines);
        NatallergyReporting.takeScreenshot(driver, "cart_lines_before_logout", true,
                "Regression: cart must not be cleared before logout (expect ≥1 line)", driver.getCurrentUrl());
        Assert.assertTrue(NatallergyPageActions.countCartLineItems(driver) >= 1,
                "Cart unexpectedly had no line items before clear/logout.");
        NatallergyPageActions.performCustomerLogout(driver);
    }
}
