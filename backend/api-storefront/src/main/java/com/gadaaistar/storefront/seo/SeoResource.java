package com.gadaaistar.storefront.seo;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.gadaaistar.persistence.cms.CmsPageRepository;
import com.gadaaistar.persistence.catalog.ProductRepository;

/**
 * SEO endpoints: {@code robots.txt} and {@code sitemap.xml}.
 * Both are served as plain text / XML so crawlers can consume them
 * directly without any JSON parsing.
 */
@Path("/api/storefront/v1/seo")
@ApplicationScoped
@Tag(name = "SEO", description = "robots.txt and sitemap.xml for search engines")
public class SeoResource {

    @Inject CmsPageRepository   pageRepo;
    @Inject ProductRepository   productRepo;

    @Inject
    @ConfigProperty(name = "gadaa.frontend.storefront-url")
    String storefrontUrl;

    @GET
    @Path("/robots.txt")
    @Produces("text/plain")
    @Operation(summary = "Serve robots.txt for crawlers")
    public Response robots() {
        var sb = new StringBuilder();
        sb.append("User-agent: *\n");
        sb.append("Allow: /\n");
        sb.append("Disallow: /api/\n");
        sb.append("Disallow: /login\n");
        sb.append("Disallow: /register\n");
        sb.append("Disallow: /account\n");
        sb.append("Disallow: /cart\n");
        sb.append("Disallow: /checkout\n");
        sb.append("Disallow: /wishlist\n");
        sb.append("\n");
        sb.append("Sitemap: ").append(storefrontUrl).append("/sitemap.xml\n");
        return Response.ok(sb.toString()).type("text/plain").build();
    }

    @GET
    @Path("/sitemap.xml")
    @Produces("application/xml")
    @Operation(summary = "Serve sitemap.xml for crawlers")
    public Response sitemap() {
        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Static pages
        appendUrl(sb, storefrontUrl + "/", "daily", "1.0");
        appendUrl(sb, storefrontUrl + "/shop", "daily", "0.9");
        appendUrl(sb, storefrontUrl + "/shop/raw-coffee", "weekly", "0.8");
        appendUrl(sb, storefrontUrl + "/shop/processed-coffee", "weekly", "0.8");
        appendUrl(sb, storefrontUrl + "/shop/white-products", "weekly", "0.8");
        appendUrl(sb, storefrontUrl + "/shop/spicy-products", "weekly", "0.8");

        // CMS pages
        for (var page : pageRepo.listPublished()) {
            appendUrl(sb, storefrontUrl + "/pages/" + page.slug, "monthly", "0.6");
        }

        // Product pages
        for (var p : productRepo.findAllActive()) {
            appendUrl(sb, storefrontUrl + "/product/" + p.slug, "weekly", "0.7");
        }

        sb.append("</urlset>\n");
        return Response.ok(sb.toString()).type("application/xml").build();
    }

    private void appendUrl(StringBuilder sb, String loc, String changefreq, String priority) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
        sb.append("    <lastmod>").append(Instant.now().toString()).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
