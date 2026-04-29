package org.example.natallergy;

/**
 * <h1>QA coverage backlog (requirements inventory)</h1>
 *
 * <p>This class is <b>not</b> executed. It exists so product, QA, and engineering share one checklist. Implement real
 * checks as new {@code @Test} methods (or new classes) and link them here in Javadoc when done.</p>
 *
 * <h2>What is automated today</h2>
 * <ul>
 *   <li><b>Account smoke ({@link NatallergyAccountWebTests}):</b> wrong password stays on login or shows error; valid login leaves login URL;
 *       dashboard shows account nav; logout via Magento route then Sign In on home; sign in again — with {@link NatallergyReporting} screenshots at each step.</li>
 *   <li><b>Guest cart ({@link NatallergyTest} → {@link NatallergyFlow}):</b> random PDPs from {@link NatallergySitemap},
 *       configurable options, OOS skip, add-to-cart.</li>
 *   <li><b>Cart page:</b> open cart, change qty, apply coupon {@code exitest}, screenshot evidence.</li>
 *   <li><b>Guest checkout:</b> email + {@link NatallergyPageActions#fillShippingNewAddressForm}, continue to payment
 *       block ({@link NatallergyPageActions#waitForPaymentMethodsLoaded}) — <b>no place order, no payment submit</b>
 *       (entire phase optional via {@link NatallergyConfig#isCheckoutShippingStepEnabled()}).</li>
 *   <li><b>Optional login</b> ({@link NatallergyConfig#isLoginEnabled()}): after shipping step, {@link NatallergyPageActions#performCustomerLogin}
 *       → dashboard URL → screenshot → return home → search keyword.</li>
 *   <li><b>Cart web ({@link NatallergyCartWebTests}):</b> remove applied coupon (Cancel control); logged-in cart shows
 *       ≥1 line before logout (screenshot).</li>
 *   <li><b>Reporting:</b> {@link NatallergyReporting} full-page PNG + CSV + HTML.</li>
 * </ul>
 *
 * <h2>1. Login validation — backlog</h2>
 * <ul>
 *   <li>Valid credentials → success — <b>asserted</b> in {@link NatallergyAccountWebTests} (URL leaves {@code /customer/account/login}).</li>
 *   <li>Invalid password — <b>asserted</b> in {@link NatallergyAccountWebTests} (stay on login or error banner).</li>
 *   <li>Redirect to dashboard <i>or</i> previous page after login — not asserted; E2E flow always opens dashboard then home.</li>
 *   <li>Session created (cookie / customer section) — not explicitly asserted.</li>
 *   <li>Logout works — <b>asserted</b> in {@link NatallergyAccountWebTests} (logout URL, then Sign In link on home).</li>
 * </ul>
 *
 * <h2>2. Account dashboard — backlog</h2>
 * <ul>
 *   <li>Name / email displayed correctly — <b>not asserted</b> (only dashboard screenshot).</li>
 *   <li>Nav links: Orders, Address Book, Wishlist — sidebar/nav presence only in {@link NatallergyAccountWebTests}; individual pages <b>not exercised</b>.</li>
 * </ul>
 *
 * <h2>3. Address book — backlog</h2>
 * <ul>
 *   <li>Add / edit / delete address — <b>not implemented</b> (checkout uses inline shipping form only).</li>
 *   <li>Default billing/shipping — <b>not implemented</b>.</li>
 *   <li>Phone persists after edit — <b>not implemented</b>.</li>
 * </ul>
 *
 * <h2>4. Product browsing (logged-in) — backlog</h2>
 * <ul>
 *   <li>Category vs PDP add paths — today only <b>PDP from sitemap</b>; no category PLP flow.</li>
 *   <li>Customer group pricing — <b>not asserted</b>.</li>
 * </ul>
 *
 * <h2>5. Add to cart — partial vs backlog</h2>
 * <ul>
 *   <li><b>Done (partial):</b> PDP add, qty update on cart page; {@link NatallergyFlow#addFirstSalableProductFromUrls} for single-line cart tests.</li>
 *   <li><b>Backlog:</b> add from category page; cart persists after <b>browser refresh</b> (F5) — not tested.</li>
 * </ul>
 *
 * <h2>6. Cart behavior — partial vs backlog</h2>
 * <ul>
 *   <li><b>Done (partial):</b> {@link NatallergyCartWebTests#loggedIn_cartNotEmpty_beforeLogout} — line count ≥1 after add, then
 *       {@link NatallergyPageActions#performCustomerLogout(WebDriver)} opens cart, {@link NatallergyPageActions#clearShoppingCartAllItems}, then logs out.</li>
 *   <li>Cart contents after logout <i>and</i> re-login merge/persist — <b>not implemented</b>.</li>
 *   <li>Price breakdown: subtotal, tax, shipping — <b>not asserted</b> (no totals parsing).</li>
 * </ul>
 *
 * <h2>7. Wishlist — backlog</h2>
 * <ul>
 *   <li>Add / remove / move to cart — <b>not implemented</b>.</li>
 * </ul>
 *
 * <h2>8. Checkout (critical) — partial vs backlog</h2>
 * <ul>
 *   <li><b>Done (partial):</b> guest email, new shipping address, shipping step → payment section visible.</li>
 *   <li><b>Backlog:</b> address auto-fill for logged-in customer; change shipping/billing; shipping method selection;
 *       payment method selection; place order; failures / retry.</li>
 * </ul>
 *
 * <h2>9. Payment — backlog</h2>
 * <ul>
 *   <li>Successful charge, decline handling, retry — <b>not implemented</b> (needs gateway sandbox + PCI-safe design).</li>
 * </ul>
 *
 * <h2>10. Order confirmation — backlog</h2>
 * <ul>
 *   <li>Success page, order id, email — <b>not implemented</b> (email needs inbox API or Mailhog).</li>
 * </ul>
 *
 * <h2>11. Order history — backlog</h2>
 * <ul>
 *   <li>List, details, invoice/shipment — <b>not implemented</b>.</li>
 * </ul>
 *
 * <h2>12. Coupons / discounts — partial vs backlog</h2>
 * <ul>
 *   <li><b>Done (partial):</b> apply {@link NatallergyConfig#CART_COUPON_CODE} in cart ({@link NatallergyFlow} and tests).</li>
 *   <li><b>Done (partial):</b> remove / cancel coupon in {@link NatallergyCartWebTests#removeCoupon_restoresStateAfterCancel}.</li>
 *   <li><b>Backlog:</b> validate discount math vs UI; invalid/expired coupon negative paths.</li>
 * </ul>
 *
 * <h2>13. Security — backlog</h2>
 * <ul>
 *   <li>Session timeout, guest access to restricted pages, password change — <b>not implemented</b>.</li>
 * </ul>
 *
 * <h2>14. Multi-device / session — backlog</h2>
 * <ul>
 *   <li>Second browser / cart sync — <b>not implemented</b> (needs parallel sessions + same account).</li>
 * </ul>
 *
 * <h2>15. Known-bug regression (document when automated)</h2>
 * <ul>
 *   <li>Cart cleared after login — {@link NatallergyCartWebTests#loggedIn_cartNotEmpty_beforeLogout} covers “not empty before logout” only;
 *       post-login merge still needs a dedicated repro if bugs persist.</li>
 *   <li>Address not saving — needs address-book flow + assertion.</li>
 *   <li>Wrong logged-in price — needs price capture + compare.</li>
 *   <li>Backorder vs in-stock mismatch — extend {@link NatallergyFlow} stock checks.</li>
 *   <li>Coupon not applying — negative path tests for invalid/expired codes.</li>
 * </ul>
 *
 * <h2>Suggested implementation order (pragmatic)</h2>
 * <ol>
 *   <li><b>Login module:</b> dedicated {@code NatallergyLoginTest} — valid/invalid creds, assert URL + header “welcome”.</li>
 *   <li><b>Logout + session:</b> extend {@link NatallergyPageActions} with {@code performLogout()}, assert guest state.</li>
 *   <li><b>Account dashboard assertions:</b> text assertions on name/email; click Orders/Address/Wishlist smoke.</li>
 *   <li><b>Address book CRUD:</b> new flow class or tests under {@code customer/address}.</li>
 *   <li><b>Cart persistence:</b> F5 refresh; cart after re-login merge (extend beyond {@link NatallergyCartWebTests}).</li>
 *   <li><b>Wishlist:</b> Magento wishlist selectors + move-to-cart.</li>
 *   <li><b>Checkout completion:</b> sandbox payment only; never real cards in repo.</li>
 *   <li><b>Order + email:</b> last; requires mail catcher or API.</li>
 * </ol>
 *
 * @see NatallergyFlow Current automated journey
 * @see NatallergyPageActions Reusable UI steps to grow from
 */
public final class NatallergyQaCoverageBacklog {

    private NatallergyQaCoverageBacklog() {
    }
}
