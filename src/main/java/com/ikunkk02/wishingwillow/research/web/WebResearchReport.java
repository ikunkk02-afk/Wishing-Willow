package com.ikunkk02.wishingwillow.research.web;

import java.util.List;

public record WebResearchReport(
        ModIdentityResolution identity,
        List<ResearchSourceTrace> sourceTraces,
        String manualUrl
) {
    public WebResearchReport {
        identity = identity == null ? ModIdentityResolution.unresolved("NOT_RESEARCHED") : identity;
        sourceTraces = List.copyOf(sourceTraces == null ? List.of() : sourceTraces);
        manualUrl = manualUrl == null ? "" : manualUrl.strip();
    }

    public static WebResearchReport empty() {
        return new WebResearchReport(ModIdentityResolution.unresolved("NOT_RESEARCHED"), List.of(), "");
    }
}
