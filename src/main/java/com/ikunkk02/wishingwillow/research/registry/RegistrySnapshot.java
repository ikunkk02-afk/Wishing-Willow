package com.ikunkk02.wishingwillow.research.registry;

import com.ikunkk02.wishingwillow.research.RegistryEntryType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RegistrySnapshot {
    private final int schemaVersion;
    private final Map<RegistryEntryType, List<String>> entries;
    private final Map<String, String> namespaceOwners;
    private final Set<String> ambiguousNamespaces;
    private final String digest;

    public RegistrySnapshot(Map<RegistryEntryType, List<String>> entries,
                            Map<String, String> namespaceOwners,
                            Set<String> ambiguousNamespaces) {
        this.schemaVersion = 1;
        EnumMap<RegistryEntryType, List<String>> copied = new EnumMap<>(RegistryEntryType.class);
        for (RegistryEntryType type : RegistryEntryType.values()) {
            List<String> values = new ArrayList<>(entries.getOrDefault(type, List.of()));
            values.sort(String::compareTo);
            copied.put(type, List.copyOf(values));
        }
        this.entries = Collections.unmodifiableMap(copied);
        this.namespaceOwners = Map.copyOf(namespaceOwners);
        this.ambiguousNamespaces = Set.copyOf(ambiguousNamespaces);
        this.digest = computeDigest(copied);
    }

    public static RegistrySnapshot empty() {
        return new RegistrySnapshot(Map.of(), Map.of(), Set.of());
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public Map<RegistryEntryType, List<String>> entries() {
        return entries;
    }

    public Map<String, String> namespaceOwners() {
        return namespaceOwners;
    }

    public Set<String> ambiguousNamespaces() {
        return ambiguousNamespaces;
    }

    public String digest() {
        return digest;
    }

    public boolean contains(RegistryEntryType type, String id) {
        return entries.getOrDefault(type, List.of()).contains(id);
    }

    public Map<RegistryEntryType, List<String>> entriesForMod(String modId) {
        EnumMap<RegistryEntryType, List<String>> result = new EnumMap<>(RegistryEntryType.class);
        for (RegistryEntryType type : RegistryEntryType.values()) {
            result.put(type, entries.getOrDefault(type, List.of()).stream()
                    .filter(id -> owner(id).equals(modId))
                    .toList());
        }
        return result;
    }

    public Map<RegistryEntryType, Integer> countsForMod(String modId) {
        Map<RegistryEntryType, List<String>> values = entriesForMod(modId);
        EnumMap<RegistryEntryType, Integer> result = new EnumMap<>(RegistryEntryType.class);
        values.forEach((type, ids) -> result.put(type, ids.size()));
        return result;
    }

    public Map<RegistryEntryType, List<String>> representativeEntries(String modId) {
        Map<RegistryEntryType, List<String>> owned = entriesForMod(modId);
        EnumMap<RegistryEntryType, List<String>> result = new EnumMap<>(RegistryEntryType.class);
        for (RegistryEntryType type : RegistryEntryType.values()) {
            result.put(type, owned.getOrDefault(type, List.of()).stream()
                    .sorted((left, right) -> {
                        int score = Integer.compare(valueScore(right), valueScore(left));
                        return score != 0 ? score : left.compareTo(right);
                    })
                    .limit(type.promptLimit())
                    .toList());
        }
        return result;
    }

    private String owner(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 1 ? "" : id.substring(0, colon);
        if (ambiguousNamespaces.contains(namespace)) {
            return "";
        }
        return namespaceOwners.getOrDefault(namespace, namespace);
    }

    private static int valueScore(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : List.of("boss", "monster", "stalker", "watcher", "ghost", "fear", "dark",
                "panic", "insanity", "dimension", "portal", "structure", "ambient", "entity")) {
            if (lower.contains(token)) {
                score += 2;
            }
        }
        return score;
    }

    private static String computeDigest(Map<RegistryEntryType, List<String>> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (RegistryEntryType type : RegistryEntryType.values()) {
                digest.update(type.name().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                for (String id : values.getOrDefault(type, List.of())) {
                    digest.update(id.getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
