package com.ikunkk02.wishingwillow.research.web;

import com.ikunkk02.wishingwillow.research.ResearchDocument;
import com.ikunkk02.wishingwillow.research.ResearchSource;

import java.util.List;
import java.util.Set;

public record WebDiscoveryResult(
        List<ResearchDocument> documents,
        Set<ResearchSource> sources,
        List<String> categories,
        WebResearchReport report
) {
    public WebDiscoveryResult {
        documents = List.copyOf(documents); sources = Set.copyOf(sources);
        categories = List.copyOf(categories); report = report == null ? WebResearchReport.empty() : report;
    }
}
