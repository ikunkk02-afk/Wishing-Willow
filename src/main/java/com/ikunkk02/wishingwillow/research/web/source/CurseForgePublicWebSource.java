package com.ikunkk02.wishingwillow.research.web.source;

import com.ikunkk02.wishingwillow.research.source.ResearchHttpClient;
import com.ikunkk02.wishingwillow.research.web.HtmlContentExtractor;
import com.ikunkk02.wishingwillow.research.web.WebPageDocument;
import com.ikunkk02.wishingwillow.research.web.WebResearchBudget;
import com.ikunkk02.wishingwillow.research.web.WebSearchResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CurseForgePublicWebSource {
    private static final String SEARCH = "https://www.curseforge.com/minecraft/search?class=mc-mods&page=1&pageSize=20&sortType=1&search=";
    private static final Pattern VERSION = Pattern.compile("(?<![0-9])1\\.(?:18|19|20|21)(?:\\.\\d+)?(?![0-9])");
    private static final Pattern JAR = Pattern.compile("(?:^|\\s)([A-Za-z0-9_.+()-]+\\.jar)", Pattern.CASE_INSENSITIVE);
    private static final Semaphore PERMIT = new Semaphore(1);
    private static final Object RATE_LOCK = new Object();
    private static long lastRequestAt;

    private final ResearchHttpClient http;
    private final HtmlContentExtractor extractor;

    public CurseForgePublicWebSource(ResearchHttpClient http, HtmlContentExtractor extractor) {
        this.http = http;
        this.extractor = extractor;
    }

    public List<WebSearchResult> search(String query, WebResearchBudget budget) {
        budget.claimSearch();
        URI uri = URI.create(SEARCH + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20"));
        ResearchHttpClient.HttpResult response = request(uri);
        if (response.status() == 403 || response.status() == 429 || challenged(response.body())) {
            throw new WebSourceException("SOURCE_TEMPORARILY_UNAVAILABLE");
        }
        if (response.status() != 200) throw new WebSourceException(response.status() == 404 ? "NOT_FOUND" : "WEB_HTTP_" + response.status());
        if (!response.contentType().toLowerCase(Locale.ROOT).contains("html")) {
            throw new WebSourceException("UNSUPPORTED_CONTENT_TYPE");
        }
        return parseSearch(response.body(), response.finalUri());
    }

    public ProjectPage fetchProject(String url, WebResearchBudget budget) {
        budget.claimCandidate();
        budget.claimFetch();
        ResearchHttpClient.HttpResult response = request(URI.create(url));
        if (response.status() == 403 || response.status() == 429 || challenged(response.body())) {
            throw new WebSourceException("SOURCE_TEMPORARILY_UNAVAILABLE");
        }
        if (response.status() != 200) throw new WebSourceException(response.status() == 404 ? "NOT_FOUND" : "WEB_HTTP_" + response.status());
        if (!response.contentType().toLowerCase(Locale.ROOT).contains("html")) {
            throw new WebSourceException("UNSUPPORTED_CONTENT_TYPE");
        }
        WebPageDocument page = extractor.extract(response.body(), response.contentType(), response.finalUri());
        WebSearchResult result = parseProject(response.body(), response.finalUri());
        return new ProjectPage(result, page);
    }

    public List<WebSearchResult> parseSearch(String html, URI baseUri) {
        Document document = Jsoup.parse(html == null ? "" : html, baseUri.toString());
        Map<String, WebSearchResult> results = new LinkedHashMap<>();
        for (Element link : document.select("a[href*=/minecraft/mc-mods/]")) {
            String url = canonicalProjectUrl(link.absUrl("href"));
            if (url.isBlank() || results.containsKey(url) || isActionLink(link)) continue;
            Element card = closestCard(link);
            String text = card == null ? link.parent() == null ? link.text() : link.parent().text() : card.text();
            String title = link.text().strip();
            if (title.isBlank() || title.equalsIgnoreCase("view") || title.equalsIgnoreCase("details")) continue;
            results.put(url, new WebSearchResult(title, url, snippet(text, title), author(text),
                    versions(text), loaders(text), categories(card), fileNames(text), "CURSEFORGE"));
        }
        return results.values().stream().limit(20).toList();
    }

    public WebSearchResult parseProject(String html, URI uri) {
        Document document = Jsoup.parse(html == null ? "" : html, uri.toString());
        Element main = document.selectFirst("main,article,[role=main]");
        if (main == null) main = document.body();
        String text = main == null ? document.text() : main.text();
        Element heading = document.selectFirst("h1");
        String title = heading == null ? document.title().replaceFirst(" - Minecraft.*$", "") : heading.text();
        List<String> categories = new ArrayList<>();
        for (Element link : document.select("a[href*=/minecraft/mc-mods/][href*=category], a[href*=/minecraft/search][href*=category]")) {
            if (!link.text().isBlank()) categories.add(link.text());
        }
        return new WebSearchResult(title, canonicalProjectUrl(uri.toString()), snippet(text, title), author(text),
                versions(text), loaders(text), categories.stream().distinct().limit(16).toList(), fileNames(text), "CURSEFORGE");
    }

    private ResearchHttpClient.HttpResult request(URI uri) {
        boolean acquired = false;
        try {
            PERMIT.acquire();
            acquired = true;
            synchronized (RATE_LOCK) {
                long wait = 1500L - (System.currentTimeMillis() - lastRequestAt);
                if (wait > 0) Thread.sleep(wait);
                lastRequestAt = System.currentTimeMillis();
            }
            return http.getWeb(uri, Map.of()).join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WebSourceException("INTERRUPTED");
        } catch (RuntimeException exception) {
            Throwable cause = unwrap(exception);
            if (cause instanceof ResearchHttpClient.ResearchHttpException httpFailure) {
                throw new WebSourceException(httpFailure.code());
            }
            throw exception;
        } finally {
            if (acquired) PERMIT.release();
        }
    }

    private static Element closestCard(Element link) {
        Element current = link;
        for (int i = 0; i < 5 && current != null; i++, current = current.parent()) {
            if (current.is("article,li") || (current.is("div") && current.select("a[href*=/minecraft/mc-mods/]").size() == 1
                    && current.text().length() > link.text().length() + 10)) return current;
        }
        return link.parent();
    }
    private static boolean isActionLink(Element link) {
        String text = link.text().toLowerCase(Locale.ROOT);
        String href = link.attr("href").toLowerCase(Locale.ROOT);
        return text.equals("download") || text.equals("install") || href.contains("/download") || href.contains("/files/");
    }
    private static String canonicalProjectUrl(String raw) {
        try {
            URI uri = URI.create(raw);
            String path = uri.getPath();
            Matcher matcher = Pattern.compile("^/minecraft/mc-mods/([^/]+)").matcher(path == null ? "" : path);
            return matcher.find() ? "https://www.curseforge.com/minecraft/mc-mods/" + matcher.group(1) : "";
        } catch (RuntimeException ignored) { return ""; }
    }
    private static String author(String text) {
        Matcher matcher = Pattern.compile("(?i)\\bby\\s+([A-Za-z0-9_.-]{2,64})").matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }
    private static List<String> versions(String text) {
        List<String> values = new ArrayList<>(); Matcher matcher = VERSION.matcher(text);
        while (matcher.find() && values.size() < 16) if (!values.contains(matcher.group())) values.add(matcher.group());
        return values;
    }
    private static List<String> loaders(String text) {
        String lower = text.toLowerCase(Locale.ROOT); List<String> result = new ArrayList<>();
        if (lower.contains("forge")) result.add("forge"); if (lower.contains("fabric")) result.add("fabric");
        if (lower.contains("neoforge")) result.add("neoforge"); return result;
    }
    private static List<String> fileNames(String text) {
        List<String> result = new ArrayList<>(); Matcher matcher = JAR.matcher(text);
        while (matcher.find() && result.size() < 16) result.add(matcher.group(1).strip()); return result;
    }
    private static List<String> categories(Element card) {
        if (card == null) return List.of();
        return card.select("a[href*=category]").eachText().stream().filter(value -> !value.isBlank()).distinct().limit(8).toList();
    }
    private static String snippet(String text, String title) {
        String value = text == null ? "" : text.replace(title == null ? "" : title, "").strip();
        return value.length() <= 2048 ? value : value.substring(0, 2048);
    }
    private static boolean challenged(String body) {
        String lower = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return lower.contains("cf-chl-") || lower.contains("cloudflare challenge") || lower.contains("captcha");
    }
    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable; while (current.getCause() != null) current = current.getCause(); return current;
    }

    public record ProjectPage(WebSearchResult result, WebPageDocument page) { }
    public static final class WebSourceException extends RuntimeException {
        public WebSourceException(String code) { super(code); }
    }
}
