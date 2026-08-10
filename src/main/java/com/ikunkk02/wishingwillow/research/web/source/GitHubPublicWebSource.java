package com.ikunkk02.wishingwillow.research.web.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.research.source.ResearchHttpClient;
import com.ikunkk02.wishingwillow.research.web.HtmlContentExtractor;
import com.ikunkk02.wishingwillow.research.web.WebPageDocument;
import com.ikunkk02.wishingwillow.research.web.WebResearchBudget;
import com.ikunkk02.wishingwillow.research.web.WebSearchResult;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GitHubPublicWebSource {
    private final ResearchHttpClient http;
    private final HtmlContentExtractor extractor;

    public GitHubPublicWebSource(ResearchHttpClient http, HtmlContentExtractor extractor) {
        this.http = http; this.extractor = extractor;
    }

    public List<WebSearchResult> search(String query, WebResearchBudget budget) {
        budget.claimSearch();
        String encoded = URLEncoder.encode(query + " minecraft mod", StandardCharsets.UTF_8).replace("+", "%20");
        ResearchHttpClient.HttpResult response = http.getWeb(URI.create(
                "https://api.github.com/search/repositories?per_page=5&q=" + encoded), Map.of("Accept", "application/vnd.github+json")).join();
        if (response.status() == 403 || response.status() == 429) throw new CurseForgePublicWebSource.WebSourceException("SOURCE_TEMPORARILY_UNAVAILABLE");
        if (response.status() != 200) return List.of();
        if (!response.contentType().toLowerCase(java.util.Locale.ROOT).contains("json")) {
            throw new CurseForgePublicWebSource.WebSourceException("UNSUPPORTED_CONTENT_TYPE");
        }
        JsonArray items = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("items");
        if (items == null) return List.of();
        List<WebSearchResult> results = new ArrayList<>();
        for (JsonElement element : items) {
            JsonObject item = element.getAsJsonObject();
            JsonObject owner = item.has("owner") ? item.getAsJsonObject("owner") : new JsonObject();
            results.add(new WebSearchResult(string(item, "name"), string(item, "html_url"),
                    string(item, "description"), string(owner, "login"), List.of(), List.of(),
                    strings(item, "topics"), List.of(), "GITHUB"));
        }
        return results;
    }

    public WebPageDocument fetchReadme(String repositoryUrl, WebResearchBudget budget) {
        budget.claimCandidate(); budget.claimFetch();
        String path = URI.create(repositoryUrl).getPath();
        String[] parts = path == null ? new String[0] : path.split("/");
        if (parts.length < 3) throw new IllegalArgumentException("INVALID_GITHUB_REPOSITORY");
        URI uri = URI.create("https://api.github.com/repos/" + parts[1] + "/" + parts[2] + "/readme");
        ResearchHttpClient.HttpResult response = http.getWeb(uri, Map.of("Accept", "application/vnd.github.raw+json")).join();
        if (response.status() == 403 || response.status() == 429) throw new CurseForgePublicWebSource.WebSourceException("SOURCE_TEMPORARILY_UNAVAILABLE");
        if (response.status() != 200) throw new CurseForgePublicWebSource.WebSourceException("NOT_FOUND");
        return extractor.extract(response.body(), response.contentType(), URI.create(repositoryUrl));
    }

    private static String string(JsonObject value, String name) {
        return value.has(name) && value.get(name).isJsonPrimitive() ? value.get(name).getAsString() : "";
    }
    private static List<String> strings(JsonObject value, String name) {
        if (!value.has(name) || !value.get(name).isJsonArray()) return List.of();
        List<String> result = new ArrayList<>(); value.getAsJsonArray(name).forEach(element -> {
            if (element.isJsonPrimitive()) result.add(element.getAsString());
        }); return result;
    }
}
