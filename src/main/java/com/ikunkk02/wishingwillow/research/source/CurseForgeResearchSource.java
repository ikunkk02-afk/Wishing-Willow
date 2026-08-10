package com.ikunkk02.wishingwillow.research.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import com.ikunkk02.wishingwillow.research.ModFingerprint;
import com.ikunkk02.wishingwillow.research.ResearchConfig;
import com.ikunkk02.wishingwillow.research.ResearchConfigManager;
import com.ikunkk02.wishingwillow.research.ResearchDocument;
import com.ikunkk02.wishingwillow.research.ResearchSource;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class CurseForgeResearchSource implements ResearchProvider {
    private static final String API = "https://api.curseforge.com/v1";
    private final ResearchHttpClient http;

    public CurseForgeResearchSource(ResearchHttpClient http) {
        this.http = http;
    }

    @Override
    public CompletableFuture<SourceResearchResult> research(InstalledModInfo mod, ModFingerprint fingerprint) {
        ResearchConfig config = ResearchConfigManager.getInstance().get();
        if (!config.curseForgeEnabled()) {
            return CompletableFuture.completedFuture(SourceResearchResult.unresolved());
        }
        String uri = API + "/mods/search?gameId=432&classId=6&gameVersion=1.20.1"
                + "&modLoaderType=1&pageSize=5&searchFilter=" + encode(mod.displayName());
        Map<String, String> headers = Map.of("x-api-key", config.curseForgeApiKey());
        return http.get(URI.create(uri), headers).thenCompose(response -> {
            if (response.status() != 200) {
                return CompletableFuture.completedFuture(SourceResearchResult.unresolved());
            }
            List<Candidate> candidates = candidates(mod, response.body());
            if (candidates.isEmpty() || candidates.get(0).score < 0.82
                    || (candidates.size() > 1 && candidates.get(0).score - candidates.get(1).score < 0.12)) {
                return CompletableFuture.completedFuture(SourceResearchResult.unresolved());
            }
            Candidate best = candidates.get(0);
            return http.get(URI.create(API + "/mods/" + best.id + "/description"), headers)
                    .handle((description, throwable) -> {
                        String body = best.summary;
                        if (throwable == null && description.status() == 200) {
                            JsonObject root = JsonParser.parseString(description.body()).getAsJsonObject();
                            if (root.has("data")) {
                                body += "\n\n" + root.get("data").getAsString();
                            }
                        }
                        ResearchDocument document = new ResearchDocument(ResearchSource.CURSEFORGE,
                                best.name, ResearchText.sanitize(body, "text/html"), best.publicUrl);
                        return new SourceResearchResult(true, best.score, best.categories, List.of(document),
                                Set.of(ResearchSource.CURSEFORGE), Integer.toString(best.id));
                    });
        }).exceptionally(throwable -> SourceResearchResult.unresolved());
    }

    private static List<Candidate> candidates(InstalledModInfo mod, String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray data = root.getAsJsonArray("data");
        if (data == null) {
            return List.of();
        }
        List<Candidate> result = new ArrayList<>();
        for (JsonElement element : data) {
            JsonObject value = element.getAsJsonObject();
            List<String> authors = new ArrayList<>();
            if (value.has("authors")) {
                value.getAsJsonArray("authors").forEach(author -> authors.add(string(author.getAsJsonObject(), "name")));
            }
            List<String> categories = new ArrayList<>();
            if (value.has("categories")) {
                value.getAsJsonArray("categories").forEach(category -> categories.add(string(category.getAsJsonObject(), "slug")));
            }
            List<String> files = new ArrayList<>();
            List<String> versions = new ArrayList<>();
            List<String> loaders = new ArrayList<>();
            if (value.has("latestFilesIndexes")) {
                value.getAsJsonArray("latestFilesIndexes").forEach(index -> {
                    JsonObject file = index.getAsJsonObject();
                    files.add(string(file, "filename"));
                    versions.add(string(file, "gameVersion"));
                    if (file.has("modLoader") && file.get("modLoader").getAsInt() == 1) {
                        loaders.add("forge");
                    }
                });
            }
            double score = ProjectMatcher.score(mod, string(value, "name"), string(value, "slug"),
                    String.join(" ", authors), string(value, "summary"), versions, loaders, files);
            JsonObject links = value.has("links") ? value.getAsJsonObject("links") : new JsonObject();
            result.add(new Candidate(value.get("id").getAsInt(), score, string(value, "name"),
                    string(value, "summary"), categories, string(links, "websiteUrl")));
        }
        result.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return result;
    }

    private static String string(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsString() : "";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record Candidate(int id, double score, String name, String summary,
                             List<String> categories, String publicUrl) {
    }
}
