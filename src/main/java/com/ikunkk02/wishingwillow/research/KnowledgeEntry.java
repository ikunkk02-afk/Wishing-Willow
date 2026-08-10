package com.ikunkk02.wishingwillow.research;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import com.ikunkk02.wishingwillow.research.web.WebResearchReport;

public record KnowledgeEntry(
        int schemaVersion,
        InstalledModInfo installed,
        ModFingerprint fingerprint,
        ModCategory category,
        ResearchState state,
        KnowledgeLevel knowledgeLevel,
        Set<ResearchSource> sources,
        List<ResearchDocument> documents,
        @Nullable ModKnowledge knowledge,
        String registryDigest,
        String errorCode,
        long updatedAt,
        WebResearchReport webResearch
) {
    public KnowledgeEntry {
        sources = Set.copyOf(sources);
        documents = List.copyOf(documents);
        webResearch = webResearch == null ? WebResearchReport.empty() : webResearch;
    }

    public KnowledgeEntry(int schemaVersion, InstalledModInfo installed, ModFingerprint fingerprint,
                          ModCategory category, ResearchState state, KnowledgeLevel knowledgeLevel,
                          Set<ResearchSource> sources, List<ResearchDocument> documents,
                          @Nullable ModKnowledge knowledge, String registryDigest, String errorCode, long updatedAt) {
        this(schemaVersion, installed, fingerprint, category, state, knowledgeLevel, sources, documents,
                knowledge, registryDigest, errorCode, updatedAt, WebResearchReport.empty());
    }

    public static KnowledgeEntry scanned(InstalledModInfo info, ModFingerprint fingerprint, ModCategory category) {
        return new KnowledgeEntry(2, info, fingerprint, category, ResearchState.NOT_STARTED,
                KnowledgeLevel.UNKNOWN, Set.of(ResearchSource.LOCAL_METADATA), List.of(), null, "", "",
                System.currentTimeMillis(), WebResearchReport.empty());
    }

    public KnowledgeEntry withState(ResearchState newState) {
        return new KnowledgeEntry(2, installed, fingerprint, category, newState, knowledgeLevel,
                sources, documents, knowledge, registryDigest, errorCode, System.currentTimeMillis(), webResearch);
    }

    public KnowledgeEntry withResearch(ModCategory newCategory, ResearchState newState, KnowledgeLevel newLevel,
                                       Set<ResearchSource> newSources, List<ResearchDocument> newDocuments,
                                       @Nullable ModKnowledge newKnowledge, String newDigest, String newError) {
        return new KnowledgeEntry(2, installed, fingerprint, newCategory, newState, newLevel, newSources,
                newDocuments, newKnowledge, newDigest, newError, System.currentTimeMillis(), webResearch);
    }

    public KnowledgeEntry withWebResearch(WebResearchReport report) {
        return new KnowledgeEntry(2, installed, fingerprint, category, state, knowledgeLevel, sources,
                documents, knowledge, registryDigest, errorCode, System.currentTimeMillis(), report);
    }
}
