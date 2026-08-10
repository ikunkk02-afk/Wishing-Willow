package com.ikunkk02.wishingwillow.research.source;

import com.ikunkk02.wishingwillow.research.InstalledModInfo;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ProjectMatcher {
    private ProjectMatcher() {
    }

    static double score(InstalledModInfo mod, String title, String slug, String author,
                        String description, List<String> versions, List<String> loaders,
                        List<String> fileNames) {
        if (!versions.contains("1.20.1") || loaders.stream().noneMatch(value -> value.equalsIgnoreCase("forge"))) {
            return 0.0;
        }
        double name = similarity(mod.displayName(), title);
        double id = Math.max(similarity(mod.modId(), slug), similarity(mod.modId(), title));
        double authorScore = mod.authors().isEmpty() ? 0.5 : mod.authors().stream()
                .mapToDouble(value -> similarity(value, author)).max().orElse(0.0);
        double descriptionScore = tokenOverlap(mod.description(), description);
        double fileScore = fileNames.stream().anyMatch(value -> normalize(value).contains(normalize(mod.modId()))) ? 1.0 : 0.0;
        return 0.30 * name + 0.20 * id + 0.15 + 0.15 + 0.10 * authorScore
                + 0.05 * descriptionScore + 0.05 * fileScore;
    }

    static double similarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.contains(b) || b.contains(a)) {
            return 0.88;
        }
        String compactA = a.replace(" ", "");
        String compactB = b.replace(" ", "");
        if (Math.min(compactA.length(), compactB.length()) >= 4
                && (compactA.contains(compactB) || compactB.contains(compactA))) {
            return 0.88;
        }
        return tokenOverlap(a, b);
    }

    private static double tokenOverlap(String left, String right) {
        Set<String> a = tokens(left);
        Set<String> b = tokens(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> tokens(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(List.of(normalized.split(" +")));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").strip();
    }
}
