package org.example;

/**
 * One storefront URL after parsing the sitemap crawl.
 *
 * @param url full HTTPS link to a {@code .html} page
 * @param fromProductSitemap {@code true} if this URL appeared inside an XML file whose path/name contains
 *     {@code product} (Magento product sitemap). Those URLs are preferred for real PDPs; CMS pages like
 *     {@code /current-sale.html} usually live only in generic sitemaps.
 */
public record SitemapPage(String url, boolean fromProductSitemap) {}
