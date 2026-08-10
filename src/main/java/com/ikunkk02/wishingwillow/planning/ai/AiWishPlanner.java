package com.ikunkk02.wishingwillow.planning.ai;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiOutputMode;
import com.ikunkk02.wishingwillow.ai.AiRequest;
import com.ikunkk02.wishingwillow.ai.AiRequestException;
import com.ikunkk02.wishingwillow.ai.AiService;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.PlanningEnvironment;
import com.ikunkk02.wishingwillow.planning.WishContextSnapshot;
import com.ikunkk02.wishingwillow.planning.WishPlanError;
import com.ikunkk02.wishingwillow.planning.WishPlanResult;
import com.ikunkk02.wishingwillow.planning.WishPlanValidation;
import com.ikunkk02.wishingwillow.planning.WishPlanValidator;

import java.util.concurrent.CompletableFuture;

public final class AiWishPlanner {
    private final AiService service;

    public AiWishPlanner(AiService service) { this.service = service; }

    public CompletableFuture<WishPlanResult> plan(AiConfig config, String originalWish,
                                                  WishInterpretation interpretation,
                                                  WishContextSnapshot context,
                                                  CapabilityCatalog catalog,
                                                  PlanningEnvironment environment) {
        if (!config.isConfigured()) return CompletableFuture.completedFuture(WishPlanResult.failed(WishPlanError.AI_REQUEST_FAILED));
        if (catalog.candidates().isEmpty()) return CompletableFuture.completedFuture(WishPlanResult.failed(WishPlanError.NO_CANDIDATES));
        AiRequest request = new AiRequest(WishPlannerPrompt.SYSTEM_PROMPT,
                WishPlannerPrompt.userMessage(originalWish, interpretation, context, catalog),
                2800, AiOutputMode.JSON_SCHEMA, WishPlannerPrompt.jsonSchema(catalog));
        return service.provider(config).complete(request).handle((response, throwable) -> {
            if (throwable != null || response == null) {
                AiErrorCategory category = category(throwable);
                return WishPlanResult.failed(category == AiErrorCategory.TIMEOUT
                        ? WishPlanError.AI_TIMEOUT : WishPlanError.AI_REQUEST_FAILED);
            }
            try {
                WishPlanValidation validation = WishPlanValidator.parseAndValidate(
                        response.assistantContent(), interpretation, catalog, environment);
                return WishPlanResult.success(validation.draft());
            } catch (IllegalArgumentException exception) {
                return WishPlanResult.failed(parseError(exception));
            }
        });
    }

    private static WishPlanError parseError(IllegalArgumentException exception) {
        try { return WishPlanError.valueOf(exception.getMessage()); }
        catch (RuntimeException ignored) { return WishPlanError.INVALID_JSON; }
    }

    private static AiErrorCategory category(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) current = current.getCause();
        return current instanceof AiRequestException request ? request.category() : AiErrorCategory.UNKNOWN;
    }
}
