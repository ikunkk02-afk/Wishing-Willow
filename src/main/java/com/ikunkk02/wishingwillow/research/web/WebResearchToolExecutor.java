package com.ikunkk02.wishingwillow.research.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.research.source.ResearchHttpClient;
import com.ikunkk02.wishingwillow.research.web.source.CurseForgePublicWebSource;
import com.ikunkk02.wishingwillow.research.web.source.GitHubPublicWebSource;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class WebResearchToolExecutor {
    private static final Gson GSON = new Gson();
    private final WebResearchBudget budget;
    private final CurseForgePublicWebSource curseForge;
    private final GitHubPublicWebSource github;
    private final ResearchHttpClient http;
    private final HtmlContentExtractor extractor;
    private final Set<String> allowedUrls = java.util.Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> candidateUrls = java.util.Collections.synchronizedSet(new LinkedHashSet<>());
    private final List<WebSearchResult> discoveredResults = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Map<String, WebPageDocument> fetchedPages = java.util.Collections.synchronizedMap(new LinkedHashMap<>());
    private int executedCalls;

    public WebResearchToolExecutor(WebResearchBudget budget, CurseForgePublicWebSource curseForge,
                                   GitHubPublicWebSource github, ResearchHttpClient http,
                                   HtmlContentExtractor extractor) {
        this.budget = budget; this.curseForge = curseForge; this.github = github;
        this.http = http; this.extractor = extractor;
    }

    public void allow(String url) {
        if (url == null || url.isBlank()) return;
        URI uri = URI.create(url);
        if ("https".equalsIgnoreCase(uri.getScheme())) allowedUrls.add(canonical(uri));
    }

    public void allowCandidate(String url) {
        allow(url);
        if (url != null && !url.isBlank()) candidateUrls.add(canonical(URI.create(url)));
    }

    public String execute(String toolName, String argumentsJson) {
        try {
            executedCalls++;
            JsonObject arguments = JsonParser.parseString(argumentsJson == null ? "{}" : argumentsJson).getAsJsonObject();
            if (WebResearchTool.SEARCH_MOD_WEB.toolName().equals(toolName)) {
                String query = required(arguments, "query", 256);
                PreferredDomain domain = PreferredDomain.valueOf(required(arguments, "preferred_domain", 32));
                List<WebSearchResult> results = switch (domain) {
                    case CURSEFORGE -> curseForge.search(query, budget);
                    case GITHUB -> github.search(query, budget);
                    case AUTO -> auto(query);
                };
                results.forEach(result -> allowCandidate(result.url()));
                discoveredResults.addAll(results);
                return GSON.toJson(Map.of("results", results));
            }
            if (WebResearchTool.FETCH_RESEARCH_PAGE.toolName().equals(toolName)) {
                String url = required(arguments, "url", 2048);
                URI uri = URI.create(url);
                String canonical = canonical(uri);
                if (!allowedUrls.contains(canonical)) throw new IllegalArgumentException("URL_NOT_APPROVED");
                if (candidateUrls.contains(canonical)) budget.claimCandidate();
                budget.claimFetch();
                ResearchHttpClient.HttpResult response = http.getWeb(uri, Map.of()).join();
                if (response.status() == 403 || response.status() == 429) {
                    return GSON.toJson(Map.of("error", "SOURCE_TEMPORARILY_UNAVAILABLE"));
                }
                if (response.status() != 200) return GSON.toJson(Map.of("error", "WEB_HTTP_" + response.status()));
                String lower = response.body().toLowerCase(Locale.ROOT);
                if (lower.contains("cf-chl-") || lower.contains("cloudflare challenge") || lower.contains("captcha")) {
                    return GSON.toJson(Map.of("error", "SOURCE_TEMPORARILY_UNAVAILABLE"));
                }
                WebPageDocument page = extractor.extract(response.body(), response.contentType(), response.finalUri());
                page.links().forEach(link -> allow(link.url()));
                fetchedPages.put(canonical(URI.create(page.finalUrl())), page);
                return GSON.toJson(page);
            }
            throw new IllegalArgumentException("UNKNOWN_TOOL");
        } catch (RuntimeException exception) {
            String code = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            return GSON.toJson(Map.of("error", code.replaceAll("[^A-Za-z0-9_]", "_")));
        }
    }

    public List<WebSearchResult> discoveredResults() { return List.copyOf(discoveredResults); }
    public Map<String, WebPageDocument> fetchedPages() { return Map.copyOf(fetchedPages); }
    public int executedCalls() { return executedCalls; }

    private List<WebSearchResult> auto(String query) {
        List<WebSearchResult> first = curseForge.search(query, budget);
        if (!first.isEmpty() || budget.searchesRemaining() == 0) return first;
        return github.search(query, budget);
    }
    private static String required(JsonObject object, String name, int max) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) throw new IllegalArgumentException("INVALID_ARGUMENTS");
        String value = object.get(name).getAsString().strip();
        if (value.isBlank() || value.length() > max) throw new IllegalArgumentException("INVALID_ARGUMENTS");
        return value;
    }
    private static String canonical(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
        return "https://" + host + path + (uri.getQuery() == null ? "" : "?" + uri.getQuery());
    }
    public enum PreferredDomain { CURSEFORGE, GITHUB, AUTO }
}
