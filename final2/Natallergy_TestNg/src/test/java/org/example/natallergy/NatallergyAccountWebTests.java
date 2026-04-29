package org.example.natallergy;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.io.IOException;

/**
 * <p>Focused assertions for customer account behaviour: invalid login, valid login, dashboard chrome, logout, and
 * re-login. Each step records a full-page PNG plus CSV/HTML rows via {@link NatallergyReporting#takeScreenshot}.</p>
 *
 * <p>Run via the same TestNG suite as {@link NatallergyTest} (see {@code src/test/resources/testng.xml}) or run this
 * class alone in the IDE — {@link NatallergySuiteListener} still applies suite parameters.</p>
 */
@Listeners(NatallergySuiteListener.class)
public class NatallergyAccountWebTests {

    private WebDriver driver;

    @BeforeClass(alwaysRun = true)
    public void startBrowser() throws IOException {
        driver = NatallergyDriverFactory.createChromeDriver();
        driver.manage().window().maximize();
        driver.get(NatallergyConfig.SITE_BASE + "/");
        NatallergyPageActions.waitForPageFullyLoaded(driver);
        NatallergyReporting.takeScreenshot(driver, "account_suite_storefront_home", true,
                "Account web tests — storefront before login steps", driver.getCurrentUrl());
    }

    @AfterClass(alwaysRun = true)
    public void closeBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    private void requireLoginConfigured() {
        if (NatallergyConfig.LOGIN_EMAIL == null || NatallergyConfig.LOGIN_EMAIL.isBlank()) {
            throw new SkipException("Set -Dnatallergy.login.email (or config) for account tests.");
        }
        if (NatallergyConfig.LOGIN_PASSWORD == null || NatallergyConfig.LOGIN_PASSWORD.isBlank()) {
            throw new SkipException("Set -Dnatallergy.login.password (or config) for account tests.");
        }
    }

    @Test(description = "Wrong password should not leave the login page (or should show an error banner)")
    public void invalidPassword_keepsUserOnLoginOrShowsError() throws IOException, InterruptedException {
        requireLoginConfigured();
        NatallergyPageActions.navigateToCustomerLogin(driver);
        NatallergyPageActions.submitCustomerLoginForm(driver, NatallergyConfig.LOGIN_EMAIL,
                NatallergyConfig.LOGIN_PASSWORD + "__wrong__");
        NatallergyPageActions.waitForLoginFailureSettled(driver);
        NatallergyReporting.takeScreenshot(driver, "account_invalid_password", true,
                "After wrong password — expect error banner and/or stay on login", driver.getCurrentUrl());
        boolean stillOnLogin = NatallergyPageActions.isLikelyOnLoginPage(driver);
        boolean error = NatallergyPageActions.hasLoginErrorBanner(driver);
        Assert.assertTrue(stillOnLogin || error,
                "Expected to remain on login or see an error; url=" + driver.getCurrentUrl());
    }

    @Test(dependsOnMethods = "invalidPassword_keepsUserOnLoginOrShowsError",
            description = "Configured credentials leave /customer/account/login")
    public void validPassword_leavesLoginUrl() throws IOException, InterruptedException {
        requireLoginConfigured();
        NatallergyPageActions.openLoginPageAndSubmitConfiguredCredentials(driver);
        NatallergyReporting.takeScreenshot(driver, "account_valid_password_post_login", true,
                "After valid credentials submit (before URL assertion)", driver.getCurrentUrl());
        String url = driver.getCurrentUrl();
        Assert.assertNotNull(url);
        Assert.assertFalse(url.toLowerCase().contains("/customer/account/login"),
                "Still on login after valid submit: " + url);
    }

    @Test(dependsOnMethods = "validPassword_leavesLoginUrl",
            description = "Account area shows typical Magento sidebar / nav links")
    public void accountDashboard_exposesNavigation() throws IOException {
        driver.get(NatallergyConfig.CUSTOMER_ACCOUNT_DASHBOARD_URL);
        NatallergyPageActions.waitForPageFullyLoaded(driver);
        NatallergyReporting.takeScreenshot(driver, "account_dashboard_nav", true,
                "Customer dashboard (before nav assertion)", driver.getCurrentUrl());
        boolean hasNav = driver.findElements(By.cssSelector(".nav.items a, .block-collapsible-nav a")).stream()
                .anyMatch(e -> {
                    try {
                        return e.isDisplayed();
                    } catch (Exception ex) {
                        return false;
                    }
                });
        Assert.assertTrue(hasNav, "No visible account nav links on " + driver.getCurrentUrl());
    }

    @Test(dependsOnMethods = "accountDashboard_exposesNavigation",
            description = "Logout route clears session; home shows Sign In again")
    public void customerLogout_guestHeaderShowsSignIn() throws IOException {
        NatallergyPageActions.performCustomerLogout(driver);
        driver.get(NatallergyConfig.SITE_BASE + "/");
        NatallergyPageActions.waitForPageFullyLoaded(driver);
        NatallergyReporting.takeScreenshot(driver, "account_after_logout_home", true,
                "Storefront home after logout (before Sign In assertion)", driver.getCurrentUrl());
        boolean signIn = driver.findElements(By.cssSelector("a[href*='customer/account/login']")).stream()
                .anyMatch(e -> {
                    try {
                        return e.isDisplayed();
                    } catch (Exception ex) {
                        return false;
                    }
                });
        Assert.assertTrue(signIn, "Expected a visible Sign In link after logout on storefront home.");
    }

    @Test(dependsOnMethods = "customerLogout_guestHeaderShowsSignIn",
            description = "Sign-in again after explicit logout")
    public void loginAgain_afterLogout_succeeds() throws IOException, InterruptedException {
        requireLoginConfigured();
        NatallergyPageActions.openLoginPageAndSubmitConfiguredCredentials(driver);
        NatallergyReporting.takeScreenshot(driver, "account_relogin_after_logout", true,
                "After second login submit (before URL assertion)", driver.getCurrentUrl());
        Assert.assertFalse(NatallergyPageActions.isLikelyOnLoginPage(driver),
                "Re-login failed; url=" + driver.getCurrentUrl());
    }
}
