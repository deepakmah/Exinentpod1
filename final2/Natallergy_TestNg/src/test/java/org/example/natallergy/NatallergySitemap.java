package org.example.natallergy;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * <p><b>What this does:</b> downloads the Magento sitemap XML (which may be an index pointing at more XML files),
 * walks that tree, and collects URLs that look like <em>product detail pages</em> — not categories, cart, login, or
 * static pages such as {@code shipping.html}.</p>
 *
 * <p>The browser is not used here; this is plain HTTP. {@link NatallergyTest} calls it during setup so the test
 * already has a ready-made list of PDP links before {@link NatallergyFlow} starts shopping.</p>
 */
public final class NatallergySitemap {

    public static List<String> discoverProductUrlsFromSitemap() throws IOException {
        Set<String> leaf = new LinkedHashSet<>();
        collectSitemapLeafUrls(NatallergyConfig.SITEMAP_URL, 0, leaf);
        List<String> products = new ArrayList<>();
        for (String u : leaf) {
            if (isCatalogProductPage(u)) {
                products.add(u);
            }
        }
        return products;
    }

    private static List<String> extractSitemapLocs(String xml) {
        List<String> out = new ArrayList<>();
        Matcher m = NatallergyConfig.SITEMAP_LOC_PATTERN.matcher(xml);
        while (m.find()) {
            String loc = m.group(1).trim();
            if (!loc.isEmpty()) {
                out.add(loc);
            }
        }
        return out;
    }

    private static String fetchHttpText(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(60_000);
        conn.setReadTimeout(120_000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; NatallergyAutomation/1.0)");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code >= 400) {
            throw new IOException("HTTP " + code + " for " + urlString);
        }
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * If the sitemap lists another storefront host (common on multi-store Magento), rewrite path/query to
     * {@link NatallergyConfig#SITE_BASE} so the session stays on the site under test.
     */
    private static String rewriteLocHostToSiteBase(String loc) {
        try {
            URI u = URI.create(loc.trim());
            URI base = URI.create(NatallergyConfig.SITE_BASE);
            if (u.getHost() != null && u.getHost().equalsIgnoreCase(base.getHost())) {
                return loc.trim();
            }
            String path = u.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String q = u.getRawQuery();
            String frag = u.getRawFragment();
            StringBuilder sb = new StringBuilder();
            sb.append(base.getScheme()).append("://").append(base.getHost());
            if (base.getPort() != -1) {
                sb.append(":").append(base.getPort());
            }
            sb.append(path);
            if (q != null) {
                sb.append("?").append(q);
            }
            if (frag != null) {
                sb.append("#").append(frag);
            }
            return sb.toString();
        } catch (Exception e) {
            return loc.trim();
        }
    }

    private static void collectSitemapLeafUrls(String sitemapUrl, int depth, Set<String> leafUrls) throws IOException {
        if (depth > 12) {
            return;
        }
        String xml = fetchHttpText(sitemapUrl);
        List<String> locs = extractSitemapLocs(xml);
        boolean hasChildXml = false;
        for (String loc : locs) {
            if (loc.toLowerCase().endsWith(".xml")) {
                hasChildXml = true;
                break;
            }
        }
        if (hasChildXml) {
            for (String loc : locs) {
                if (loc.toLowerCase().endsWith(".xml")) {
                    collectSitemapLeafUrls(loc, depth + 1, leafUrls);
                }
            }
        } else {
            for (String loc : locs) {
                leafUrls.add(rewriteLocHostToSiteBase(loc));
            }
        }
    }

    /** URLs that look like catalog product detail pages (not categories, cart, account, or {@code shipping.html}). */
    private static boolean isCatalogProductPage(String url) {
        try {
            URI u = URI.create(url);
            if (u.getHost() == null) {
                return false;
            }
            String siteHost = URI.create(NatallergyConfig.SITE_BASE).getHost();
            if (siteHost != null && !u.getHost().equalsIgnoreCase(siteHost)) {
                return false;
            }
            String path = u.getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) {
                return false;
            }
            String lower = url.toLowerCase();
            int q = lower.indexOf('?');
            String pathLower = q >= 0 ? lower.substring(0, q) : lower;
            if (pathLower.contains("/customer/")
                    || pathLower.contains("/checkout")
                    || pathLower.contains("/cart")
                    || pathLower.contains("/catalogsearch/")
                    || pathLower.contains("/wishlist")) {
                return false;
            }
            if (pathLower.contains("/catalog/category/")) {
                return false;
            }
            // CMS / static pages that end in .html but are not PDPs (sitemap sometimes lists them).
            if (pathLower.contains("shipping.html")) {
                return false;
            }
            if (pathLower.endsWith(".html")) {
                return true;
            }
            return pathLower.contains("/catalog/product/view/");
        } catch (Exception e) {
            return false;
        }
    }

    private NatallergySitemap() {
    }
}
