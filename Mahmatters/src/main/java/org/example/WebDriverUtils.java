package org.example;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Small Selenium utilities used by {@link MahmattersFlows} and {@link Main}.
 * Includes {@link #createChromeDriver()} (password-manager popups) plus waits, clicks, and cart helpers.
 */
public final class WebDriverUtils {

    private WebDriverUtils() {
    }

    /**
     * Chrome with password-manager UI suppressed so the native “Change your password” / breach dialog
     * (not part of the page DOM) does not block automation after login.
     */
    public static WebDriver createChromeDriver() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);

        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("prefs", prefs);
        options.addArguments(
                "--disable-save-password-bubble",
                "--disable-features=PasswordCheck,PasswordManagerOnboarding");

        return new ChromeDriver(options);
    }

    /** Waits for {@code document.readyState == complete} (up to 45s). */
    public static void waitForPageFullyLoaded(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(45));
        wait.until(webDriver -> "complete".equals(
                ((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
    }

    /** For env vs default strings (e.g. login email). */
    public static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback != null ? fallback : "";
    }

    /**
     * Magento often renders multiple matching nodes (hidden templates). Returns the first that is
     * actually visible and enabled, or {@code null} after the wait times out.
     */
    public static WebElement firstDisplayedMatching(WebDriver driver, WebDriverWait wait, By locator) {
        try {
            return wait.until(webDriver -> {
                for (WebElement el : webDriver.findElements(locator)) {
                    try {
                        if (el.isDisplayed() && el.isEnabled()) {
                            return el;
                        }
                    } catch (StaleElementReferenceException e) {
                        // next
                    }
                }
                return null;
            });
        } catch (TimeoutException e) {
            return null;
        }
    }

    /** Qty field on the full cart page (not mini-cart drawer). */
    public static WebElement firstDisplayedCartQty(WebDriver driver, WebDriverWait wait) {
        return firstDisplayedMatching(driver, wait, MahmattersConfig.CART_QTY_INPUT);
    }

    /** Normal click first; if intercepted (overlays), fall back to JS click. */
    public static void safeClick(WebDriver driver, WebElement element) {
        try {
            element.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    /** Waits until {@code locator} is clickable, then {@link #safeClick(WebDriver, WebElement)}. */
    public static void safeClick(WebDriver driver, WebDriverWait wait, By locator) {
        safeClick(driver, wait.until(ExpectedConditions.elementToBeClickable(locator)));
    }

    /** Centers element in the viewport before click/type (reduces “not clickable” errors). */
    public static void scrollIntoView(WebDriver driver, WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", el);
    }

    /**
     * Removes prefilled/autocomplete text on Magento checkout (Knockout-bound inputs). Plain {@link WebElement#clear()}
     * often leaves the UI model out of sync; this uses JS + select-all-delete then types {@code text}.
     */
    public static void clearAndType(WebDriver driver, WebElement el, String text) {
        scrollIntoView(driver, el);
        safeClick(driver, el);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript(
                "var e = arguments[0]; e.focus(); e.value = '';"
                        + "e.dispatchEvent(new Event('input', {bubbles: true}));"
                        + "e.dispatchEvent(new Event('change', {bubbles: true}));",
                el);
        el.clear();
        Keys mod = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
                ? Keys.COMMAND : Keys.CONTROL;
        el.sendKeys(Keys.chord(mod, "a"), Keys.DELETE);
        if (text != null && !text.isEmpty()) {
            el.sendKeys(text);
        }
    }

    /**
     * Checkout shipping fields are Knockout-bound; TAB helps trigger validation after typing.
     * {@code fieldName} is the HTML {@code name} (e.g. {@code street[0]}).
     */
    public static void setShippingInput(WebDriver driver, WebElement form, String fieldName, String value) {
        WebElement el = form.findElement(By.name(fieldName));
        clearAndType(driver, el, value != null ? value : "");
        el.sendKeys(Keys.TAB);
    }
}
