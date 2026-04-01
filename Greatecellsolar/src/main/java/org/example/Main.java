package org.example;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.util.List;

/**
 * Entry point for Great Cell Solar storefront automation.
 *
 * <p><b>High-level flow</b>
 * <ol>
 *   <li>Open homepage → wait → screenshot</li>
 *   <li>Load all store URLs from Magento sitemap (see {@link SitemapService})</li>
 *   <li>Pick a random <i>non-empty</i> category (skips empty ones with error screenshots)</li>
 *   <li>Add {@link GreatCellConfig#randomProductsToAddToCart()} distinct products: first from category
 *       grid links, then sitemap if needed → minicart → full cart → update line qty</li>
 * </ol>
 *
 * <p>Supporting classes: {@link GreatCellConfig} (paths, timing), {@link StorefrontFlow} (browser steps),
 * {@link ScreenshotReporter} (PNG + HTML + CSV), {@link BrowserWaits} (ready-state waits).
 */
public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        ChromeOptions options = new ChromeOptions();
        WebDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();

        // --- Step 1: Homepage ---
        driver.get(GreatCellConfig.SITE_ORIGIN + "/");
        BrowserWaits.waitForPageFullyLoaded(driver);
        BrowserWaits.waitForHomeContentReady(driver);
        ScreenshotReporter.takeFullPageScreenshot(driver, "homepage");

        // --- Step 2: Sitemap → list of pages (categories + products) ---
        List<SitemapPage> pages = SitemapService.loadStorePages(GreatCellConfig.SITEMAP_URL);
        if (pages.isEmpty()) {
            throw new IllegalStateException("No page URLs parsed from sitemap: " + GreatCellConfig.SITEMAP_URL);
        }

        // --- Step 3: How many PDPs to add (2 or 3, from config) ---
        int productsToAdd = GreatCellConfig.randomProductsToAddToCart();
        System.out.println("Target distinct products to add to cart: " + productsToAdd);

        // --- Step 4: Category with products (retry empty categories) ---
        String categoryUrl = StorefrontFlow.resolveNonEmptyCategoryUrl(driver, pages);

        // --- Step 5: Add products + open cart + update qty ---
        StorefrontFlow.addDynamicProductsAndCompleteCartFlow(driver, pages, categoryUrl, productsToAdd);

        driver.quit();
    }
}
