package com.ikunkk02.wishingwillow.ai;

import com.ikunkk02.wishingwillow.ai.prompt.WishingWillowPrompt;

import java.util.concurrent.CompletableFuture;

public final class WishInterpreter {
    private final AiService service;

    public WishInterpreter(AiService service) {
        this.service = service;
    }

    public CompletableFuture<WishInterpretationResult> interpret(AiConfig config, String wish) {
        if (!config.isConfigured()) {
            return CompletableFuture.completedFuture(
                    WishInterpretationResult.requestFailure(AiErrorCategory.NOT_CONFIGURED, 0)
            );
        }
        AiRequest request = new AiRequest(
                WishingWillowPrompt.SYSTEM_PROMPT,
                WishingWillowPrompt.untrustedWishMessage(wish),
                1200,
                AiOutputMode.JSON_SCHEMA,
                WishInterpretationValidator.jsonSchema()
        );
        return service.provider(config).complete(request).handle((response, throwable) -> {
            if (throwable != null) {
                AiRequestException failure = unwrap(throwable);
                if (failure.category() == AiErrorCategory.MALFORMED_RESPONSE
                        || failure.category() == AiErrorCategory.RESPONSE_TOO_LARGE
                        || failure.category() == AiErrorCategory.EMPTY_RESPONSE) {
                    return new WishInterpretationResult(
                            InterpretationState.INVALID_RESPONSE,
                            failure.category(),
                            failure.httpStatus(),
                            null
                    );
                }
                return WishInterpretationResult.requestFailure(failure.category(), failure.httpStatus());
            }
            try {
                return WishInterpretationResult.success(
                        WishInterpretationValidator.parseAndValidate(response.assistantContent())
                );
            } catch (IllegalArgumentException exception) {
                return WishInterpretationResult.invalidResponse();
            }
        });
    }

    private static AiRequestException unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof AiRequestException requestException
                ? requestException
                : new AiRequestException(AiErrorCategory.UNKNOWN, current);
    }
}
