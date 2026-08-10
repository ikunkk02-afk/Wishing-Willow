package com.ikunkk02.wishingwillow.research.web.source;

import com.ikunkk02.wishingwillow.research.source.ResearchHttpClient;
import com.ikunkk02.wishingwillow.research.web.HtmlContentExtractor;
import com.ikunkk02.wishingwillow.research.web.WebResearchBudget;
import com.ikunkk02.wishingwillow.research.web.WebSearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in public-page smoke test. It never downloads a mod artifact. */
class CurseForgePublicWebLiveTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "WISHING_WILLOW_LIVE_WEB_TEST", matches = "true")
    void identifiesInstalledCaveDwellerAgainstTheCompetingProject() {
        assertTrue(Files.isRegularFile(Path.of("run/deobf-mods/cavedweller-RELEASE-1.0.0.jar")),
                "The existing locally installed test JAR is required; this test never downloads it.");
        CurseForgePublicWebSource source = new CurseForgePublicWebSource(
                new ResearchHttpClient(), new HtmlContentExtractor());
        WebResearchBudget budget = new WebResearchBudget();

        List<WebSearchResult> results;
        try {
            results = source.search("\"Cave Dweller Reimagined\" Forge 1.20.1", budget);
        } catch (CurseForgePublicWebSource.WebSourceException exception) {
            Assumptions.assumeTrue(!"SOURCE_TEMPORARILY_UNAVAILABLE".equals(exception.getMessage()),
                    "CurseForge returned 403/429/Cloudflare; policy requires no bypass");
            throw exception;
        }
        WebSearchResult expected = results.stream()
                .filter(result -> result.url().endsWith("/reimagination-of-the-cavedweller"))
                .findFirst().orElseThrow(() -> new AssertionError("Expected CurseForge project not present: " + results));
        assertTrue(results.stream().noneMatch(result -> result.url().contains("/download")));

        CurseForgePublicWebSource.ProjectPage page = source.fetchProject(expected.url(), budget);
        assertEquals("https://www.curseforge.com/minecraft/mc-mods/reimagination-of-the-cavedweller",
                page.result().url());
        assertTrue(page.page().content().toLowerCase().contains("cave dweller"));
        assertTrue(page.result().loaders().stream().anyMatch(value -> value.equalsIgnoreCase("forge")));
        assertTrue(page.result().gameVersions().contains("1.20.1"));
    }
}
