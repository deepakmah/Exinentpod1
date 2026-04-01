package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Downloads and walks Magento XML sitemaps, then exposes URLs for automation.
 *
 * <p><b>How crawling works</b>
 * <ul>
 *   <li>Start at {@link GreatCellConfig#SITEMAP_URL} (often a sitemap <i>index</i>).</li>
 *   <li>If the file is an index, enqueue every child {@code .xml} {@code &lt;loc&gt;}.</li>
 *   <li>Leaf sitemaps: collect every non-XML {@code &lt;loc&gt;} that passes {@link #isEligibleStorePageUrl}.</li>
 *   <li>Remember whether each URL came from a “product” sitemap file → {@link SitemapPage#fromProductSitemap}.</li>
 * </ul>
 *
 * <p><b>URL shapes</b> (heuristics, not Magento API):
 * <ul>
 *   <li><i>Category-like:</i> path has 2+ segments ending in {@code .html} (e.g. {@code /a/b.html}).</li>
 *   <li><i>Product-like (fallback):</i> single segment {@code /slug.html} excluding CMS slugs in
 *       {@link #CMS_TOP_LEVEL_SLUGS}.</li>
 * </ul>
 */
public final class SitemapService {

    private SitemapService() {}

    // --- Paths we never treat as storefront “pages” for this automation ---
    private static final String[] SITEMAP_EXCLUDED_PATH_SNIPPETS = {
            "/checkout/", "/customer/", "/wishlist", "catalogsearch", "/pub/media/", "/static/",
            "/rss/", "sitemap.xml", "/catalog/product_compare", "newsletter", "/sendfriend/",
    };

    /** Top-level {@code /slug.html} pages that are CMS, not simple products. */
    private static final Set<String> CMS_TOP_LEVEL_SLUGS = Set.of(
            "about-us",
            "contact",
            "faqs",
            "faq",
            "advanced-search",
            "privacy-policy-cookie-restriction-mode",
            "privacy-policy",
            "catalog-search-advanced",
            "enable-cookies",
            "home",
            "current-sale",
            "sale",
            "clearance",
            "deals",
            "specials",
            "promotions",
            "terms-and-conditions",
            "shipping-delivery",
            "returns",
            "blog",
            "customer-service");

    // --- Public API: load everything, then filtered lists for Main / StorefrontFlow ---

    /**
     * Crawls from {@code entryPoint} (typically {@link GreatCellConfig#SITEMAP_URL}) and returns deduped
     * eligible pages with product-sitemap flags.
     */
    public static List<SitemapPage> loadStorePages(String entryPoint) throws IOException {
        Set<String> visitedXml = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(entryPoint);
        Map<String, Boolean> urlToFromProduct = new LinkedHashMap<>();

        while (!queue.isEmpty()) {
            String xmlUrl = queue.removeFirst();
            if (xmlUrl.endsWith(".xml")) {
                if (!visitedXml.add(xmlUrl)) {
                    continue;
                }
            }
            String xml = httpGet(xmlUrl);
            boolean isIndex = xml.contains("sitemapindex");
            boolean productSitemap = xmlUrl.toLowerCase(Locale.ROOT).contains("product");
            List<String> locs = extractLocsFromXml(xml);
            for (String loc : locs) {
                if (loc == null || !loc.startsWith("http")) {
                    continue;
                }
                if (isIndex && loc.endsWith(".xml")) {
                    queue.addLast(loc);
                } else if (!isIndex && !loc.endsWith(".xml") && isEligibleStorePageUrl(loc)) {
                    String u = loc.trim();
                    urlToFromProduct.merge(u, productSitemap, Boolean::logicalOr);
                }
            }
        }

        return urlToFromProduct.entrySet().stream()
                .map(e -> new SitemapPage(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    // --- XML / HTTP helpers (no Selenium) ---

    /** Pulls every {@code <loc>...</loc>} value; supports CDATA. */
    private static List<String> extractLocsFromXml(String xml) {
        List<String> out = new ArrayList<>();
        Pattern p = Pattern.compile("<loc>\\s*([^<]+?)\\s*</loc>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(xml);
        while (m.find()) {
            String s = m.group(1).trim();
            if (s.startsWith("<![CDATA[")) {
                int end = s.indexOf("]]>");
                s = end > 0 ? s.substring(9, end).trim() : s;
            }
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** Simple GET with timeouts and a custom User-Agent (sitemap servers sometimes block default Java UA). */
    private static String httpGet(String urlString) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(25_000);
        conn.setReadTimeout(45_000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; GreatCellSolarAutomation/1.0)");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) {
            throw new IOException("HTTP " + code + " for " + urlString);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    /** Host must be Great Cell Solar; path must be {@code .html}; must not match checkout/media/etc. */
    static boolean isEligibleStorePageUrl(String loc) {
        try {
            URI uri = URI.create(loc.replace(" ", "%20"));
            String host = uri.getHost();
            if (host == null || !host.toLowerCase(Locale.ROOT).contains("greatcellsolarmaterials")) {
                return false;
            }
            String path = uri.getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) {
                return false;
            }
            String pl = path.toLowerCase(Locale.ROOT);
            for (String ex : SITEMAP_EXCLUDED_PATH_SNIPPETS) {
                if (pl.contains(ex.toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
            return path.endsWith(".html");
        } catch (Exception e) {
            return false;
        }
    }

    /** Counts non-empty path segments: {@code /a/b.html} → 2. */
    private static int pathSegmentCount(String path) {
        if (path == null) {
            return 0;
        }
        return (int) Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).count();
    }

    /** Magento-style category URLs: {@code /parent/child.html} (two or more segments). */
    static boolean looksLikeCategoryUrl(String loc) {
        try {
            String path = URI.create(loc.replace(" ", "%20")).getPath();
            return path != null && path.endsWith(".html") && pathSegmentCount(path) >= 2;
        } catch (Exception e) {
            return false;
        }
    }

    /** Single-path {@code /slug.html}; excludes known CMS slugs. */
    static boolean looksLikeProductUrl(String loc) {
        try {
            String path = URI.create(loc.replace(" ", "%20")).getPath();
            if (path == null || !path.endsWith(".html")) {
                return false;
            }
            if (pathSegmentCount(path) != 1) {
                return false;
            }
            String file = path.substring(path.lastIndexOf('/') + 1);
            String slug = file.substring(0, file.length() - 5).toLowerCase(Locale.ROOT);
            if (CMS_TOP_LEVEL_SLUGS.contains(slug)) {
                return false;
            }
            if (slug.endsWith("-sale") || slug.endsWith("-deals") || slug.startsWith("sale-")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Legacy single-pick helpers (still useful for one-off tests); main flow uses all*Shuffled ---

    public static String pickRandomCategoryUrl(List<SitemapPage> pages) {
        List<String> cats = pages.stream()
                .map(SitemapPage::url)
                .filter(SitemapService::looksLikeCategoryUrl)
                .collect(Collectors.toList());
        if (cats.isEmpty()) {
            cats = pages.stream()
                    .map(SitemapPage::url)
                    .filter(u -> {
                        try {
                            String p = URI.create(u.replace(" ", "%20")).getPath();
                            return p != null && p.endsWith(".html") && pathSegmentCount(p) >= 2;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
        }
        if (cats.isEmpty()) {
            throw new IllegalStateException("Sitemap contained no category-like URLs (multi-segment .html paths)");
        }
        Collections.shuffle(cats, new Random());
        String picked = cats.get(0);
        System.out.println("Sitemap category URL: " + picked);
        return picked;
    }

    /**
     * Prefers URLs from product sitemaps (Magento lists real PDPs there). Falls back to single-segment
     * {@code /slug.html} heuristics if none are found.
     */
    public static String pickRandomProductUrl(List<SitemapPage> pages, String avoidUrl) {
        List<String> fromProductSitemap = pages.stream()
                .filter(SitemapPage::fromProductSitemap)
                .map(SitemapPage::url)
                .filter(u -> !u.equals(avoidUrl))
                .collect(Collectors.toList());
        Collections.shuffle(fromProductSitemap, new Random());
        if (!fromProductSitemap.isEmpty()) {
            String picked = fromProductSitemap.get(0);
            System.out.println("Sitemap product URL (from product sitemap): " + picked);
            return picked;
        }

        List<String> prods = pages.stream()
                .map(SitemapPage::url)
                .filter(SitemapService::looksLikeProductUrl)
                .collect(Collectors.toList());
        if (prods.isEmpty()) {
            throw new IllegalStateException(
                    "Sitemap contained no product-like URLs. Ensure product sitemaps are linked from "
                            + GreatCellConfig.SITEMAP_URL
                            + " or add CMS slugs / filters in SitemapService.");
        }
        Collections.shuffle(prods, new Random());
        for (String p : prods) {
            if (!p.equals(avoidUrl)) {
                System.out.println("Sitemap product URL (single-segment heuristic): " + p);
                return p;
            }
        }
        return prods.get(0);
    }

    /** All category-like URLs from the sitemap, shuffled (for empty-category retries). */
    public static List<String> allCategoryUrlsShuffled(List<SitemapPage> pages) {
        List<String> cats = pages.stream()
                .map(SitemapPage::url)
                .filter(SitemapService::looksLikeCategoryUrl)
                .collect(Collectors.toList());
        if (cats.isEmpty()) {
            cats = pages.stream()
                    .map(SitemapPage::url)
                    .filter(u -> {
                        try {
                            String p = URI.create(u.replace(" ", "%20")).getPath();
                            return p != null && p.endsWith(".html") && pathSegmentCount(p) >= 2;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
        }
        Collections.shuffle(cats, new Random());
        return cats;
    }

    /**
     * Product candidates: product-sitemap URLs first, then single-segment heuristic URLs. Deduped; excludes any
     * URL in {@code exclude} (e.g. chosen category and already-added PDPs).
     */
    public static List<String> allProductUrlsShuffled(List<SitemapPage> pages, Set<String> exclude) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        pages.stream()
                .filter(SitemapPage::fromProductSitemap)
                .map(SitemapPage::url)
                .filter(u -> exclude == null || !exclude.contains(u))
                .forEach(ordered::add);
        pages.stream()
                .map(SitemapPage::url)
                .filter(SitemapService::looksLikeProductUrl)
                .filter(u -> exclude == null || !exclude.contains(u))
                .forEach(ordered::add);
        List<String> list = new ArrayList<>(ordered);
        Collections.shuffle(list, new Random());
        return list;
    }
}
