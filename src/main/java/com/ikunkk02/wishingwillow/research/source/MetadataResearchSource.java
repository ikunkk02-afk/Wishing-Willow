package com.ikunkk02.wishingwillow.research.source;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import com.ikunkk02.wishingwillow.research.ModFingerprint;
import com.ikunkk02.wishingwillow.research.ResearchDocument;
import com.ikunkk02.wishingwillow.research.ResearchSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class MetadataResearchSource implements ResearchProvider {
    public MetadataResearchSource(ResearchHttpClient http) { }

    @Override
    public CompletableFuture<SourceResearchResult> research(InstalledModInfo mod, ModFingerprint fingerprint) {
        List<ResearchDocument> base = new ArrayList<>();
        base.add(new ResearchDocument(ResearchSource.LOCAL_METADATA, mod.displayName(), metadataText(mod), mod.displayUrl()));
        return CompletableFuture.completedFuture(new SourceResearchResult(false, 0.0, List.of(), base,
                Set.of(ResearchSource.LOCAL_METADATA), ""));
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
        json.addProperty("mod_url", mod.modUrl());
        json.addProperty("issue_tracker_url", mod.issueTrackerUrl());
        json.addProperty("update_url", mod.updateUrl());
        json.addProperty("file_name", mod.fileName());
        json.addProperty("minecraft_version", mod.minecraftVersion());
        json.addProperty("loader", mod.loader());
        return json.toString();
    }
}
