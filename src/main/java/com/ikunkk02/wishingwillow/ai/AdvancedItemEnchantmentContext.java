package com.ikunkk02.wishingwillow.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Bounded live-registry context for advanced ItemStack planning. */
public final class AdvancedItemEnchantmentContext {
    private static final Gson GSON = new Gson();
    private static final int MAX_ITEMS = 8;
    private static final int MAX_ENCHANTMENTS = 96;

    private AdvancedItemEnchantmentContext() { }

    public static String prompt(String wish) {
        try {
            return livePrompt(wish);
        } catch (Throwable unavailable) {
            JsonObject fallback = new JsonObject();
            fallback.add("target_items", new JsonArray());
            fallback.add("available_enchantments", new JsonArray());
            fallback.addProperty("registry_status", "unavailable_until_minecraft_bootstrap");
            fallback.addProperty("selection_rule", "The live client and authoritative server registries validate all enchantment ids, applicability, compatibility, curses, and max levels at runtime.");
            return GSON.toJson(fallback);
        }
    }

    private static String livePrompt(String wish) {
        String normalized = normalize(wish);
        List<Item> targets = ForgeRegistries.ITEMS.getValues().stream()
                .filter(item -> matches(normalized, item))
                .sorted(Comparator.comparing(item -> id(item).toString())).limit(MAX_ITEMS).toList();
        JsonObject root = new JsonObject();
        JsonArray targetIds = new JsonArray(); targets.forEach(item -> targetIds.add(id(item).toString()));
        root.add("target_items", targetIds);
        JsonArray available = new JsonArray();
        ForgeRegistries.ENCHANTMENTS.getValues().stream()
                .sorted(Comparator.comparing(enchantment -> id(enchantment).toString()))
                .filter(enchantment -> targets.isEmpty() || targets.stream()
                        .anyMatch(item -> enchantment.canEnchant(new ItemStack(item))))
                .limit(MAX_ENCHANTMENTS).forEach(enchantment -> available.add(describe(enchantment, targets)));
        root.add("available_enchantments", available);
        root.addProperty("selection_rule", "MAXED selects an excellent normally compatible non-curse combination at registered maxLevel; explicit requested levels are preserved up to unsafeEnchantmentMaxLevel=10; incompatible/curse enchantments require explicit player intent.");
        return GSON.toJson(root);
    }

    private static JsonObject describe(Enchantment enchantment, List<Item> targets) {
        JsonObject value = new JsonObject();
        value.addProperty("id", id(enchantment).toString());
        value.addProperty("maxLevel", enchantment.getMaxLevel());
        value.addProperty("curse", enchantment.isCurse());
        JsonArray applies = new JsonArray();
        targets.stream().filter(item -> enchantment.canEnchant(new ItemStack(item)))
                .map(AdvancedItemEnchantmentContext::id).map(ResourceLocation::toString).forEach(applies::add);
        value.add("applicable_items", applies);
        JsonArray incompatible = new JsonArray();
        ForgeRegistries.ENCHANTMENTS.getValues().stream()
                .filter(other -> other != enchantment && !enchantment.isCompatibleWith(other))
                .map(AdvancedItemEnchantmentContext::id).sorted().limit(16)
                .map(ResourceLocation::toString).forEach(incompatible::add);
        value.add("incompatible_with", incompatible);
        return value;
    }

    private static boolean matches(String wish, Item item) {
        ResourceLocation id = id(item);
        String path = normalize(id.getPath());
        String translated;
        try { translated = normalize(item.getDescription().getString()); }
        catch (RuntimeException ignored) { translated = ""; }
        return !path.isBlank() && wish.contains(path) || !translated.isBlank() && wish.contains(translated);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "_");
    }

    private static ResourceLocation id(Item item) { return ForgeRegistries.ITEMS.getKey(item); }
    private static ResourceLocation id(Enchantment enchantment) { return ForgeRegistries.ENCHANTMENTS.getKey(enchantment); }
}