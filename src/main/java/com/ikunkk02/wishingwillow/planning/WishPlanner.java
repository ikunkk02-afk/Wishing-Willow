package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiService;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.planning.ai.AiWishPlanner;

import java.util.concurrent.CompletableFuture;

public final class WishPlanner {
    private final AiWishPlanner aiPlanner;

    public WishPlanner() { this(new AiWishPlanner(AiService.getInstance())); }
    public WishPlanner(AiWishPlanner aiPlanner) { this.aiPlanner = aiPlanner; }

    public CompletableFuture<WishPlanResult> plan(AiConfig config, String originalWish,
                                                  WishInterpretation interpretation,
                                                  WishContextSnapshot context,
                                                  CapabilityCatalog catalog,
                                                  PlanningEnvironment environment) {
        return aiPlanner.plan(config, originalWish, interpretation, context, catalog, environment);
    }
}
