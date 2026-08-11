package com.ikunkk02.wishingwillow.planning.semantic;

import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.contract.WishConstraintKind;
import com.ikunkk02.wishingwillow.planning.WishActionType;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Resolves structured delivery semantics to allowlisted vanilla implementation recipes. */
public final class WishSemanticRecipeRegistry {
    private static final Map<String, WishSemanticRecipe> DELIVERY_RECIPES = Map.ofEntries(
            Map.entry("fall_from_sky", WishSemanticRecipe.FALLING_BLOCK_SHOWER),
            Map.entry("fall_from_above", WishSemanticRecipe.FALLING_BLOCK_SHOWER),
            Map.entry("drop_from_above", WishSemanticRecipe.FALLING_BLOCK_SHOWER),
            Map.entry("rain_from_sky", WishSemanticRecipe.FALLING_BLOCK_SHOWER),
            Map.entry("block_rain", WishSemanticRecipe.FALLING_BLOCK_SHOWER),
            Map.entry("physical_block_fall", WishSemanticRecipe.FALLING_BLOCK_SHOWER),
            Map.entry("gravity_delivery", WishSemanticRecipe.FALLING_BLOCK_SHOWER)
    );

    private WishSemanticRecipeRegistry() { }

    public static Optional<WishSemanticRecipe> resolve(WishInterpretation interpretation) {
        if (interpretation == null || interpretation.schemaVersion() < 2) return Optional.empty();
        Optional<String> structured = interpretation.contract().semantic(WishConstraintKind.DELIVERY_SEMANTIC);
        if (structured.isEmpty()) {
            structured = interpretation.contract().semantic(WishConstraintKind.CUSTOM_SEMANTIC);
        }
        return structured.map(WishSemanticRecipeRegistry::normalize).map(DELIVERY_RECIPES::get);
    }

    public static boolean proves(WishInterpretation interpretation, WishActionType action) {
        return resolve(interpretation).map(recipe -> recipe.action() == action).orElse(false);
    }

    public static String deliverySemantic(WishInterpretation interpretation) {
        return interpretation == null || interpretation.schemaVersion() < 2 ? ""
                : interpretation.contract().semantic(WishConstraintKind.DELIVERY_SEMANTIC)
                .or(() -> interpretation.contract().semantic(WishConstraintKind.CUSTOM_SEMANTIC))
                .map(WishSemanticRecipeRegistry::normalize).orElse("");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).strip()
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
