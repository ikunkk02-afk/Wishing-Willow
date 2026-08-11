package com.ikunkk02.wishingwillow.client.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.agent.platform.MinecraftToolPlatform;
import com.ikunkk02.wishingwillow.agent.platform.StatusEffectCategory;
import com.ikunkk02.wishingwillow.agent.tool.ToolResult;
import com.ikunkk02.wishingwillow.agent.tool.ToolStatus;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.planning.CapabilityCandidate;
import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.WishContextSnapshot;
import com.ikunkk02.wishingwillow.research.KnowledgeBaseSnapshot;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Forge bridge that captures all game objects on the client thread, then exposes plain immutable data. */
public final class ForgeMinecraftToolPlatform implements MinecraftToolPlatform {
    private final RegistrySnapshot registry;
    private final KnowledgeBaseSnapshot knowledge;
    private final CapabilityCatalog initialCatalog;
    private final Map<StatusEffectCategory, List<String>> effects;
    private final JsonObject playerState;
    private final JsonObject playerEffects;
    private final JsonObject inventory;

    private ForgeMinecraftToolPlatform(RegistrySnapshot registry, KnowledgeBaseSnapshot knowledge,
                                       CapabilityCatalog initialCatalog,
                                       Map<StatusEffectCategory, List<String>> effects,
                                       JsonObject playerState, JsonObject playerEffects, JsonObject inventory) {
        this.registry = registry;
        this.knowledge = knowledge;
        this.initialCatalog = initialCatalog;
        this.effects = Map.copyOf(effects);
        this.playerState = playerState.deepCopy();
        this.playerEffects = playerEffects.deepCopy();
        this.inventory = inventory.deepCopy();
    }

    public ForgeMinecraftToolPlatform withCatalog(CapabilityCatalog catalog) {
        return new ForgeMinecraftToolPlatform(registry, knowledge, catalog, effects, playerState, playerEffects, inventory);
    }

    /** Must be invoked before asynchronous planning, on the Minecraft client thread. */
    public static ForgeMinecraftToolPlatform capture(LocalPlayer player, WishContextSnapshot context,
                                                     RegistrySnapshot registry, KnowledgeBaseSnapshot knowledge,
                                                     CapabilityCatalog initialCatalog) {
        EnumMap<StatusEffectCategory, List<String>> effects = new EnumMap<>(StatusEffectCategory.class);
        for (StatusEffectCategory category : StatusEffectCategory.values()) effects.put(category, new ArrayList<>());
        ForgeRegistries.MOB_EFFECTS.getEntries().forEach(entry -> {
            StatusEffectCategory category = switch (entry.getValue().getCategory()) {
                case BENEFICIAL -> StatusEffectCategory.BENEFICIAL;
                case HARMFUL -> StatusEffectCategory.HARMFUL;
                default -> StatusEffectCategory.NEUTRAL;
            };
            effects.get(category).add(entry.getKey().location().toString());
        });
        effects.replaceAll((key, values) -> values.stream().sorted().toList());
        effects.put(StatusEffectCategory.ALL, effects.entrySet().stream()
                .filter(entry -> entry.getKey() != StatusEffectCategory.ALL)
                .flatMap(entry -> entry.getValue().stream()).distinct().sorted().toList());

        JsonObject state = new JsonObject();
        state.addProperty("health", context.health()); state.addProperty("max_health", context.maxHealth());
        state.addProperty("hunger", context.hunger()); state.addProperty("game_mode", context.gameMode());
        state.addProperty("dimension", context.dimension()); state.addProperty("environment", context.environmentType());
        state.addProperty("nearby_hostiles", context.nearbyHostileCount());

        JsonArray active = new JsonArray();
        player.getActiveEffects().forEach(instance -> {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect());
            if (id != null) active.add(id.toString());
        });
        JsonObject effectSummary = new JsonObject(); effectSummary.add("effect_ids", active);

        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        player.getInventory().items.forEach(stack -> {
            if (stack.isEmpty()) return;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null) counts.merge(id.toString(), stack.getCount(), Integer::sum);
        });
        JsonObject itemCounts = new JsonObject(); counts.forEach(itemCounts::addProperty);
        JsonObject inventorySummary = new JsonObject(); inventorySummary.add("item_counts", itemCounts);
        return new ForgeMinecraftToolPlatform(registry, knowledge, initialCatalog, effects, state,
                effectSummary, inventorySummary);
    }

    @Override public ToolResult listStatusEffects(StatusEffectCategory category, int limit, String cursor) {
        return page(effects.getOrDefault(category, List.of()), limit, cursor, "STATUS_EFFECTS");
    }

    @Override public ToolResult listRegistry(RegistryEntryType type, String semantic, String namespace,
                                             int limit, String cursor) {
        return filtered(type, semantic, namespace, limit, cursor);
    }

    @Override public ToolResult queryRegistry(RegistryEntryType type, String query, String namespace,
                                              int limit, String cursor) {
        return filtered(type, query, namespace, limit, cursor);
    }

    @Override public ToolResult getPlayerState() { return data("PLAYER_SAFE_STATE", playerState); }
    @Override public ToolResult getPlayerEffects() { return data("PLAYER_EFFECTS", playerEffects); }
    @Override public ToolResult getPlayerInventorySummary() { return data("PLAYER_INVENTORY", inventory); }

    @Override public ToolResult inspectModFeature(String modId, String feature) {
        String needle = feature.toLowerCase(Locale.ROOT);
        for (var entry : knowledge.entries()) {
            if (!entry.installed().modId().equals(modId) || entry.knowledge() == null) continue;
            for (var candidate : entry.knowledge().features()) {
                if (!candidate.name().toLowerCase(Locale.ROOT).contains(needle)) continue;
                JsonObject data = new JsonObject(); data.addProperty("name", candidate.name());
                data.addProperty("type", candidate.type().name()); data.addProperty("description", candidate.description());
                return ToolResult.success("FEATURE_FOUND", "Frozen knowledge feature found.", 1,
                        List.of(candidate.name()), data, "");
            }
        }
        return ToolResult.notFound("FEATURE_NOT_FOUND", "Feature is not in the frozen knowledge snapshot.",
                "Search capability candidates or choose a registry-backed resource.");
    }

    @Override public List<CapabilityCandidate> findCapabilityCandidates(String semantic,
                                                                        WishInterpretation interpretation) {
        String needle = semantic.toLowerCase(Locale.ROOT);
        return initialCatalog.candidates().stream().filter(candidate -> needle.isBlank()
                || candidate.featureName().toLowerCase(Locale.ROOT).contains(needle)
                || candidate.description().toLowerCase(Locale.ROOT).contains(needle)
                || candidate.providedCapability().name().toLowerCase(Locale.ROOT).contains(needle)).limit(50).toList();
    }

    @Override public boolean contains(RegistryEntryType type, String id) { return registry.contains(type, id); }
    @Override public List<String> statusEffectIds(StatusEffectCategory category) { return effects.getOrDefault(category, List.of()); }

    private ToolResult filtered(RegistryEntryType type, String query, String namespace, int limit, String cursor) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
        String ns = namespace == null ? "" : namespace.toLowerCase(Locale.ROOT);
        List<String> values = registry.entries().getOrDefault(type, List.of()).stream()
                .filter(id -> needle.isBlank() || id.toLowerCase(Locale.ROOT).contains(needle))
                .filter(id -> ns.isBlank() || id.startsWith(ns + ":"))
                .toList();
        return page(values, limit, cursor, type.name() + "_REGISTRY");
    }

    private static ToolResult page(List<String> values, int requestedLimit, String cursor, String code) {
        int limit = Math.max(1, Math.min(200, requestedLimit <= 0 ? 50 : requestedLimit));
        int offset;
        try { offset = cursor == null || cursor.isBlank() ? 0 : Integer.parseInt(cursor); }
        catch (NumberFormatException exception) { return ToolResult.invalid("INVALID_CURSOR", "Cursor is invalid.", "Restart without a cursor."); }
        if (offset < 0 || offset > values.size()) return ToolResult.invalid("INVALID_CURSOR", "Cursor is outside the result set.", "Restart without a cursor.");
        List<String> page = values.subList(offset, Math.min(values.size(), offset + limit));
        String next = offset + page.size() < values.size() ? Integer.toString(offset + page.size()) : "";
        JsonObject data = new JsonObject(); data.addProperty("total", values.size());
        ToolStatus status = next.isBlank() ? ToolStatus.SUCCESS : ToolStatus.PARTIAL;
        return new ToolResult(status, code, next.isBlank() ? "Complete result set." : "Page returned; more results remain.",
                page.size(), page, List.of(), next.isBlank() ? "" : "Continue with nextCursor.", data, next);
    }

    private static ToolResult data(String code, JsonObject data) {
        return ToolResult.success(code, "Frozen safe snapshot returned.", 1, List.of(), data, "");
    }
}
