package org.example.natallergy;

import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.testng.TestNG;

import java.io.IOException;
import java.util.List;

/**
 * <p><b>For a human reader:</b> this is the smallest class on purpose. TestNG only cares about three things here —
 * what runs before all tests ({@code @BeforeClass}), the one big test ({@code @Test}), and cleanup ({@code @AfterClass}).</p>
 *
 * <p>The real “script” of what the shopper does is {@link NatallergyFlow#run}; this class just prepares data
 * (product URL list from the sitemap) and passes the open browser into that method.</p>
 *
 * @see org.example.natallergy Package overview
 */
@Listeners(NatallergySuiteListener.class)
public class NatallergyTest {

    /** Browser session for this test class. */
    private WebDriver driver;
    /** Product page URLs discovered once in {@code @BeforeClass}; consumed by {@link NatallergyFlow}. */
    private List<String> catalogProductUrls;

    /**
     * Optional entry point for the IDE: forwards {@code args[0]} to system property {@code sitemapSeed} (shuffle seed),
     * then runs this class through TestNG (same as {@code mvn test}).
     */
    public static void main(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            System.setProperty("sitemapSeed", args[0].trim());
        }
        TestNG testng = new TestNG();
        testng.addListener(new NatallergySuiteListener());
        testng.setTestClasses(new Class<?>[]{NatallergyTest.class});
        testng.run();
    }

    /**
     * Runs once before {@link #natAllergyEndToEnd}: (1) start Chrome, (2) open the shop home, (3) build the product URL
     * list from the sitemap (can take a minute — browser stays on home), (4) first screenshot for the report.
     */
    @BeforeClass(alwaysRun = true)
    public void setUpBrowser() throws IOException, InterruptedException {
        // 1) Visible Chrome window
        driver = NatallergyDriverFactory.createChromeDriver();
        driver.manage().window().maximize();

        // 2) Land on storefront so cookies / session are normal for a shopper
        System.out.println("[Natallergy] Chrome should be visible. Loading storefront: " + NatallergyConfig.SITE_BASE + "/");
        driver.get(NatallergyConfig.SITE_BASE + "/");
        NatallergyPageActions.waitForPageFullyLoaded(driver);

        // 3) Sitemap is fetched over HTTP (not by clicking); keep user informed because it can be slow
        System.out.println("[Natallergy] Downloading & parsing sitemap.xml (often 30–120 s, ~1 MB). "
                + "The browser may sit on the homepage until this finishes — this is normal.");
        catalogProductUrls = NatallergySitemap.discoverProductUrlsFromSitemap();
        if (catalogProductUrls.isEmpty()) {
            throw new IllegalStateException("No product URLs matched filters from " + NatallergyConfig.SITEMAP_URL);
        }
        System.out.println("[Natallergy] Sitemap ready: " + catalogProductUrls.size()
                + " product URLs. Capturing homepage screenshot…");

        // 4) Proof in the report that setup reached the home page
        NatallergyReporting.takeScreenshot(driver, "homepage", true, "Homepage after sitemap discovery",
                driver.getCurrentUrl());
    }

    /** One end-to-end path: cart build → checkout → optional login → search (see {@link NatallergyFlow}). */
    @Test(description = "Sitemap-driven cart (3–4 products), coupon, checkout through shipping, home search")
    public void natAllergyEndToEnd() throws IOException, InterruptedException {
        String seed = System.getProperty("sitemapSeed", "");
        String[] testArgs = seed.isBlank() ? new String[0] : new String[]{seed};
        NatallergyFlow.run(driver, testArgs, catalogProductUrls);
    }

    /** Always closes Chrome, even if a test failed (so no stray browser windows). */
    @AfterClass(alwaysRun = true)
    public void tearDownBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }
}
