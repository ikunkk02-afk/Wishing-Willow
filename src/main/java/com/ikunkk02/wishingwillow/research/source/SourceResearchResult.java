package com.ikunkk02.wishingwillow.research.source;

import com.ikunkk02.wishingwillow.research.ResearchDocument;
import com.ikunkk02.wishingwillow.research.ResearchSource;

import java.util.List;
import java.util.Set;

public record SourceResearchResult(
        boolean identified,
        double matchConfidence,
        List<String> categories,
        List<ResearchDocument> documents,
        Set<ResearchSource> sources,
        String projectId
) {
    public SourceResearchResult {
        categories = List.copyOf(categories);
        documents = List.copyOf(documents);
        sources = Set.copyOf(sources);
    }

    public static SourceResearchResult unresolved() {
        return new SourceResearchResult(false, 0.0, List.of(), List.of(), Set.of(), "");
    }
}
