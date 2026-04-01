package org.example;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Central place for <b>site URLs</b>, <b>output folders</b>, <b>run timestamps</b>, and <b>tuning</b>
 * (cart size, screenshot delay). Change paths here if you move the machine or output root.
 */
public final class GreatCellConfig {

    private GreatCellConfig() {}

    // -------------------------------------------------------------------------
    // Cart: how many different products to add per run (random integer in [min, max])
    // -------------------------------------------------------------------------

    /** Minimum distinct PDPs added to cart in one run. */
    public static final int MIN_PRODUCTS_IN_CART = 2;
    /** Maximum distinct PDPs added to cart in one run. */
    public static final int MAX_PRODUCTS_IN_CART = 3;

    /** Picks {@value MIN_PRODUCTS_IN_CART} or {@value MAX_PRODUCTS_IN_CART} for this execution. */
    public static int randomProductsToAddToCart() {
        return ThreadLocalRandom.current().nextInt(MIN_PRODUCTS_IN_CART, MAX_PRODUCTS_IN_CART + 1);
    }

    /**
     * Called immediately before “after add to cart” and “minicart open” screenshots so overlays/toasts
     * finish animating (~6 s, small random jitter). Tweak {@code nextInt} bounds to change duration.
     */
    public static void sleepBeforeAddToCartScreenshot() throws InterruptedException {
        int ms = ThreadLocalRandom.current().nextInt(5500, 6501);
        Thread.sleep(ms);
    }

    // -------------------------------------------------------------------------
    // Store URLs
    // -------------------------------------------------------------------------

    public static final String SITE_ORIGIN = "https://www.greatcellsolarmaterials.com";
    /** Magento media sitemap index; child XMLs list categories and products. */
    public static final String SITEMAP_URL = SITE_ORIGIN + "/pub/media/sitemap.xml";

    // -------------------------------------------------------------------------
    // Run identity (fixed when this class loads — one folder per JVM run)
    // -------------------------------------------------------------------------

    public static final String RUN_DATE = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    public static final String RUN_TIME = new SimpleDateFormat("HH-mm-ss").format(new Date());
    public static final String START_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

    // -------------------------------------------------------------------------
    // Output paths (screenshots + HTML report + CSV log)
    // -------------------------------------------------------------------------

    public static final String SS_DIR =
            "C:\\Users\\deepa\\Documents\\Automation\\GreatCellSolar\\screenshots\\" + RUN_DATE + "\\" + RUN_TIME;
    public static final String HTML_DIR =
            "C:\\Users\\deepa\\Documents\\Automation\\GreatCellSolar\\html\\" + RUN_DATE + "\\" + RUN_TIME;
    public static final String CSV_PATH =
            "C:\\Users\\deepa\\Documents\\Automation\\GreatCellSolar\\GreatCellSolar.csv";
}
