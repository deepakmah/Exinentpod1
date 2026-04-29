/**
 * <h2>National Allergy — automated browser test (human guide)</h2>
 *
 * <p><b>What this project does in one sentence:</b> opens a real Chrome window, walks through shopping like a
 * customer (products from the sitemap → cart → coupon → checkout), optionally logs in after checkout, then tries
 * search on the home page — and saves screenshots plus a simple HTML report.</p>
 *
 * <h3>Order to read the code (easiest path)</h3>
 * <ol>
 *   <li>{@link org.example.natallergy.NatallergyTest} — three hooks: start browser + load sitemap list, run the story, close browser.</li>
 *   <li>{@link org.example.natallergy.NatallergyAccountWebTests} — smaller suite: invalid/valid login, dashboard nav, logout, re-login.</li>
 *   <li>{@link org.example.natallergy.NatallergyCartWebTests} — remove coupon; cart line items before logout.</li>
 *   <li>{@link org.example.natallergy.NatallergyFlow} — the whole story in one {@code run(...)} method (read top to bottom).</li>
 *   <li>{@link org.example.natallergy.NatallergyPageActions} — reusable “how to click / type / wait” for Magento pages.</li>
 *   <li>{@link org.example.natallergy.NatallergyConfig} — all addresses and toggles (many can be set with {@code -Dname=value}).</li>
 *   <li>{@link org.example.natallergy.NatallergyReporting} — every “picture + CSV + HTML line” goes through here.</li>
 * </ol>
 *
 * <h3>Other files (supporting roles)</h3>
 * <ul>
 *   <li>{@link org.example.natallergy.NatallergySitemap} — downloads the XML sitemap and builds the list of product page links.</li>
 *   <li>{@link org.example.natallergy.NatallergyScreenshotCapture} — actually grabs the PNG (full page in Chrome).</li>
 *   <li>{@link org.example.natallergy.NatallergyDriverFactory} — starts Chrome + driver.</li>
 *   <li>{@link org.example.natallergy.NatallergySuiteListener} — reads {@code testng.xml} {@code <parameter>} entries into Java
 *       system properties (so you do not need long {@code -D} lists for every run).</li>
 * </ul>
 *
 * <h3>Story at a glance</h3>
 * <pre>
 *   [Setup]  Open site → download sitemap → list of product URLs
 *            ↓
 *   [Flow]   Visit random products → add to cart → open cart → change qty → coupon → checkout
 *            → guest email + shipping → continue to payment screen
 *            → (optional) empty cart (Clear-all + OK when possible, else line delete) → log in → dashboard → home → search
 *   [Teardown] Close browser
 * </pre>
 *
 * <p><b>Optional login after checkout:</b> set {@code natallergy.login.enabled=true} in {@code testng.xml} or with
 * {@code -Dnatallergy.login.enabled=true}. Default is off so runs stay as guest checkout only.</p>
 *
 * <p><b>Skip guest checkout / shipping in E2E:</b> {@code natallergy.checkout.shipping.enabled=false} skips that block and
 * leaves the browser on the cart page until Phase D (one {@code GET /} when login is off, for search).</p>
 *
 * <p><b>Logout:</b> {@link org.example.natallergy.NatallergyPageActions#performCustomerLogout(org.openqa.selenium.WebDriver)}
 * uses {@link org.example.natallergy.NatallergyPageActions#emptyShoppingCartBestEffort} (Clear-all UI + line-delete fallback), then logout.</p>
 *
 * <p><b>Full QA wish list vs current automation:</b> {@link NatallergyQaCoverageBacklog} (inventory only; not runnable).</p>
 */
package org.example.natallergy;
