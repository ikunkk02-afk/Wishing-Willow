package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiService;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.planning.ai.AiWishPlanner;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;

import java.util.concurrent.CompletableFuture;

public final class WishPlanner {
    private final AiWishPlanner aiPlanner;
    private final FallbackWishPlanner fallbackPlanner;
    private final WishPlanRepairCoordinator repairs;

    public WishPlanner() { this(new AiWishPlanner(AiService.getInstance()), new FallbackWishPlanner()); }
    public WishPlanner(AiWishPlanner aiPlanner) { this(aiPlanner, new FallbackWishPlanner()); }
    public WishPlanner(AiWishPlanner aiPlanner, FallbackWishPlanner fallbackPlanner) {
        this.aiPlanner = aiPlanner;
        this.fallbackPlanner = fallbackPlanner;
        this.repairs = new WishPlanRepairCoordinator(aiPlanner, fallbackPlanner);
    }

    public CompletableFuture<WishPlanResult> plan(AiConfig config, String originalWish,
                                                  WishInterpretation interpretation,
                                                  WishContextSnapshot context,
                                                  CapabilityCatalog catalog,
                                                  PlanningEnvironment environment) {
        return plan(config,originalWish,interpretation,context,catalog,environment,
                ExecutionSettingsSnapshot.permissive());
    }

    public CompletableFuture<WishPlanResult> plan(AiConfig config, String originalWish,
                                                  WishInterpretation interpretation,
                                                  WishContextSnapshot context,
                                                  CapabilityCatalog catalog,
                                                  PlanningEnvironment environment,
                                                  ExecutionSettingsSnapshot settings) {
        return repairs.plan(config, originalWish, interpretation, context, catalog, environment, settings);
    }
}
