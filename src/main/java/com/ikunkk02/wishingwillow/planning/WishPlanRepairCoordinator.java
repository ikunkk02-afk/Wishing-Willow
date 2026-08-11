package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.planning.ai.AiWishPlanner;

import java.util.concurrent.CompletableFuture;

public final class WishPlanRepairCoordinator {
    public static final int MAX_ATTEMPTS = 3;
    private final AiWishPlanner aiPlanner;
    private final FallbackWishPlanner fallbackPlanner;

    public WishPlanRepairCoordinator(AiWishPlanner aiPlanner, FallbackWishPlanner fallbackPlanner) {
        this.aiPlanner = aiPlanner;
        this.fallbackPlanner = fallbackPlanner;
    }

    public CompletableFuture<WishPlanResult> plan(AiConfig config, String originalWish,
                                                  WishInterpretation interpretation,
                                                  WishContextSnapshot context, CapabilityCatalog catalog,
                                                  PlanningEnvironment environment,
                                                  ExecutionSettingsSnapshot settings) {
        return aiPlanner.plan(config, originalWish, interpretation, context, catalog, environment, settings)
                .thenApply(result -> {
                    if (result.draft() != null || result.attemptsUsed() >= MAX_ATTEMPTS) return result;
                    int fallbackAttempt = result.attemptsUsed() + 1;
                    WishingWillow.LOGGER.info("Wish fulfillment replan reason={} attempt={}",
                            result.error(), fallbackAttempt);
                    WishPlanResult fallback = fallbackPlanner.plan(originalWish, interpretation, context,
                            catalog, environment, settings);
                    if (fallback.draft() == null) return WishPlanResult.failed(fallback.error(), fallbackAttempt);
                    CandidateSourceKind source = fallback.draft().steps().get(0)
                            .candidateReference().sourceKind();
                    String label = source == CandidateSourceKind.VANILLA_REGISTRY
                            ? "VANILLA" : "WISHING_WILLOW_BUILTIN";
                    WishingWillow.LOGGER.info("Wish fulfillment fallback source={}", label);
                    return fallback.state() == WishPlanState.PARTIAL
                            ? WishPlanResult.partial(fallback.draft(), fallbackAttempt)
                            : WishPlanResult.success(fallback.draft(), fallbackAttempt);
                });
    }
}
