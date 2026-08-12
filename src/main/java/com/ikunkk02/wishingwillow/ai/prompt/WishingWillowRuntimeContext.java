package com.ikunkk02.wishingwillow.ai.prompt;

import com.google.gson.Gson;

import java.util.List;

/** Compact per-request world state; registry contents stay outside the core identity. */
public record WishingWillowRuntimeContext(
        String minecraftVersion,
        String loader,
        String dimension,
        String playerUuid,
        String playerPosition,
        String willowPosition,
        String difficulty,
        List<String> installedMods,
        String registrySummary,
        boolean researchAvailable,
        String executionLimits
) {
    private static final Gson GSON = new Gson();
    private static final int MAX_MODS = 32;

    public WishingWillowRuntimeContext {
        minecraftVersion = clean(minecraftVersion, 32);
        loader = clean(loader, 64);
        dimension = clean(dimension, 128);
        playerUuid = clean(playerUuid, 64);
        playerPosition = clean(playerPosition, 64);
        willowPosition = clean(willowPosition, 64);
        difficulty = clean(difficulty, 32);
        installedMods = installedMods == null ? List.of() : installedMods.stream()
                .map(value -> clean(value, 128)).filter(value -> !value.isBlank()).distinct()
                .sorted().limit(MAX_MODS).toList();
        registrySummary = clean(registrySummary, 512);
        executionLimits = clean(executionLimits, 1024);
    }

    public static WishingWillowRuntimeContext minimal() {
        return new WishingWillowRuntimeContext("1.20.1", "Forge 47.4.22", "unknown",
                "unknown", "unknown", "unknown", "unknown", List.of(),
                "Registry details available only through validated runtime resolution", true,
                "WishProgramValidator, WishActionPolicy, server policy, permissions, entity caps and world limits are authoritative");
    }

    public String compactJson() {
        return GSON.toJson(this);
    }

    private static String clean(String value, int max) {
        String safe = value == null ? "unknown" : value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").strip();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
