package com.ikunkk02.wishingwillow.planning.direct;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;

import java.util.ArrayList;
import java.util.List;

/** Supplies harmless theatrical fallbacks. Destructive absurdity is never synthesized here. */
public final class WishAbsurdityPlanner {
    public List<DirectWishAction> candidates(WishAbsurdityProfile requested, RegistrySnapshot registry) {
        List<DirectWishAction> result = new ArrayList<>(requested.modifiers());
        if (requested.style() == WishAbsurdityStyle.NONE || requested.intensity() <= 0) {
            return List.copyOf(result);
        }
        firstPresent(registry, RegistryEntryType.PARTICLE, List.of(
                "minecraft:totem_of_undying", "minecraft:end_rod", "minecraft:enchanted_hit"
        )).ifPresent(id -> result.add(particles(id, requested.intensity())));
        firstPresent(registry, RegistryEntryType.SOUND, List.of(
                "minecraft:ui.toast.challenge_complete", "minecraft:entity.lightning_bolt.thunder",
                "minecraft:entity.player.levelup"
        )).ifPresent(id -> result.add(sound(id, requested.intensity())));
        return List.copyOf(result);
    }

    private static DirectWishAction particles(String id, int intensity) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("count", Math.min(512, 64 + intensity * 4));
        parameters.addProperty("radius", Math.min(8.0, 1.5 + intensity / 20.0));
        return new DirectWishAction(WishActionType.SPAWN_PARTICLE, DirectWishTarget.SELF, id, parameters);
    }

    private static DirectWishAction sound(String id, int intensity) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("volume", Math.min(4.0, 1.0 + intensity / 50.0));
        parameters.addProperty("pitch", Math.max(0.5, 1.2 - intensity / 250.0));
        parameters.addProperty("distance", Math.min(128, 24 + intensity));
        return new DirectWishAction(WishActionType.PLAY_SOUND, DirectWishTarget.SELF, id, parameters);
    }

    private static java.util.Optional<String> firstPresent(RegistrySnapshot registry, RegistryEntryType type,
                                                            List<String> choices) {
        return choices.stream().filter(id -> registry.contains(type, id)).findFirst();
    }
}
