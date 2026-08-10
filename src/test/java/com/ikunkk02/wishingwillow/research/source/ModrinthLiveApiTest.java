package com.ikunkk02.wishingwillow.research.source;

import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import com.ikunkk02.wishingwillow.research.ModFingerprint;
import com.ikunkk02.wishingwillow.research.ResearchSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in smoke test for the real platform; normal builds remain deterministic and offline-safe. */
class ModrinthLiveApiTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "WISHING_WILLOW_LIVE_API_TEST", matches = "true")
    void identifiesCreateThroughTheRealSearchApi() {
        InstalledModInfo create = new InstalledModInfo("create", "create", "Create", "6.0.8",
                "Aesthetic technology that empowers the player.", List.of("simibubi"), "MIT",
                "https://www.curseforge.com/minecraft/mc-mods/create", "",
                "create-1.20.1-6.0.8_mapped_official_1.20.1.jar", List.of("forge"));
        SourceResearchResult result = new ModrinthResearchSource(new ResearchHttpClient())
                .research(create, new ModFingerprint("create", "6.0.8", create.fileName(), "")).join();

        assertTrue(result.identified());
        assertTrue(result.sources().contains(ResearchSource.MODRINTH_SEARCH));
        assertTrue(result.sources().contains(ResearchSource.MODRINTH_PROJECT));
        assertTrue(result.documents().stream().anyMatch(document -> !document.content().isBlank()));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "WISHING_WILLOW_LIVE_API_TEST", matches = "true")
    void identifiesReimaginedCaveDwellerWithoutGuessingAnotherProject() {
        InstalledModInfo caveDweller = new InstalledModInfo("cavedweller", "cavedweller",
                "Cave Dweller Reimagined", "1.0.0",
                "The legendary terror of the caves, completely reimagined with dynamic hunting mechanics.",
                List.of("Valk", "Naz"), "All Rights Reserved", "", "",
                "cavedweller-RELEASE-1.0.0_mapped_official_1.20.1.jar", List.of("forge", "geckolib"));
        SourceResearchResult result = new ModrinthResearchSource(new ResearchHttpClient())
                .research(caveDweller, new ModFingerprint("cavedweller", "1.0.0",
                        caveDweller.fileName(), "")).join();

        assertTrue(result.identified());
        assertTrue(result.sources().contains(ResearchSource.MODRINTH_SEARCH));
        assertTrue(result.documents().stream().anyMatch(document ->
                document.publicUrl().contains("reimagined-cave-dweller")));
    }
}
