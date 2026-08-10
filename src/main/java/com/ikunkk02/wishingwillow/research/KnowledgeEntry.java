package com.ikunkk02.wishingwillow.research;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

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
        long updatedAt
) {
    public KnowledgeEntry {
        sources = Set.copyOf(sources);
        documents = List.copyOf(documents);
    }

    public static KnowledgeEntry scanned(InstalledModInfo info, ModFingerprint fingerprint, ModCategory category) {
        return new KnowledgeEntry(1, info, fingerprint, category, ResearchState.NOT_STARTED,
                KnowledgeLevel.UNKNOWN, Set.of(ResearchSource.LOCAL_METADATA), List.of(), null, "", "",
                System.currentTimeMillis());
    }

    public KnowledgeEntry withState(ResearchState newState) {
        return new KnowledgeEntry(schemaVersion, installed, fingerprint, category, newState, knowledgeLevel,
                sources, documents, knowledge, registryDigest, errorCode, System.currentTimeMillis());
    }

    public KnowledgeEntry withResearch(ModCategory newCategory, ResearchState newState, KnowledgeLevel newLevel,
                                       Set<ResearchSource> newSources, List<ResearchDocument> newDocuments,
                                       @Nullable ModKnowledge newKnowledge, String newDigest, String newError) {
        return new KnowledgeEntry(1, installed, fingerprint, newCategory, newState, newLevel, newSources,
                newDocuments, newKnowledge, newDigest, newError, System.currentTimeMillis());
    }
}
