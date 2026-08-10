package com.ikunkk02.wishingwillow.research.ai;

import com.ikunkk02.wishingwillow.research.ModKnowledge;

import javax.annotation.Nullable;

public record ResearchAnalysisResult(@Nullable ModKnowledge knowledge, String errorCode) {
    public static ResearchAnalysisResult success(ModKnowledge knowledge) {
        return new ResearchAnalysisResult(knowledge, "");
    }

    public static ResearchAnalysisResult failure(String code) {
        return new ResearchAnalysisResult(null, code);
    }
}
