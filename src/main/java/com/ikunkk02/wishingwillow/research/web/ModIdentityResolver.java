package com.ikunkk02.wishingwillow.research.web;

import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ModIdentityResolver {
    public static final double MIN_MARGIN = 0.12;

    public ModIdentityResolution resolve(InstalledModInfo mod, RegistrySnapshot registry,
                                         List<WebSearchResult> results) {
        List<ModIdentityCandidate> candidates = results.stream().distinct().map(result -> score(mod, registry, result))
                .sorted(Comparator.comparingDouble(ModIdentityCandidate::confidence).reversed()).toList();
        List<ModIdentityCandidate> accepted = candidates.stream().filter(candidate -> !candidate.rejected()).toList();
        if (accepted.isEmpty()) {
            return new ModIdentityResolution(IdentityConfidenceLevel.UNRESOLVED, 0.0, "", "",
                    candidates, "NO_COMPATIBLE_CANDIDATE", System.currentTimeMillis());
        }
        ModIdentityCandidate best = accepted.get(0);
        if (accepted.size() > 1 && best.confidence() - accepted.get(1).confidence() < MIN_MARGIN) {
            return new ModIdentityResolution(IdentityConfidenceLevel.UNRESOLVED, best.confidence(), "", "",
                    candidates, "UNRESOLVED_AMBIGUOUS", System.currentTimeMillis());
        }
        return new ModIdentityResolution(best.level(), best.confidence(), best.result().url(), best.result().title(),
                candidates, best.level() == IdentityConfidenceLevel.UNRESOLVED ? "LOW_CONFIDENCE" : "MATCHED",
                System.currentTimeMillis());
    }

    public ModIdentityCandidate score(InstalledModInfo mod, RegistrySnapshot registry, WebSearchResult result) {
        List<IdentityMatchFactor> factors = new ArrayList<>();
        List<String> lowerLoaders = result.loaders().stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
        boolean incompatibleLoader = !lowerLoaders.contains("forge")
                && lowerLoaders.stream().anyMatch(value -> value.equals("fabric") || value.equals("neoforge")
                || value.equals("quilt"));
        boolean wrongType = result.categories().stream().map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains("resource pack") || value.contains("modpack") || value.equals("addons"));
        if (incompatibleLoader || wrongType) {
            String reason = incompatibleLoader ? "INCOMPATIBLE_LOADER" : "NOT_A_FORGE_MOD";
            factors.add(new IdentityMatchFactor("Hard Conflict", -1.0, reason));
            return new ModIdentityCandidate(result, 0.0, IdentityConfidenceLevel.UNRESOLVED,
                    factors, true, reason);
        }

        double score = 0.0;
        double metadataUrl = metadataUrlMatch(mod, result.url()) ? 0.50 : 0.0;
        score += add(factors, "Metadata URL", metadataUrl, metadataUrl > 0 ? "canonical URL match" : "no match");
        double name = 0.25 * similarity(mod.displayName(), result.title());
        score += add(factors, "Name Match", name, result.title());
        double authorSimilarity = mod.authors().isEmpty() ? 0.0 : mod.authors().stream()
                .mapToDouble(author -> similarity(author, result.author())).max().orElse(0.0);
        double author = 0.15 * authorSimilarity;
        score += add(factors, "Author Match", author, result.author());
        double file = 0.20 * fileSimilarity(mod.fileName(), result.fileNames());
        score += add(factors, "Filename Match", file, String.join(", ", result.fileNames()));
        double id = 0.15 * Math.max(similarity(mod.modId(), slug(result.url())),
                Math.max(similarity(mod.modId(), result.title()), similarity(mod.namespace(), result.title())));
        score += add(factors, "Mod ID / Namespace", id, mod.modId() + "/" + mod.namespace());

        boolean versionsKnown = !result.gameVersions().isEmpty();
        boolean versionMatch = result.gameVersions().stream().anyMatch(value -> compatibleVersion(mod.minecraftVersion(), value));
        if (versionMatch) score += add(factors, "Minecraft Version", 0.10, mod.minecraftVersion());
        else if (versionsKnown) score += add(factors, "Minecraft Version", -0.25,
                    "candidate=" + String.join(",", result.gameVersions()));

        boolean loaderMatch = lowerLoaders.stream().anyMatch(value -> value.equals("forge"));
        if (loaderMatch) score += add(factors, "Forge Loader", 0.10, "Forge");

        double description = 0.05 * tokenOverlap(mod.description(), result.snippet());
        score += add(factors, "Description", description, result.snippet());
        String evidence = (result.title() + " " + result.snippet() + " " + String.join(" ", result.fileNames()))
                .toLowerCase(Locale.ROOT);
        boolean registryEvidence = registry.countsForMod(mod.modId()).values().stream().mapToInt(Integer::intValue).sum() > 0
                && (evidence.contains(mod.modId().toLowerCase(Locale.ROOT))
                || evidence.contains(mod.namespace().toLowerCase(Locale.ROOT)));
        if (registryEvidence) score += add(factors, "Registry Match", 0.10, "namespace corroborated");

        if (!mod.authors().isEmpty() && !result.author().isBlank() && authorSimilarity < 0.20) {
            score += add(factors, "Author Conflict", -0.10, result.author());
        }
        score = Math.max(0.0, Math.min(1.0, score));
        return new ModIdentityCandidate(result, score, IdentityConfidenceLevel.from(score), factors, false, "");
    }

    private static double add(List<IdentityMatchFactor> factors, String name, double value, String detail) {
        if (Math.abs(value) >= 0.0001) factors.add(new IdentityMatchFactor(name, value, detail));
        return value;
    }

    private static boolean metadataUrlMatch(InstalledModInfo mod, String candidate) {
        String canonical = canonical(candidate);
        if (canonical.isBlank()) return false;
        return List.of(mod.displayUrl(), mod.modUrl(), mod.issueTrackerUrl(), mod.updateUrl()).stream()
                .map(ModIdentityResolver::canonical).anyMatch(value -> !value.isBlank()
                        && (value.equals(canonical) || value.startsWith(canonical + "/") || canonical.startsWith(value + "/")));
    }

    private static String canonical(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.strip());
            if (uri.getHost() == null) return "";
            String host = uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            return host + path.toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) { return ""; }
    }

    private static String slug(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path == null || path.isBlank()) return "";
            String[] parts = path.split("/");
            for (int index = parts.length - 1; index >= 0; index--) {
                if (!parts[index].isBlank() && !parts[index].equals("files") && !parts[index].matches("\\d+")) return parts[index];
            }
        } catch (RuntimeException ignored) { }
        return "";
    }

    static double similarity(String left, String right) {
        String a = ModSearchQueryBuilder.normalize(left), b = ModSearchQueryBuilder.normalize(right);
        if (a.isBlank() || b.isBlank()) return 0.0;
        if (a.equals(b)) return 1.0;
        if (a.contains(b) || b.contains(a) || a.replace(" ", "").contains(b.replace(" ", ""))
                || b.replace(" ", "").contains(a.replace(" ", ""))) return 0.88;
        return tokenOverlap(a, b);
    }

    private static double fileSimilarity(String local, List<String> remote) {
        String a = ModSearchQueryBuilder.normalize(local.replaceAll("(?i)\\.jar$", ""));
        return remote.stream().map(value -> ModSearchQueryBuilder.normalize(value.replaceAll("(?i)\\.jar$", "")))
                .mapToDouble(value -> a.equals(value) ? 1.0 : similarity(a, value)).max().orElse(0.0);
    }

    private static double tokenOverlap(String left, String right) {
        Set<String> a = tokens(left), b = tokens(right);
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a); intersection.retainAll(b);
        Set<String> union = new HashSet<>(a); union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> tokens(String value) {
        String normalized = ModSearchQueryBuilder.normalize(value);
        return normalized.isBlank() ? Set.of() : new HashSet<>(List.of(normalized.split(" +")));
    }

    private static boolean compatibleVersion(String local, String remote) {
        String a = local == null ? "" : local.strip();
        String b = remote == null ? "" : remote.strip();
        return a.equalsIgnoreCase(b) || (a.equals("1.20.1") && b.equals("1.20"));
    }
}
