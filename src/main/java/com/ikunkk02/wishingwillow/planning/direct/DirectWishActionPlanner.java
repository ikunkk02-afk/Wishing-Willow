package com.ikunkk02.wishingwillow.planning.direct;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiOutputMode;
import com.ikunkk02.wishingwillow.ai.AiProvider;
import com.ikunkk02.wishingwillow.ai.AiRequest;
import com.ikunkk02.wishingwillow.ai.AiRequestException;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.WishExecutionRoute;
import com.ikunkk02.wishingwillow.planning.WishPlanError;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

/** Exactly one normal Action request and, when validation fails, at most one repair request. */
@Deprecated(forRemoval = false)
public final class DirectWishActionPlanner {
    public static final int MAX_ATTEMPTS = 2;
    public static final int REQUEST_TIMEOUT_SECONDS = 8;
    private final DirectActionPlanCompiler compiler;

    public DirectWishActionPlanner() {
        this(new DirectActionPlanCompiler());
    }

    DirectWishActionPlanner(DirectActionPlanCompiler compiler) {
        this.compiler = compiler;
    }

    public CompletableFuture<DirectActionPlanningResult> plan(
            AiProvider provider,
            String originalWish,
            WishInterpretation interpretation,
            CapabilityCatalog initialCatalog,
            RegistrySnapshot registry,
            ExecutionSettingsSnapshot settings
    ) {
        return plan(null, provider, originalWish, interpretation, initialCatalog, registry, settings);
    }

    public CompletableFuture<DirectActionPlanningResult> plan(
            UUID sessionId, AiProvider provider, String originalWish, WishInterpretation interpretation,
            CapabilityCatalog initialCatalog, RegistrySnapshot registry, ExecutionSettingsSnapshot settings
    ) {
        WishingWillow.LOGGER.info("Direct action planning started session={} coreOutcome={}", sessionId,
                clean(interpretation.contract().requiredOutcome(), 160));
        AiRequest request = new AiRequest(DirectActionJson.systemPrompt(),
                DirectActionJson.userMessage(originalWish, interpretation, registry, settings),
                2200, AiOutputMode.JSON_SCHEMA, DirectActionJson.schema());
        return provider.complete(request).orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).handle((response, error) -> {
            if (error != null || response == null) {
                return CompletableFuture.completedFuture(failedProvider(error, 1));
            }
            return parseCompileOrRepair(sessionId, provider, originalWish, interpretation, initialCatalog,
                    registry, settings, response.assistantContent(), 1);
        }).thenCompose(value -> value);
    }

    private CompletableFuture<DirectActionPlanningResult> parseCompileOrRepair(
            UUID sessionId, AiProvider provider, String originalWish, WishInterpretation interpretation,
            CapabilityCatalog initialCatalog, RegistrySnapshot registry, ExecutionSettingsSnapshot settings,
            String raw, int attempt
    ) {
        try {
            DirectActionPlan parsed = DirectActionJson.parse(raw);
            WishingWillow.LOGGER.info("Direct action received session={} route={} actions={} modifiers={} attempt={}",
                    sessionId, parsed.route(), parsed.actions().stream().map(action -> action.type().name()).toList(),
                    parsed.absurdity().modifiers().stream().map(action -> action.type().name()).toList(), attempt);
            if (parsed.route() == WishExecutionRoute.COMPLEX_AGENT) {
                return CompletableFuture.completedFuture(DirectActionPlanningResult.unsupported(
                        "planner_reported_unexpressible_action", attempt));
            }
            CompiledDirectActionPlan compiled = compiler.compile(parsed, interpretation, initialCatalog,
                    registry, settings);
            WishingWillow.LOGGER.info("Direct action validation session={} state=VALID actions={} droppedModifiers={}",
                    sessionId, compiled.directActions(), compiled.droppedModifiers());
            WishingWillow.LOGGER.info("Absurdity profile session={} style={} intensity={} modifiers={}",
                    sessionId, compiled.absurdity().style(), compiled.absurdity().intensity(),
                    compiled.absurdity().modifiers().stream().map(action -> action.type().name()).toList());
            return CompletableFuture.completedFuture(DirectActionPlanningResult.success(compiled, attempt));
        } catch (IllegalArgumentException invalid) {
            WishPlanError error = planError(invalid);
            if (error == WishPlanError.UNSUPPORTED_ACTION) {
                return CompletableFuture.completedFuture(DirectActionPlanningResult.unsupported(
                        "direct_dsl_cannot_prove_contract", attempt));
            }
            if (attempt >= MAX_ATTEMPTS) {
                WishingWillow.LOGGER.warn("Direct action validation state=FAILED error={} detail={} attempts={}",
                        error, validationDetail(invalid), attempt);
                return CompletableFuture.completedFuture(DirectActionPlanningResult.failed(error,
                        validationDetail(invalid), attempt));
            }
            WishingWillow.LOGGER.info("Direct action repair started error={} detail={} attempt=2", error,
                    validationDetail(invalid));
            AiRequest repair = new AiRequest(DirectActionJson.systemPrompt()
                    + "\nRepair exactly the named validation error. Preserve the same core outcome. Return JSON only.",
                    DirectActionJson.repairMessage(originalWish, interpretation, registry, settings,
                            error.name(), validationDetail(invalid), raw), 2200,
                    AiOutputMode.JSON_SCHEMA, DirectActionJson.schema());
            return provider.complete(repair).orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).handle((response, providerError) -> {
                if (providerError != null || response == null) {
                    return CompletableFuture.completedFuture(failedProvider(providerError, 2));
                }
                return parseCompileOrRepair(sessionId, provider, originalWish, interpretation, initialCatalog,
                        registry, settings, response.assistantContent(), 2);
            }).thenCompose(value -> value);
        }
    }

    private static DirectActionPlanningResult failedProvider(Throwable error, int attempts) {
        Throwable cause = root(error);
        WishPlanError planError = cause instanceof TimeoutException
                || cause instanceof AiRequestException request && request.category() == AiErrorCategory.TIMEOUT
                ? WishPlanError.AI_TIMEOUT
                : WishPlanError.AI_REQUEST_FAILED;
        return DirectActionPlanningResult.failed(planError,
                cause == null ? "provider_failed" : cause.getClass().getSimpleName(), attempts);
    }

    private static WishPlanError planError(IllegalArgumentException error) {
        String message = error.getMessage();
        String code = message == null ? "" : message.split("\\|", 2)[0];
        try { return WishPlanError.valueOf(code); }
        catch (RuntimeException ignored) { return WishPlanError.INVALID_JSON; }
    }

    private static String validationDetail(IllegalArgumentException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "validation_failed";
        int separator = message.indexOf('|');
        return clean(separator < 0 ? message : message.substring(separator + 1), 160);
    }

    private static Throwable root(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static String clean(String value, int max) {
        String clean = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').strip();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
