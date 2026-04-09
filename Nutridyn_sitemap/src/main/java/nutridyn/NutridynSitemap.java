package nutridyn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads NutriDyn {@code sitemap.xml}: product URLs vs category URLs use different markers in each {@code <url>} block.
 */
public final class NutridynSitemap {

    private NutridynSitemap() {
    }

    private static String httpGetString(String urlStr) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", "NutridynAutomation/1.0 (Sitemap)");
        c.setConnectTimeout(30000);
        c.setReadTimeout(120000);
        int code = c.getResponseCode();
        if (code >= 400) {
            throw new IOException("HTTP " + code + " for " + urlStr);
        }
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            in.transferTo(buf);
            return buf.toString(StandardCharsets.UTF_8);
        }
    }

    private static List<String> dedupeUrls(List<String> urls) {
        return new ArrayList<>(new LinkedHashSet<>(urls));
    }

    /**
     * Product rows include {@code image:image} and {@code pub/media/catalog/product}.
     */
    private static List<String> extractProductUrlsFromUrlset(String xml) {
        List<String> out = new ArrayList<>();
        Pattern locPattern = Pattern.compile("<loc>(https://nutridyn\\.com[^<]*)</loc>");
        for (String chunk : xml.split("</url>")) {
            if (!chunk.contains("image:image") || !chunk.contains("catalog/product")) {
                continue;
            }
            Matcher m = locPattern.matcher(chunk);
            if (!m.find()) {
                continue;
            }
            String u = m.group(1).trim();
            if ("https://nutridyn.com/".equals(u) || "https://nutridyn.com".equals(u)) {
                continue;
            }
            if (u.contains("/catalog/category/")) {
                continue;
            }
            try {
                String path = URI.create(u).normalize().getPath();
                if (path == null) {
                    continue;
                }
                long segments = Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).count();
                if (segments < 2) {
                    continue;
                }
            } catch (IllegalArgumentException | NullPointerException ex) {
                continue;
            }
            out.add(u);
        }
        return out;
    }

    /**
     * Category / department URLs: {@code priority} is {@code 0.5} and the block does not contain product image paths
     * ({@code pub/media/catalog/product}). Product rows always include that string.
     */
    private static List<String> extractCategoryUrlsFromUrlset(String xml) {
        List<String> out = new ArrayList<>();
        Pattern locPattern = Pattern.compile("<loc>(https://nutridyn\\.com[^<]*)</loc>");
        for (String chunk : xml.split("</url>")) {
            if (chunk.contains("pub/media/catalog/product")) {
                continue;
            }
            if (!chunk.contains("<priority>0.5</priority>")) {
                continue;
            }
            Matcher m = locPattern.matcher(chunk);
            if (!m.find()) {
                continue;
            }
            String u = m.group(1).trim();
            if ("https://nutridyn.com/".equals(u) || "https://nutridyn.com".equals(u)) {
                continue;
            }
            if (u.contains("/catalog/category/view")) {
                continue;
            }
            try {
                String path = URI.create(u).normalize().getPath();
                if (path == null) {
                    continue;
                }
                long segments = Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).count();
                if (segments < 1 || segments > 3) {
                    continue;
                }
            } catch (IllegalArgumentException | NullPointerException ex) {
                continue;
            }
            out.add(u);
        }
        return out;
    }

    private static List<String> loadAllCategoryUrls(String sitemapUrl) throws IOException {
        String xml = httpGetString(sitemapUrl);
        if (xml.contains("<sitemapindex")) {
            List<String> merged = new ArrayList<>();
            Matcher m = Pattern.compile("<loc>(https://[^<]+\\.xml)</loc>").matcher(xml);
            while (m.find()) {
                String child = m.group(1);
                if (!child.contains("nutridyn.com")) {
                    continue;
                }
                try {
                    merged.addAll(extractCategoryUrlsFromUrlset(httpGetString(child)));
                } catch (IOException e) {
                    System.err.println("Child sitemap skipped: " + child + " — " + e.getMessage());
                }
            }
            return dedupeUrls(merged);
        }
        return dedupeUrls(extractCategoryUrlsFromUrlset(xml));
    }

    /**
     * @param maxCount how many category URLs to return after shuffle (caller picks random 3–4, etc.)
     */
    public static List<String> loadCategoryUrlsFromSitemap(String sitemapUrl, int maxCount) throws IOException {
        List<String> all = loadAllCategoryUrls(sitemapUrl);
        if (all.isEmpty()) {
            return all;
        }
        Collections.shuffle(all);
        int n = Math.min(maxCount, all.size());
        return new ArrayList<>(all.subList(0, n));
    }

    private static List<String> loadAllProductUrls(String sitemapUrl) throws IOException {
        String xml = httpGetString(sitemapUrl);
        if (xml.contains("<sitemapindex")) {
            List<String> merged = new ArrayList<>();
            Matcher m = Pattern.compile("<loc>(https://[^<]+\\.xml)</loc>").matcher(xml);
            while (m.find()) {
                String child = m.group(1);
                if (!child.contains("nutridyn.com")) {
                    continue;
                }
                try {
                    merged.addAll(extractProductUrlsFromUrlset(httpGetString(child)));
                } catch (IOException e) {
                    System.err.println("Child sitemap skipped: " + child + " — " + e.getMessage());
                }
            }
            return dedupeUrls(merged);
        }
        return dedupeUrls(extractProductUrlsFromUrlset(xml));
    }

    public static List<String> loadProductUrlsFromSitemap(String sitemapUrl, int maxCount) throws IOException {
        List<String> all = loadAllProductUrls(sitemapUrl);
        if (all.isEmpty()) {
            return all;
        }
        Collections.shuffle(all);
        int n = Math.min(maxCount, all.size());
        return new ArrayList<>(all.subList(0, n));
    }

    public static String productUrlSlug(String productUrl) {
        try {
            String path = URI.create(productUrl).getPath();
            if (path == null) {
                return "product";
            }
            String[] parts = path.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (!parts[i].isEmpty()) {
                    return parts[i].replaceAll("[^a-zA-Z0-9_-]+", "_");
                }
            }
        } catch (Exception ignored) {
        }
        return "product";
    }

    /** Last path segment of a category URL, safe for screenshot / report filenames. */
    public static String categoryUrlSlug(String categoryUrl) {
        String s = productUrlSlug(categoryUrl);
        return "product".equals(s) ? "category" : s;
    }
}
