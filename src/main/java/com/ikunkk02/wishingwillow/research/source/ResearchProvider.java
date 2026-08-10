package com.ikunkk02.wishingwillow.research.source;

import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import com.ikunkk02.wishingwillow.research.ModFingerprint;

import java.util.concurrent.CompletableFuture;

public interface ResearchProvider {
    CompletableFuture<SourceResearchResult> research(InstalledModInfo mod, ModFingerprint fingerprint);
}
