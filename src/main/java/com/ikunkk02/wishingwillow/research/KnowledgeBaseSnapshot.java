package com.ikunkk02.wishingwillow.research;

import java.util.List;

public record KnowledgeBaseSnapshot(KnowledgeBaseState state, boolean paused, List<KnowledgeEntry> entries) {
    public KnowledgeBaseSnapshot {
        entries = List.copyOf(entries);
    }

    public long count(ResearchState researchState) {
        return entries.stream().filter(entry -> entry.state() == researchState).count();
    }
}
