package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.planning.semantic.WishSemanticRecipeRegistry;

import java.util.Locale;
import java.util.Set;
import com.ikunkk02.wishingwillow.program.WishProgram;

/** Gives the controlled Action DSL first refusal; the Agent is reserved for genuinely unknown capabilities. */
public final class WishActionRouter {
    private static final Set<WishCapability> UNKNOWN_EXTERNAL_CAPABILITIES = Set.of(
            WishCapability.MEMORY_RELATED_EVENT,
            WishCapability.SPACECRAFT,
            WishCapability.SPACE_TRAVEL
    );
    private static final Set<String> EXPLICIT_MOD_RESEARCH = Set.of(
            "mod-specific", "mod specific", "special api", "special behavior", "unknown mod",
            "third-party mod", "cave dweller", "mod event",
            "\u6a21\u7ec4\u8054\u52a8", "\u6a21\u7ec4\u7279\u6b8a", "\u7279\u6b8a\u884c\u4e3a",
            "\u7279\u6b8a\u4e8b\u4ef6", "\u672a\u77e5\u6a21\u7ec4", "\u6d1e\u7a74\u5c45\u4f4f\u8005",
            "\u7b2c\u4e09\u65b9\u6a21\u7ec4", "\u6a21\u7ec4api", "\u6a21\u7ec4 api"
    );

    public WishRouteDecision select(String originalWish, WishInterpretation interpretation) {
        if (interpretation == null || interpretation.schemaVersion() < 2) {
            return complex("legacy_or_missing_contract");
        }
        String normalized = (originalWish == null ? "" : originalWish).toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        if (EXPLICIT_MOD_RESEARCH.stream().anyMatch(normalized::contains)) {
            return complex("explicit_mod_research_or_special_behavior");
        }
        for (WishCapability capability : interpretation.requiredCapabilities()) {
            if (UNKNOWN_EXTERNAL_CAPABILITIES.contains(capability)) {
                return complex("unknown_external_capability=" + capability.name());
            }
        }
        if (WishSemanticRecipeRegistry.resolve(interpretation).isPresent()) {
            return new WishRouteDecision(WishExecutionRoute.DIRECT_ACTION,
                    "semantic_expressible_by_vanilla_primitives");
        }
        return new WishRouteDecision(WishExecutionRoute.DIRECT_ACTION,
                "direct_action_first_refusal");
    }

    public WishRouteDecision select(WishProgram program) {
        if (program == null) return complex("missing_wish_program");
        if (program.requiresAgent()) return complex("unknown_capability=" + program.unknownCapability());
        if (program.usesSkill()) return new WishRouteDecision(WishExecutionRoute.DIRECT_ACTION,
                "known_skill=" + program.skill());
        return new WishRouteDecision(WishExecutionRoute.DIRECT_ACTION, "known_action_program");
    }

    private static WishRouteDecision complex(String reason) {
        return new WishRouteDecision(WishExecutionRoute.COMPLEX_AGENT, reason);
    }
}
