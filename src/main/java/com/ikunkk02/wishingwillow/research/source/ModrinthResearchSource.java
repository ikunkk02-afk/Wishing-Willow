package com.ikunkk02.wishingwillow.research.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import com.ikunkk02.wishingwillow.research.ModFingerprint;
import com.ikunkk02.wishingwillow.research.ResearchDocument;
import com.ikunkk02.wishingwillow.research.ResearchSource;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModrinthResearchSource implements ResearchProvider {
    private static final String DEFAULT_API = "https://api.modrinth.com/v2";
    private static final Pattern GITHUB_REPOSITORY = Pattern.compile(
            "https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?(?:[?#].*)?"
    );
    private final ResearchHttpClient http;
    private final String api;

    public ModrinthResearchSource(ResearchHttpClient http) {
        this(http, DEFAULT_API);
    }

    ModrinthResearchSource(ResearchHttpClient http, String api) {
        this.http = http;
        this.api = api;
    }

    @Override
    public CompletableFuture<SourceResearchResult> research(InstalledModInfo mod, ModFingerprint fingerprint) {
        if (!fingerprint.available()) {
            return fuzzy(mod);
        }
        URI uri = URI.create(api + "/version_file/" + fingerprint.sha512() + "?algorithm=sha512");
        return http.get(uri, Map.of()).thenCompose(response -> {
            if (response.status() == 200) {
                JsonObject version = object(response.body());
                String projectId = string(version, "project_id");
                return project(mod, projectId, 1.0, ResearchSource.MODRINTH_HASH);
            }
            if (response.status() == 404) {
                return fuzzy(mod);
            }
            return CompletableFuture.completedFuture(SourceResearchResult.unresolved());
        }).exceptionally(throwable -> SourceResearchResult.unresolved());
    }

    private CompletableFuture<SourceResearchResult> fuzzy(InstalledModInfo mod) {
        CompletableFuture<List<Candidate>> byName = search(mod, mod.displayName());
        CompletableFuture<List<Candidate>> byId = mod.modId().equalsIgnoreCase(mod.displayName())
                ? CompletableFuture.completedFuture(List.of()) : search(mod, mod.modId());
        return byName.thenCombine(byId, (left, right) -> {
            Map<String, Candidate> unique = new HashMap<>();
            left.forEach(candidate -> unique.merge(candidate.projectId, candidate, Candidate::better));
            right.forEach(candidate -> unique.merge(candidate.projectId, candidate, Candidate::better));
            return unique.values().stream().sorted(Comparator.comparingDouble(Candidate::score).reversed()).toList();
        }).thenCompose(candidates -> {
            if (candidates.isEmpty() || candidates.get(0).score < 0.82
                    || (candidates.size() > 1 && candidates.get(0).score - candidates.get(1).score < 0.12)) {
                return CompletableFuture.completedFuture(SourceResearchResult.unresolved());
            }
            return project(mod, candidates.get(0).projectId, candidates.get(0).score, ResearchSource.MODRINTH_SEARCH);
        });
    }

    private CompletableFuture<List<Candidate>> search(InstalledModInfo mod, String query) {
        String facets = "[[\"project_type:mod\"],[\"versions:1.20.1\"],[\"categories:forge\"]]";
        String uri = api + "/search?limit=5&query=" + encode(query) + "&facets=" + encode(facets);
        return http.get(URI.create(uri), Map.of()).handle((response, throwable) -> {
            if (throwable != null || response.status() != 200) {
                return List.of();
            }
            List<Candidate> values = new ArrayList<>();
            JsonArray hits = object(response.body()).getAsJsonArray("hits");
            if (hits == null) {
                return values;
            }
            for (JsonElement element : hits) {
                JsonObject hit = element.getAsJsonObject();
                List<String> categories = new ArrayList<>(strings(hit, "categories"));
                categories.addAll(strings(hit, "display_categories"));
                List<String> versions = strings(hit, "versions");
                String projectType = string(hit, "project_type");
                boolean isMod = "mod".equals(projectType) || strings(hit, "all_project_types").contains("mod");
                if (!isMod || !categories.contains("forge") || !versions.contains("1.20.1")) {
                    continue;
                }
                double score = ProjectMatcher.score(mod, string(hit, "title"), string(hit, "slug"),
                        string(hit, "author"), string(hit, "description"), versions, categories, List.of());
                values.add(new Candidate(string(hit, "project_id"), score));
            }
            return values;
        });
    }

    private CompletableFuture<SourceResearchResult> project(InstalledModInfo mod, String projectId,
                                                              double confidence, ResearchSource identitySource) {
        if (projectId.isBlank()) {
            return CompletableFuture.completedFuture(SourceResearchResult.unresolved());
        }
        return http.get(URI.create(api + "/project/" + encode(projectId)), Map.of()).thenCompose(response -> {
            if (response.status() != 200) {
                return CompletableFuture.completedFuture(SourceResearchResult.unresolved());
            }
            JsonObject project = object(response.body());
            JsonObject selected = new JsonObject();
            for (String field : List.of("id", "slug", "title", "description", "body", "categories",
                    "additional_categories", "source_url", "wiki_url", "issues_url", "loaders",
                    "game_versions", "license")) {
                if (project.has(field)) {
                    selected.add(field, project.get(field));
                }
            }
            String publicUrl = "https://modrinth.com/mod/" + string(project, "slug");
            ResearchDocument document = new ResearchDocument(ResearchSource.MODRINTH_PROJECT,
                    string(project, "title"), ResearchText.sanitize(selected.toString(), "application/json"), publicUrl);
            Set<ResearchSource> sources = new LinkedHashSet<>();
            sources.add(identitySource);
            sources.add(ResearchSource.MODRINTH_PROJECT);
            List<String> categories = new ArrayList<>(strings(project, "categories"));
            categories.addAll(strings(project, "additional_categories"));
            List<ResearchDocument> documents = new ArrayList<>();
            documents.add(document);
            SourceResearchResult base = new SourceResearchResult(true, confidence, categories, documents, sources, projectId);
            Matcher matcher = GITHUB_REPOSITORY.matcher(string(project, "source_url"));
            if (!matcher.matches()) {
                return CompletableFuture.completedFuture(base);
            }
            URI readme = URI.create("https://api.github.com/repos/" + encode(matcher.group(1))
                    + "/" + encode(matcher.group(2)) + "/readme");
            return http.get(readme, Map.of("Accept", "application/vnd.github.raw+json"))
                    .handle((readmeResponse, throwable) -> {
                        if (throwable == null && readmeResponse.status() == 200) {
                            documents.add(new ResearchDocument(ResearchSource.GITHUB_README,
                                    string(project, "title") + " README",
                                    ResearchText.sanitize(readmeResponse.body(), "text/markdown"),
                                    string(project, "source_url")));
                            sources.add(ResearchSource.GITHUB_README);
                        }
                        return new SourceResearchResult(true, confidence, categories, documents, sources, projectId);
                    });
        });
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static String string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static List<String> strings(JsonObject object, String name) {
        JsonArray array = object.getAsJsonArray(name);
        if (array == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record Candidate(String projectId, double score) {
        private static Candidate better(Candidate left, Candidate right) {
            return left.score >= right.score ? left : right;
        }
    }
}
