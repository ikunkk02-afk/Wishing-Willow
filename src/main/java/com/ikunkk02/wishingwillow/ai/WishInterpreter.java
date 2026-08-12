package com.ikunkk02.wishingwillow.ai;

import com.ikunkk02.wishingwillow.ai.prompt.WishingWillowPrompt;
import com.ikunkk02.wishingwillow.ai.prompt.WishingWillowPromptAssembler;
import com.ikunkk02.wishingwillow.ai.prompt.WishingWillowRuntimeContext;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.program.WishProgramNormalizationException;
import com.ikunkk02.wishingwillow.program.WishProgramValidationIssue;
import com.ikunkk02.wishingwillow.program.skill.WishSkillRegistry;
import com.ikunkk02.wishingwillow.wish.WishLifecycleLog;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class WishInterpreter {
    public static final int MAX_ATTEMPTS = 3;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Function<AiConfig, AiProvider> providers;

    public WishInterpreter(AiService service) {
        this(service::provider);
    }

    WishInterpreter(Function<AiConfig, AiProvider> providers) {
        this.providers = providers;
    }

    public CompletableFuture<WishInterpretationResult> interpret(AiConfig config, String wish) {
        return interpret(config, wish, WishFulfillmentMode.ABSURD);
    }

    public CompletableFuture<WishInterpretationResult> interpret(AiConfig config, String wish,
                                                                 WishFulfillmentMode mode) {
        return interpret(config, wish, mode, null);
    }

    public CompletableFuture<WishInterpretationResult> interpret(AiConfig config, String wish,
                                                                 WishFulfillmentMode mode,
                                                                 @Nullable UUID sessionId) {
        return interpret(config, wish, mode, sessionId, WishingWillowRuntimeContext.minimal());
    }

    public CompletableFuture<WishInterpretationResult> interpret(AiConfig config, String wish,
                                                                 WishFulfillmentMode mode,
                                                                 @Nullable UUID sessionId,
                                                                 WishingWillowRuntimeContext runtimeContext) {
        if (!config.isConfigured()) {
            return CompletableFuture.completedFuture(
                    WishInterpretationResult.requestFailure(AiErrorCategory.NOT_CONFIGURED, 0)
            );
        }
        WishingWillowPromptAssembler.AssembledPrompt prompt = understandingPrompt(
                wish, mode, runtimeContext, WishingWillowPromptAssembler.RequestKind.INTERPRETATION, null);
        logPrompt(sessionId, prompt, wish);
        AiRequest request = new AiRequest(
                prompt.systemMessage(),
                prompt.userMessage(),
                2200,
                AiOutputMode.JSON_SCHEMA,
                WishUnderstandingJson.jsonSchema()
        );
        AiProvider provider = providers.apply(config);
        return provider.complete(request).handle((response, throwable) -> {
            if (throwable != null) {
                AiRequestException failure = unwrap(throwable);
                if (repairableProviderFailure(failure.category())) {
                    LOGGER.info("AI interpretation repair started cause={}", failure.category());
                    return repair(provider, wish, mode, "", 2, sessionId, runtimeContext,
                            genericRepairIssue(failure.category().name()));
                }
                return CompletableFuture.completedFuture(failureResult(throwable));
            }
            try {
                if (sessionId != null) {
                    WishLifecycleLog.event(sessionId, "AI_RESPONSE_RECEIVED", "attempt=1");
                }
                WishUnderstandingJson.Understanding understanding = WishUnderstandingJson.parse(
                        response.assistantContent(), sessionId);
                return CompletableFuture.completedFuture(WishInterpretationResult.success(
                        understanding.interpretation(), understanding.program()));
            } catch (IllegalArgumentException exception) {
                LOGGER.info("AI interpretation repair started cause=SCHEMA_VALIDATION detail={} attempt=2",
                        validationDetail(exception));
                return repair(provider, wish, mode, response.assistantContent(), 2, sessionId, runtimeContext,
                        repairIssue(exception));
            }
        }).thenCompose(Function.identity());
    }

    private static CompletableFuture<WishInterpretationResult> repair(
            AiProvider provider,
            String wish,
            WishFulfillmentMode mode,
            String invalidCandidate,
            int attempt,
            @Nullable UUID sessionId,
            WishingWillowRuntimeContext runtimeContext,
            WishProgramValidationIssue issue
    ) {
        String repairPayload = WishingWillowPrompt.repairMessage(wish, mode, invalidCandidate, issue);
        WishingWillowPromptAssembler.AssembledPrompt prompt = understandingPrompt(wish, mode, runtimeContext,
                WishingWillowPromptAssembler.RequestKind.REPAIR, repairPayload);
        logPrompt(sessionId, prompt, wish);
        AiRequest repairRequest = new AiRequest(
                prompt.systemMessage(), prompt.userMessage(),
                2200,
                AiOutputMode.JSON_SCHEMA,
                WishUnderstandingJson.jsonSchema()
        );
        return provider.complete(repairRequest).handle((response, throwable) -> {
            if (throwable != null) {
                AiRequestException failure = unwrap(throwable);
                if (attempt < MAX_ATTEMPTS && repairableProviderFailure(failure.category())) {
                    LOGGER.info("AI interpretation repair retry cause={} attempt={}",
                            failure.category(), attempt + 1);
                    return repair(provider, wish, mode, "", attempt + 1, sessionId, runtimeContext,
                            genericRepairIssue(failure.category().name()));
                }
                LOGGER.info("AI interpretation repair failed cause={} attempt={}",
                        failure.category(), attempt);
                return CompletableFuture.completedFuture(failureResult(throwable));
            }
            try {
                if (sessionId != null) {
                    WishLifecycleLog.event(sessionId, "AI_RESPONSE_RECEIVED",
                            "attempt=" + attempt + " repair=true");
                }
                WishUnderstandingJson.Understanding understanding = WishUnderstandingJson.parse(
                        response.assistantContent(), sessionId);
                return CompletableFuture.completedFuture(WishInterpretationResult.success(
                        understanding.interpretation(), understanding.program()));
            } catch (IllegalArgumentException exception) {
                if (attempt < MAX_ATTEMPTS) {
                    LOGGER.info("AI interpretation repair retry cause=SCHEMA_VALIDATION detail={} attempt={}",
                            validationDetail(exception), attempt + 1);
                    return repair(provider, wish, mode, response.assistantContent(), attempt + 1,
                            sessionId, runtimeContext, repairIssue(exception));
                }
                LOGGER.info("AI interpretation repair failed cause=SCHEMA_VALIDATION detail={} attempt={}",
                        validationDetail(exception), attempt);
                return CompletableFuture.completedFuture(WishInterpretationResult.invalidResponse());
            }
        }).thenCompose(Function.identity());
    }

    private static boolean repairableProviderFailure(AiErrorCategory category) {
        return category == AiErrorCategory.MALFORMED_RESPONSE
                || category == AiErrorCategory.EMPTY_RESPONSE;
    }

    private static WishProgramValidationIssue repairIssue(IllegalArgumentException exception) {
        if (exception instanceof WishProgramNormalizationException normalization) {
            return normalization.issue();
        }
        return genericRepairIssue(validationDetail(exception));
    }

    private static WishProgramValidationIssue genericRepairIssue(String detail) {
        String validationError = detail != null && detail.contains(":")
                ? detail : "MALFORMED_RESPONSE";
        return new WishProgramValidationIssue(validationError, "", "", null,
                null, null, false, detail);
    }

    private static WishingWillowPromptAssembler.AssembledPrompt understandingPrompt(
            String wish, WishFulfillmentMode mode, WishingWillowRuntimeContext context,
            WishingWillowPromptAssembler.RequestKind kind, @Nullable String repairPayload) {
        String contract = WishingWillowPrompt.SYSTEM_PROMPT + "\n\nWISH PROGRAM COMPILATION RULES:\n"
                + (kind == WishingWillowPromptAssembler.RequestKind.REPAIR
                ? "You are repairing one previous invalid response. Preserve the same wish semantics and replace invalid fields with schema-valid values.\n" : "")
                + "This is the only normal AI call. Choose executable primitive actions now; do not describe a later plan.\n"
                + "Return exactly {interpretation:{...},program:{schema_version,goal,core_actions,presentation_actions,skill,unknown_capability}}.\n"
                + "All required outcomes belong in core_actions. Optional spectacle belongs in presentation_actions.\n"
                + "Known primitive -> use it directly. Multiple primitives -> compose them. Known reusable skill -> set skill and include its primitive composition.\n"
                + "Only when no action or skill can express a genuinely mod-specific capability: leave both action arrays empty and set unknown_capability.\n"
                + "Creative wording is never an unknown capability. Never output Minecraft commands, Java, scripts, code, NBT or shell text.\n"
                + "For all beneficial effects use apply_effect_group group=beneficial.\n"
                + "RESOURCE KIND MATTERS. Determine what the resource is independently from how it is delivered.\n"
                + "ITEM + physical falling from above -> spawn_item_rain. BLOCK + physical falling from above -> spawn_falling_block.\n"
                + "Never put an item registry id into a block parameter. Never put a block registry id into an item parameter merely because the names are related.\n"
                + "minecraft:diamond != minecraft:diamond_block. Do not silently convert an item wish into a block wish.\n"
                + "Without falling-from-above semantics, an ordinary inventory reward still uses give_item.\n"
                + "Resource parameters use exact namespaced ids (for example minecraft:diamond and minecraft:diamond_block).\n"
                + "The program JSON Schema is strict: every action is a discriminated oneOf with a const action id.\n"
                + "Emit only declared parameters with exactly their declared types; counts are integers (never words), enums must be one of the listed values, and every value must stay within the declared bounds.\n"
                + "The JSON schema supplied with this request is the authoritative parameter contract.";
        return WishingWillowPromptAssembler.assemble(kind, context,
                WishActionRegistry.defaults().summaryPrompt(), WishSkillRegistry.defaults().candidatePrompt(wish),
                "Server validator, action policy, entity caps, world limits and permissions remain authoritative.",
                contract, wish, (repairPayload == null ? "" : repairPayload)
                        + "\nFULFILLMENT_MODE=" + mode.name());
    }

    private static void logPrompt(@Nullable UUID sessionId,
                                  WishingWillowPromptAssembler.AssembledPrompt prompt, String wish) {
        LOGGER.info("Wish AI context prepared session={} corePromptVersion=1 availableActions={} selectedSkills={}",
                sessionId == null ? "UNAVAILABLE" : sessionId, WishActionRegistry.defaults().ids().size(),
                WishSkillRegistry.defaults().retrieve(wish, 3).stream().map(skill -> skill.id()).toList());
        LOGGER.info("AI prompt assembled session={} sections={} characters={} tokensEstimate={}",
                sessionId == null ? "UNAVAILABLE" : sessionId, prompt.sections(), prompt.characters(),
                prompt.tokensEstimate());
    }

    private static WishInterpretationResult failureResult(Throwable throwable) {
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

    private static String validationDetail(IllegalArgumentException exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) return "UNKNOWN";
        value = value.replaceAll("[^A-Za-z0-9_:.-]", "_");
        return value.length() <= 96 ? value : value.substring(0, 96);
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
