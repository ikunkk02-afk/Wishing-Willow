package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.contract.WishContractType;

import java.util.Locale;
import java.util.Set;

/** Chooses the cheap path unless the interpretation positively requires research or custom semantics. */
public final class WishActionRouter {
    private static final Set<WishCapability> COMPLEX_CAPABILITIES = Set.of(
            WishCapability.STALKING_ENTITY,
            WishCapability.PERSISTENT_FOLLOWER,
            WishCapability.MIMIC_ENTITY,
            WishCapability.MOB_BEHAVIOR,
            WishCapability.MEMORY_RELATED_EVENT,
            WishCapability.ENTITY_RECREATION,
            WishCapability.IMITATION,
            WishCapability.POWERFUL_ENEMY,
            WishCapability.SPACECRAFT,
            WishCapability.SPACE_TRAVEL
    );
    private static final Set<WishContractType> SEMANTIC_CONTRACTS = Set.of(
            WishContractType.KNOWLEDGE,
            WishContractType.RESURRECTION,
            WishContractType.OTHER
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
        if (interpretation.contract().requiresAiReview()
                || SEMANTIC_CONTRACTS.contains(interpretation.contract().type())) {
            return complex("contract_requires_semantic_research");
        }
        if (interpretation.delivery() == WishDelivery.CONDITIONAL
                || interpretation.delivery() == WishDelivery.PROGRESSIVE) {
            return complex("delivery_requires_multi_step_agent_timing");
        }
        for (WishCapability capability : interpretation.requiredCapabilities()) {
            if (COMPLEX_CAPABILITIES.contains(capability)) {
                return complex("complex_capability=" + capability.name());
            }
        }
        String normalized = (originalWish == null ? "" : originalWish).toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        if (EXPLICIT_MOD_RESEARCH.stream().anyMatch(normalized::contains)) {
            return complex("explicit_mod_research_or_special_behavior");
        }
        return new WishRouteDecision(WishExecutionRoute.DIRECT_ACTION,
                "contract_expressible_by_allowlisted_actions");
    }

    private static WishRouteDecision complex(String reason) {
        return new WishRouteDecision(WishExecutionRoute.COMPLEX_AGENT, reason);
    }
}
