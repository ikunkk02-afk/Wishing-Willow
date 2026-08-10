package com.ikunkk02.wishingwillow.research.web;

import com.ikunkk02.wishingwillow.research.ResearchSource;

public record ResearchSourceTrace(ResearchSource source, SourceTraceOutcome outcome, String detail) {
    public ResearchSourceTrace {
        detail = detail == null ? "" : detail.strip();
    }
}
