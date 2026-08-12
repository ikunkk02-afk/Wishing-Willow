package com.ikunkk02.wishingwillow.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict whitelist normalization for capability names produced by external models. */
public final class WishCapabilityNormalizer {
    private static final Map<String, WishCapability> ALIASES = createAliases();

    private WishCapabilityNormalizer() { }

    public static WishCapability normalize(String value) {
        if (value == null) throw new IllegalArgumentException("UNKNOWN_WISH_CAPABILITY");
        String normalized = value.strip().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("UNKNOWN_WISH_CAPABILITY");
        WishCapability alias = ALIASES.get(normalized);
        if (alias != null) return alias;
        try { return WishCapability.valueOf(normalized); }
        catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("UNKNOWN_WISH_CAPABILITY_" + normalized);
        }
    }

    public static Map<String, WishCapability> aliases() { return ALIASES; }

    public static Set<String> allowedValues() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (WishCapability capability : WishCapability.values()) values.add(capability.name());
        values.addAll(ALIASES.keySet());
        return Collections.unmodifiableSet(values);
    }

    private static Map<String, WishCapability> createAliases() {
        Map<String, WishCapability> values = new LinkedHashMap<>();
        for (String alias : Set.of("REMOVE_ENTITY", "DESPAWN_ENTITY", "CLEAR_ENTITIES",
                "ENTITY_REMOVE", "REMOVE_MOBS", "ENTITY_SUPPRESSION")) {
            values.put(alias, WishCapability.ENTITY_REMOVAL);
        }
        return Collections.unmodifiableMap(values);
    }
}
