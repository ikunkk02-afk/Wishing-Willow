package com.ikunkk02.wishingwillow.ai;

import java.util.concurrent.CompletableFuture;

public interface AiProvider {
    AiProviderType type();

    CompletableFuture<AiResponse> complete(AiRequest request);

    CompletableFuture<AiModelListResult> listModels();
}
