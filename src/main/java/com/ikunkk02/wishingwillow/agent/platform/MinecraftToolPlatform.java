package com.ikunkk02.wishingwillow.agent.platform;

import com.ikunkk02.wishingwillow.agent.tool.ToolResult;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.planning.CapabilityCandidate;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;

import java.util.List;

/** Loader-neutral, read-only game-data SPI. It never mutates a Minecraft world. */
public interface MinecraftToolPlatform {
    ToolResult listStatusEffects(StatusEffectCategory category, int limit, String cursor);
    ToolResult listRegistry(RegistryEntryType type, String semantic, String namespace, int limit, String cursor);
    ToolResult queryRegistry(RegistryEntryType type, String query, String namespace, int limit, String cursor);
    ToolResult getPlayerState();
    ToolResult getPlayerEffects();
    ToolResult getPlayerInventorySummary();
    ToolResult inspectModFeature(String modId, String feature);
    List<CapabilityCandidate> findCapabilityCandidates(String semantic, WishInterpretation interpretation);
    boolean contains(RegistryEntryType type, String id);
    List<String> statusEffectIds(StatusEffectCategory category);
}
