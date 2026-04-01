package org.example;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.function.Function;

/**
 * Shared wait helpers so pages finish loading before we click or screenshot.
 *
 * <ul>
 *   <li>{@link #waitForPageFullyLoaded} — document.readyState + jQuery.ajax idle (if jQuery exists)</li>
 *   <li>{@link #waitForHomeContentReady} — scroll + fonts/jQuery optional waits (homepage only)</li>
 *   <li>{@link #waitOptional} — never fails the run; logs a warning if condition times out</li>
 * </ul>
 */
public final class BrowserWaits {

    private BrowserWaits() {}

    /**
     * Blocks until the HTML document is {@code complete} and any in-flight jQuery requests finish.
     * Safe on sites without jQuery (second check returns true immediately).
     */
    public static void waitForPageFullyLoaded(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(d -> "complete".equals(
                ((JavascriptExecutor) d).executeScript("return document.readyState")));
        wait.until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                "return typeof jQuery === 'undefined' || jQuery.active === 0")));
    }

    /**
     * Extra settling for the homepage: scroll to trigger lazy sections, then best-effort font / jQuery waits.
     * Used once after first navigation in {@link Main}.
     */
    public static void waitForHomeContentReady(WebDriver driver) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
        Thread.sleep(2_000);
        js.executeScript("window.scrollTo(0, 0);");
        Thread.sleep(1_000);

        waitOptional(driver, Duration.ofSeconds(10),
                d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                        "return !document.fonts || document.fonts.status === 'loaded';")),
                "web fonts");
        waitOptional(driver, Duration.ofSeconds(15),
                d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                        "return typeof jQuery === 'undefined' || jQuery.active === 0")),
                "jQuery idle");

        Thread.sleep(4_000);
    }

    /**
     * Tries to satisfy {@code condition} within {@code timeout}. On timeout, prints a warning and continues
     * (does not throw). Use for nice-to-have checks (lazy images, optional panels).
     */
    public static void waitOptional(
            WebDriver driver, Duration timeout, Function<WebDriver, Boolean> condition, String label) {
        try {
            new WebDriverWait(driver, timeout).until(condition::apply);
        } catch (TimeoutException e) {
            System.out.println("Warning: optional wait for " + label + " timed out — continuing.");
        }
    }
}
