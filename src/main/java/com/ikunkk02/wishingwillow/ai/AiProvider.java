package com.ikunkk02.wishingwillow.ai;

import java.util.concurrent.CompletableFuture;

public interface AiProvider {
    AiProviderType type();

    CompletableFuture<AiResponse> complete(AiRequest request);

    default CompletableFuture<AiToolResponse> completeTools(AiToolRequest request) {
        return CompletableFuture.failedFuture(new AiRequestException(AiErrorCategory.UNSUPPORTED_FEATURE, 0, false));
    }

    CompletableFuture<AiModelListResult> listModels();
}
