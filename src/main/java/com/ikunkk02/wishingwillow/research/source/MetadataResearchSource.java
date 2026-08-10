package com.ikunkk02.wishingwillow.research.source;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import com.ikunkk02.wishingwillow.research.ModFingerprint;
import com.ikunkk02.wishingwillow.research.ResearchDocument;
import com.ikunkk02.wishingwillow.research.ResearchSource;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MetadataResearchSource implements ResearchProvider {
    private static final Pattern GITHUB_REPOSITORY = Pattern.compile(
            "https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?(?:[?#].*)?"
    );
    private final ResearchHttpClient http;

    public MetadataResearchSource(ResearchHttpClient http) {
        this.http = http;
    }

    @Override
    public CompletableFuture<SourceResearchResult> research(InstalledModInfo mod, ModFingerprint fingerprint) {
        List<ResearchDocument> base = new ArrayList<>();
        base.add(new ResearchDocument(ResearchSource.LOCAL_METADATA, mod.displayName(), metadataText(mod), mod.displayUrl()));
        Matcher matcher = GITHUB_REPOSITORY.matcher(mod.displayUrl());
        if (!matcher.matches()) {
            return CompletableFuture.completedFuture(new SourceResearchResult(false, 0.0, List.of(), base,
                    Set.of(ResearchSource.LOCAL_METADATA), ""));
        }
        String owner = URLEncoder.encode(matcher.group(1), StandardCharsets.UTF_8);
        String repository = URLEncoder.encode(matcher.group(2), StandardCharsets.UTF_8);
        URI uri = URI.create("https://api.github.com/repos/" + owner + "/" + repository + "/readme");
        return http.get(uri, Map.of("Accept", "application/vnd.github.raw+json"))
                .handle((response, throwable) -> {
                    if (throwable == null && response.status() == 200) {
                        base.add(new ResearchDocument(ResearchSource.SOURCE_README,
                                mod.displayName() + " README",
                                ResearchText.sanitize(response.body(), "text/markdown"), mod.displayUrl()));
                        return new SourceResearchResult(false, 0.0, List.of(), base,
                                Set.of(ResearchSource.LOCAL_METADATA, ResearchSource.SOURCE_README), "");
                    }
                    return new SourceResearchResult(false, 0.0, List.of(), base,
                            Set.of(ResearchSource.LOCAL_METADATA), "");
                });
    }

    private static String metadataText(InstalledModInfo mod) {
        JsonObject json = new JsonObject();
        json.addProperty("mod_id", mod.modId());
        json.addProperty("name", mod.displayName());
        json.addProperty("version", mod.version());
        json.addProperty("description", mod.description());
        json.add("authors", com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(mod.authors())));
        json.addProperty("license", mod.license());
        json.addProperty("display_url", mod.displayUrl());
        json.addProperty("update_url", mod.updateUrl());
        json.addProperty("file_name", mod.fileName());
        return json.toString();
    }
}
