package com.ikunkk02.wishingwillow.research.web;

import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModIdentityResolverTest {
    private final ModIdentityResolver resolver = new ModIdentityResolver();

    @Test
    void confirmsExactForgeProjectWithRegistryEvidence() {
        ModIdentityResolution resolution = resolver.resolve(mod(""), registry(), List.of(correct()));

        assertEquals(IdentityConfidenceLevel.CONFIRMED, resolution.level());
        assertEquals(correct().url(), resolution.selectedUrl());
        assertTrue(resolution.confidence() >= 0.90);
        assertTrue(resolution.candidates().get(0).factors().stream()
                .anyMatch(factor -> factor.name().equals("Registry Match")));
    }

    @Test
    void canonicalMetadataUrlIsStrongButStillRequiresCompatibility() {
        ModIdentityCandidate candidate = resolver.score(mod(correct().url()), registry(), correct());
        WebSearchResult fabric = new WebSearchResult(correct().title(), correct().url(), correct().snippet(),
                correct().author(), List.of("1.20.1"), List.of("Fabric"), List.of("Mods"), correct().fileNames(), "CURSEFORGE");

        assertEquals(1.0, candidate.confidence());
        assertTrue(resolver.score(mod(correct().url()), registry(), fabric).rejected());
    }

    @Test
    void leavesCloseSameNameProjectsAmbiguous() {
        WebSearchResult other = new WebSearchResult("Cave Dweller Reimagined", "https://www.curseforge.com/minecraft/mc-mods/cave-dweller-reimagined",
                "Cave stalking horror mod", "Valk", List.of("1.20.1"), List.of("Forge"), List.of("Mods"),
                List.of("cavedweller-RELEASE-1.0.0.jar"), "CURSEFORGE");
        ModIdentityResolution resolution = resolver.resolve(mod(""), registry(), List.of(correct(), other));

        assertEquals(IdentityConfidenceLevel.UNRESOLVED, resolution.level());
        assertEquals("UNRESOLVED_AMBIGUOUS", resolution.reason());
        assertTrue(resolution.selectedUrl().isBlank());
    }

    @Test
    void rejectsFabricOnlyAndPenalizesWrongMinecraftVersion() {
        WebSearchResult fabric = new WebSearchResult(correct().title(), correct().url(), correct().snippet(),
                correct().author(), List.of("1.20.1"), List.of("Fabric"), List.of("Mods"), correct().fileNames(), "CURSEFORGE");
        WebSearchResult wrongVersion = new WebSearchResult(correct().title(), correct().url(), correct().snippet(),
                correct().author(), List.of("1.21.1"), List.of("Forge"), List.of("Mods"), correct().fileNames(), "CURSEFORGE");

        assertTrue(resolver.score(mod(""), registry(), fabric).rejected());
        ModIdentityCandidate wrong = resolver.score(mod(""), registry(), wrongVersion);
        assertTrue(wrong.confidence() < 0.75);
        assertTrue(wrong.factors().stream().anyMatch(factor -> factor.name().equals("Minecraft Version") && factor.contribution() < 0));
    }

    private static InstalledModInfo mod(String displayUrl) {
        return new InstalledModInfo("cavedweller", "cavedweller", "Cave Dweller Reimagined", "1.0.0",
                "The legendary terror of the caves, reimagined as a stalking horror creature", List.of("Valk", "Naz"),
                "ARR", displayUrl, "", "", "", "cavedweller-RELEASE-1.0.0.jar", "1.20.1", "forge", List.of());
    }

    private static RegistrySnapshot registry() {
        return new RegistrySnapshot(Map.of(RegistryEntryType.ENTITY, List.of("cavedweller:cave_dweller")),
                Map.of("cavedweller", "cavedweller"), Set.of());
    }

    private static WebSearchResult correct() {
        return new WebSearchResult("Cave Dweller Reimagined", "https://www.curseforge.com/minecraft/mc-mods/reimagination-of-the-cavedweller",
                "The legendary terror stalks caves. cavedweller", "Valk", List.of("1.20.1"), List.of("Forge"),
                List.of("Mods", "Mobs"), List.of("cavedweller-RELEASE-1.0.0.jar"), "CURSEFORGE");
    }
}
