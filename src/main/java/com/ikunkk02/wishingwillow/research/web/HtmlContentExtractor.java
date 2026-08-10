package com.ikunkk02.wishingwillow.research.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HtmlContentExtractor {
    private static final int MAX_LINKS = 32;

    public WebPageDocument extract(String body, String contentType, URI finalUri) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!(type.contains("html") || type.contains("json") || type.startsWith("text/") || type.isBlank())) {
            throw new IllegalArgumentException("UNSUPPORTED_CONTENT_TYPE");
        }
        if (!type.contains("html")) {
            String content = limit(normalize(body));
            return new WebPageDocument("", finalUri.toString(), content, List.of());
        }
        Document document = Jsoup.parse(body == null ? "" : body, finalUri.toString());
        document.select("script,style,noscript,svg,canvas,form,nav,footer,iframe,object,embed").remove();
        String title = document.title();
        Element root = document.selectFirst("main,article,[role=main]");
        if (root == null) root = document.body();
        List<WebPageDocument.Link> links = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (root != null) {
            for (Element link : root.select("a[href]")) {
                if (links.size() >= MAX_LINKS) break;
                String absolute = link.absUrl("href");
                if (absolute.isBlank() || !absolute.toLowerCase(Locale.ROOT).startsWith("https://")
                        || isDownload(absolute) || !seen.add(absolute)) continue;
                links.add(new WebPageDocument.Link(limit(link.text(), 256), limit(absolute, 2048)));
            }
        }
        String content = limit(normalize(root == null ? "" : root.wholeText()));
        return new WebPageDocument(limit(title, 256), finalUri.toString(), content, links);
    }

    private static String normalize(String value) {
        return (value == null ? "" : value).replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ").replaceAll("\\n{3,}", "\n\n").strip();
    }

    private static String limit(String value) { return limit(value, WebResearchBudget.MAX_EXTRACTED_CHARS); }
    private static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
    private static boolean isDownload(String url) {
        String path;
        try { path = URI.create(url).getPath().toLowerCase(Locale.ROOT); }
        catch (RuntimeException exception) { return true; }
        return path.matches(".*\\.(jar|zip|exe|dll|msi|dmg|pkg)$")
                || path.contains("/download/") || path.endsWith("/download");
    }
}
