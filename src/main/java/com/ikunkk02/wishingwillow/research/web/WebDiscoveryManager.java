package com.ikunkk02.wishingwillow.research.web;

import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import com.ikunkk02.wishingwillow.research.ModFingerprint;
import com.ikunkk02.wishingwillow.research.ResearchDocument;
import com.ikunkk02.wishingwillow.research.ResearchSource;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import com.ikunkk02.wishingwillow.research.source.ResearchHttpClient;
import com.ikunkk02.wishingwillow.research.web.source.CurseForgePublicWebSource;
import com.ikunkk02.wishingwillow.research.web.source.GitHubPublicWebSource;
import com.ikunkk02.wishingwillow.research.ai.ModWebResearchAgent;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WebDiscoveryManager {
    private final ResearchHttpClient http;
    private final HtmlContentExtractor extractor = new HtmlContentExtractor();
    private final CurseForgePublicWebSource curseForge;
    private final GitHubPublicWebSource github;
    private final ModSearchQueryBuilder queries = new ModSearchQueryBuilder();
    private final ModIdentityResolver resolver = new ModIdentityResolver();
    private final WebResearchCache cache;

    public WebDiscoveryManager(ResearchHttpClient http) { this(http, new WebResearchCache()); }
    WebDiscoveryManager(ResearchHttpClient http, WebResearchCache cache) {
        this.http = http; this.cache = cache;
        this.curseForge = new CurseForgePublicWebSource(http, extractor);
        this.github = new GitHubPublicWebSource(http, extractor);
    }

    public WebDiscoveryResult discover(InstalledModInfo mod, ModFingerprint fingerprint,
                                       RegistrySnapshot registry, String suppliedManualUrl,
                                       boolean force, boolean reserveAiSearches) {
        return discover(mod, fingerprint, registry, suppliedManualUrl, force, reserveAiSearches,
                new WebResearchBudget());
    }

    public WebDiscoveryResult discover(InstalledModInfo mod, ModFingerprint fingerprint,
                                       RegistrySnapshot registry, String suppliedManualUrl,
                                       boolean force, boolean reserveAiSearches, WebResearchBudget budget) {
        List<ResearchDocument> documents = new ArrayList<>();
        Set<ResearchSource> sources = new LinkedHashSet<>();
        List<ResearchSourceTrace> traces = new ArrayList<>();
        Map<String, WebSearchResult> candidates = new LinkedHashMap<>();
        String manualUrl = suppliedManualUrl == null || suppliedManualUrl.isBlank()
                ? cache.loadManualUrl(fingerprint) : suppliedManualUrl.strip();
        if (suppliedManualUrl != null && !suppliedManualUrl.isBlank()) {
            try {
                new UrlSafetyValidator().validate(URI.create(manualUrl), false);
                cache.saveManualUrl(fingerprint, manualUrl);
            } catch (RuntimeException ignored) {
                // It will be reported as a rejected direct source below and is deliberately not persisted.
            }
        }

        List<String> direct = new ArrayList<>();
        if (!manualUrl.isBlank()) direct.add(manualUrl);
        for (String url : List.of(mod.displayUrl(), mod.modUrl(), mod.issueTrackerUrl(), mod.updateUrl())) {
            if (url != null && !url.isBlank() && !direct.contains(url)) direct.add(url);
        }
        for (String url : direct) {
            try {
                WebSearchResult enriched = fetchDirect(mod, fingerprint, url, budget, force, documents, sources);
                candidates.put(enriched.url(), enriched);
                traces.add(new ResearchSourceTrace(sourceFor(url), SourceTraceOutcome.SUCCEEDED, url));
                ModIdentityResolution resolution = resolver.resolve(mod, registry, List.copyOf(candidates.values()));
                if (resolution.level() == IdentityConfidenceLevel.CONFIRMED) return finish(fingerprint, resolution,
                        documents, sources, traces, manualUrl);
            } catch (RuntimeException exception) {
                traces.add(traceFailure(sourceFor(url), exception));
            }
        }

        int initialLimit = reserveAiSearches ? 3 : WebResearchBudget.MAX_SEARCH_QUERIES;
        for (String query : queries.build(mod)) {
            if (budget.searchesUsed() >= initialLimit) break;
            try {
                List<WebSearchResult> found = !force ? cache.loadSearch(fingerprint, "CURSEFORGE", query) : null;
                if (found == null) {
                    found = curseForge.search(query, budget); cache.saveSearch(fingerprint, "CURSEFORGE", query, found);
                }
                found.forEach(result -> candidates.putIfAbsent(result.url(), result));
                traces.add(new ResearchSourceTrace(ResearchSource.CURSEFORGE_PUBLIC_SEARCH,
                        found.isEmpty() ? SourceTraceOutcome.NOT_FOUND : SourceTraceOutcome.SUCCEEDED, query));
                if (!found.isEmpty()) sources.add(ResearchSource.CURSEFORGE_PUBLIC_SEARCH);
            } catch (RuntimeException exception) {
                traces.add(traceFailure(ResearchSource.CURSEFORGE_PUBLIC_SEARCH, exception));
                break;
            }
        }

        ModIdentityResolution preliminary = resolver.resolve(mod, registry, List.copyOf(candidates.values()));
        for (ModIdentityCandidate candidate : preliminary.candidates().stream().filter(value -> !value.rejected())
                .sorted(Comparator.comparingDouble(ModIdentityCandidate::confidence).reversed()).limit(3).toList()) {
            if (!candidate.result().source().equals("CURSEFORGE")) continue;
            try {
                CurseForgePublicWebSource.ProjectPage page = fetchCurseForge(fingerprint, candidate.result().url(), budget, force);
                candidates.put(page.result().url(), page.result());
                documents.add(new ResearchDocument(ResearchSource.CURSEFORGE_PUBLIC_PAGE, page.result().title(),
                        page.page().content(), page.page().finalUrl()));
                sources.add(ResearchSource.CURSEFORGE_PUBLIC_PAGE);
                traces.add(new ResearchSourceTrace(ResearchSource.CURSEFORGE_PUBLIC_PAGE, SourceTraceOutcome.SUCCEEDED,
                        page.page().finalUrl()));
            } catch (RuntimeException exception) {
                traces.add(traceFailure(ResearchSource.CURSEFORGE_PUBLIC_PAGE, exception));
            }
        }

        ModIdentityResolution resolution = resolver.resolve(mod, registry, List.copyOf(candidates.values()));
        if (resolution.level() != IdentityConfidenceLevel.CONFIRMED && !reserveAiSearches) {
            for (String query : queries.build(mod)) {
                if (budget.searchesRemaining() == 0) break;
                try {
                    List<WebSearchResult> found = !force ? cache.loadSearch(fingerprint, "GITHUB", query) : null;
                    if (found == null) { found = github.search(query, budget); cache.saveSearch(fingerprint, "GITHUB", query, found); }
                    found.forEach(result -> candidates.putIfAbsent(result.url(), result));
                    traces.add(new ResearchSourceTrace(ResearchSource.GITHUB_README,
                            found.isEmpty() ? SourceTraceOutcome.NOT_FOUND : SourceTraceOutcome.SUCCEEDED, query));
                    if (!found.isEmpty()) sources.add(ResearchSource.AI_WEB_DISCOVERY);
                } catch (RuntimeException exception) { traces.add(traceFailure(ResearchSource.GITHUB_README, exception)); break; }
            }
            resolution = resolver.resolve(mod, registry, List.copyOf(candidates.values()));
        }
        return finish(fingerprint, resolution, documents, sources, traces, manualUrl);
    }

    public WebResearchToolExecutor toolExecutor(WebResearchBudget budget, List<String> approvedUrls) {
        WebResearchToolExecutor executor = new WebResearchToolExecutor(budget, curseForge, github, http, extractor);
        approvedUrls.forEach(executor::allowCandidate); return executor;
    }

    public WebDiscoveryResult mergeAiDiscovery(InstalledModInfo mod, ModFingerprint fingerprint,
                                               RegistrySnapshot registry, WebDiscoveryResult base,
                                               ModWebResearchAgent.Result ai) {
        Map<String, WebSearchResult> candidates = new LinkedHashMap<>();
        base.report().identity().candidates().forEach(candidate -> candidates.put(candidate.result().url(), candidate.result()));
        ai.results().forEach(result -> candidates.put(result.url(), result));
        List<ResearchDocument> documents = new ArrayList<>(base.documents());
        Set<ResearchSource> sources = new LinkedHashSet<>(base.sources());
        List<ResearchSourceTrace> traces = new ArrayList<>(base.report().sourceTraces());
        for (Map.Entry<String, WebPageDocument> entry : ai.pages().entrySet()) {
            WebPageDocument page = entry.getValue();
            WebSearchResult prior = candidates.getOrDefault(page.finalUrl(), new WebSearchResult(
                    page.title(), page.finalUrl(), "", "", List.of(), List.of(), List.of(), List.of(), "AI_WEB"));
            WebSearchResult enriched = new WebSearchResult(
                    page.title().isBlank() ? prior.title() : page.title(), page.finalUrl(), page.content(), prior.author(),
                    inferVersions(page.content(), prior.gameVersions()), inferLoaders(page.content(), prior.loaders()),
                    prior.categories(), merge(prior.fileNames(), extractJarNames(page.content())), prior.source());
            candidates.put(enriched.url(), enriched);
            ResearchSource source = sourceFor(page.finalUrl());
            documents.add(new ResearchDocument(source, enriched.title(), page.content(), page.finalUrl()));
            sources.add(source); cache.savePage(fingerprint, page.finalUrl(), page);
        }
        if (ai.executedCalls() > 0) {
            sources.add(ResearchSource.AI_WEB_DISCOVERY);
            traces.add(new ResearchSourceTrace(ResearchSource.AI_WEB_DISCOVERY, SourceTraceOutcome.SUCCEEDED,
                    "tool_calls=" + ai.executedCalls()));
        } else if (!ai.errorCode().isBlank()) {
            traces.add(new ResearchSourceTrace(ResearchSource.AI_WEB_DISCOVERY, SourceTraceOutcome.SKIPPED, ai.errorCode()));
        }
        ModIdentityResolution resolution = resolver.resolve(mod, registry, List.copyOf(candidates.values()));
        return finish(fingerprint, resolution, documents, sources, traces, base.report().manualUrl());
    }

    public WebDiscoveryResult withCandidateRanking(WebDiscoveryResult base,
                                                    ModWebResearchAgent.CandidateRanking ranking) {
        if (!ranking.attempted()) return base;
        Set<String> candidateUrls = base.report().identity().candidates().stream()
                .map(candidate -> candidate.result().url()).collect(java.util.stream.Collectors.toSet());
        boolean accepted = !ranking.selectedUrl().isBlank() && candidateUrls.contains(ranking.selectedUrl());
        List<ResearchSourceTrace> traces = new ArrayList<>(base.report().sourceTraces());
        traces.add(new ResearchSourceTrace(ResearchSource.AI_WEB_DISCOVERY,
                accepted ? SourceTraceOutcome.SUCCEEDED : SourceTraceOutcome.REJECTED,
                accepted ? "candidate_ranking=" + ranking.selectedUrl() : ranking.errorCode()));
        Set<ResearchSource> sources = new LinkedHashSet<>(base.sources());
        if (accepted) sources.add(ResearchSource.AI_WEB_DISCOVERY);
        return new WebDiscoveryResult(base.documents(), sources, base.categories(),
                new WebResearchReport(base.report().identity(), traces, base.report().manualUrl()));
    }

    private WebSearchResult fetchDirect(InstalledModInfo mod, ModFingerprint fingerprint, String url,
                                        WebResearchBudget budget, boolean force, List<ResearchDocument> documents,
                                        Set<ResearchSource> sources) {
        String host = URI.create(url).getHost(); host = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (host.endsWith("curseforge.com")) {
            CurseForgePublicWebSource.ProjectPage page = fetchCurseForge(fingerprint, url, budget, force);
            documents.add(new ResearchDocument(ResearchSource.CURSEFORGE_PUBLIC_PAGE, page.result().title(),
                    page.page().content(), page.page().finalUrl())); sources.add(ResearchSource.CURSEFORGE_PUBLIC_PAGE);
            return page.result();
        }
        if (host.equals("github.com")) {
            WebPageDocument page = !force ? cache.loadPage(fingerprint, url) : null;
            if (page == null) { page = github.fetchReadme(url, budget); cache.savePage(fingerprint, url, page); }
            String[] parts = URI.create(url).getPath().split("/");
            String title = parts.length >= 3 ? parts[2] : mod.displayName();
            String author = parts.length >= 2 ? parts[1] : "";
            documents.add(new ResearchDocument(ResearchSource.GITHUB_README, title + " README", page.content(), url));
            sources.add(ResearchSource.GITHUB_README);
            return new WebSearchResult(title, url, page.content(), author, List.of(), List.of(), List.of(), List.of(), "GITHUB");
        }
        WebPageDocument page = !force ? cache.loadPage(fingerprint, url) : null;
        if (page == null) {
            budget.claimFetch(); var response = http.getWeb(URI.create(url), Map.of()).join();
            if (response.status() == 403 || response.status() == 429) throw new IllegalStateException("SOURCE_TEMPORARILY_UNAVAILABLE");
            if (response.status() != 200) throw new IllegalStateException("WEB_HTTP_" + response.status());
            page = extractor.extract(response.body(), response.contentType(), response.finalUri()); cache.savePage(fingerprint, url, page);
        }
        documents.add(new ResearchDocument(ResearchSource.OFFICIAL_WEBPAGE, page.title(), page.content(), page.finalUrl()));
        sources.add(ResearchSource.OFFICIAL_WEBPAGE);
        return new WebSearchResult(page.title(), page.finalUrl(), page.content(), "", List.of(), List.of(), List.of(), List.of(), "OFFICIAL");
    }

    private CurseForgePublicWebSource.ProjectPage fetchCurseForge(ModFingerprint fingerprint, String url,
                                                                  WebResearchBudget budget, boolean force) {
        WebPageDocument cached = !force ? cache.loadPage(fingerprint, url) : null;
        if (cached == null) {
            CurseForgePublicWebSource.ProjectPage page = curseForge.fetchProject(url, budget);
            cache.savePage(fingerprint, url, page.page()); return page;
        }
        WebSearchResult result = new WebSearchResult(cached.title(), url, cached.content(), inferAuthor(cached.content()),
                inferVersions(cached.content(), List.of()), inferLoaders(cached.content(), List.of()),
                List.of(), extractJarNames(cached.content()), "CURSEFORGE");
        return new CurseForgePublicWebSource.ProjectPage(result, cached);
    }

    private WebDiscoveryResult finish(ModFingerprint fingerprint, ModIdentityResolution resolution,
                                      List<ResearchDocument> documents, Set<ResearchSource> sources,
                                      List<ResearchSourceTrace> traces, String manualUrl) {
        cache.saveIdentity(fingerprint, resolution);
        List<String> categories = resolution.candidates().stream()
                .filter(candidate -> candidate.result().url().equals(resolution.selectedUrl()))
                .flatMap(candidate -> candidate.result().categories().stream()).distinct().toList();
        return new WebDiscoveryResult(documents, sources, categories,
                new WebResearchReport(resolution, traces, manualUrl));
    }
    private static ResearchSource sourceFor(String url) {
        String host; try { host = URI.create(url).getHost(); } catch (RuntimeException exception) { return ResearchSource.OFFICIAL_WEBPAGE; }
        host = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (host.endsWith("curseforge.com")) return ResearchSource.CURSEFORGE_PUBLIC_PAGE;
        if (host.equals("github.com") || host.equals("raw.githubusercontent.com")) return ResearchSource.GITHUB_README;
        if (host.endsWith("modrinth.com")) return ResearchSource.MODRINTH_PROJECT;
        return ResearchSource.OFFICIAL_WEBPAGE;
    }
    private static ResearchSourceTrace traceFailure(ResearchSource source, RuntimeException exception) {
        String code = rootMessage(exception);
        SourceTraceOutcome outcome = code.contains("TEMPORARILY") ? SourceTraceOutcome.TEMPORARILY_UNAVAILABLE
                : code.contains("PARSE") ? SourceTraceOutcome.PARSE_FAILED
                : code.contains("NOT_FOUND") ? SourceTraceOutcome.NOT_FOUND : SourceTraceOutcome.REJECTED;
        return new ResearchSourceTrace(source, outcome, code);
    }
    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable; while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
    private static List<String> extractJarNames(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|\\s)([A-Za-z0-9_.+()-]+\\.jar)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        List<String> result = new ArrayList<>(); while (matcher.find() && result.size() < 16) result.add(matcher.group(1).strip()); return result;
    }
    private static List<String> inferVersions(String text, List<String> existing) {
        List<String> values = new ArrayList<>(existing);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?<![0-9])1\\.(?:18|19|20|21)(?:\\.\\d+)?(?![0-9])").matcher(text);
        while (matcher.find() && values.size() < 16) if (!values.contains(matcher.group())) values.add(matcher.group());
        return values;
    }
    private static String inferAuthor(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)\\bby\\s+([A-Za-z0-9_.-]{2,64})").matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }
    private static List<String> inferLoaders(String text, List<String> existing) {
        List<String> values = new ArrayList<>(existing); String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("forge") && !values.contains("forge")) values.add("forge");
        if (lower.contains("fabric") && !values.contains("fabric")) values.add("fabric");
        if (lower.contains("neoforge") && !values.contains("neoforge")) values.add("neoforge"); return values;
    }
    private static List<String> merge(List<String> left, List<String> right) {
        List<String> values = new ArrayList<>(left); right.forEach(value -> { if (!values.contains(value)) values.add(value); }); return values;
    }
}
