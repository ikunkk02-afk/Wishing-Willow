package com.ikunkk02.wishingwillow.research.web;

import com.ikunkk02.wishingwillow.research.InstalledModInfo;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ModSearchQueryBuilder {
    private static final Set<String> LOW_VALUE = Set.of(
            "forge", "fabric", "neoforge", "minecraft", "mc", "release", "released", "beta", "alpha",
            "snapshot", "final", "latest", "stable", "mod", "mods", "jar", "mapped", "official"
    );

    public List<String> build(InstalledModInfo mod) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String name = quote(mod.displayName());
        queries.add(name + " Minecraft");
        queries.add(name + " " + title(mod.loader()) + " " + mod.minecraftVersion());
        if (!mod.authors().isEmpty()) {
            queries.add(name + " " + mod.authors().get(0) + " Minecraft mod");
        } else if (!normalize(mod.modId()).equals(normalize(mod.displayName()))) {
            queries.add(quote(mod.modId()) + " Minecraft mod");
        } else {
            distinctiveFileTokens(mod.fileName()).stream().findFirst()
                    .ifPresent(token -> queries.add(name + " " + token));
        }
        return queries.stream().filter(value -> !value.isBlank()).limit(3).toList();
    }

    public List<String> distinctiveFileTokens(String fileName) {
        String normalized = normalize(fileName.replaceAll("(?i)\\.jar$", ""));
        Set<String> versionParts = new LinkedHashSet<>(Arrays.asList(normalizeVersion(fileName).split(" +")));
        List<String> result = new ArrayList<>();
        for (String token : normalized.split(" +")) {
            if (token.length() < 3 || LOW_VALUE.contains(token) || versionParts.contains(token)
                    || token.matches("v?\\d+(?:\\d+)*")) continue;
            if (!result.contains(token)) result.add(token);
        }
        result.sort((left, right) -> Integer.compare(right.length(), left.length()));
        return List.copyOf(result);
    }

    private static String normalizeVersion(String value) {
        return value == null ? "" : value.replaceAll("[^0-9.]+", " ").replace('.', ' ').strip();
    }
    private static String quote(String value) { return "\"" + value.replace("\"", "") + "\""; }
    private static String title(String value) {
        return value == null || value.isBlank() ? "Forge" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").strip();
    }
}
