package org.example;

import java.io.IOException;

import org.openqa.selenium.WebDriver;

/**
 * Entry point for Mahj MATters UI automation.
 * <p>
 * Structure:
 * <ul>
 *   <li>{@link MahmattersConfig} — URLs, locators, test data</li>
 *   <li>{@link AutomationRun} — this run’s folders and report counters</li>
 *   <li>{@link WebDriverUtils} — waits, clicks, scroll, small DOM helpers</li>
 *   <li>{@link TestReporter} — screenshots, ImgBB, CSV, HTML report</li>
 *   <li>{@link MahmattersFlows} — login + PDP → cart → checkout flows</li>
 * </ul>
 * CLI: {@code java Main [coupon] [checkoutEmail] [pdpUrl] [designOptionValue]} — see {@link MahmattersConfig#applyCliArgs(String[])}.
 */
public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        // Optional: args[0] coupon, [1] checkout email, [2] PDP URL, [3] Design option value (736/737/738 or blank=auto)
        MahmattersConfig.applyCliArgs(args);

        // One folder per run under Documents\Automation\Mahmatters (screenshots + html + shared CSV)
        AutomationRun run = AutomationRun.create();

        WebDriver driver = WebDriverUtils.createChromeDriver();
        try {
            driver.manage().window().maximize();

            // --- 1) Storefront entry + baseline screenshot ---
            driver.get(MahmattersConfig.SITE_URL);
            WebDriverUtils.waitForPageFullyLoaded(driver);
            TestReporter.takeScreenshot(driver, run, "homepage");

            // --- 2) Optional early login (isolated). Login is verified again on the cart before checkout in the flow. ---
            try {
                MahmattersFlows.checkLogin(driver, run);
            } catch (Exception e) {
                TestReporter.takeScreenshot(driver, run, "login_check_failed", false,
                        "Login check failed: " + e.getMessage());
            }

            // --- 3) PDP (configurable) → cart → pre-checkout login → checkout → payment ---
            MahmattersFlows.runShoppingAndCheckout(driver, run);
        } finally {
            driver.quit();
        }
    }
}
