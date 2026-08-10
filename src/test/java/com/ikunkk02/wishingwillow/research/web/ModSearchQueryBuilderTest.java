package com.ikunkk02.wishingwillow.research.web;

import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModSearchQueryBuilderTest {
    @Test
    void buildsThreeBoundedHighQualityQueries() {
        InstalledModInfo mod = mod(List.of("Gargin"));
        List<String> queries = new ModSearchQueryBuilder().build(mod);

        assertEquals(3, queries.size());
        assertEquals("\"Cave Dweller Reimagined\" Minecraft", queries.get(0));
        assertEquals("\"Cave Dweller Reimagined\" Forge 1.20.1", queries.get(1));
        assertEquals("\"Cave Dweller Reimagined\" Gargin Minecraft mod", queries.get(2));
    }

    @Test
    void keepsIdentityTokensAndDropsBuildNoise() {
        List<String> tokens = new ModSearchQueryBuilder().distinctiveFileTokens(
                "reimagined-cavedweller-FORGE-mc1.20.1-RELEASE-beta-1.0.0_mapped_official_1.20.1.jar");

        assertTrue(tokens.contains("reimagined"));
        assertTrue(tokens.contains("cavedweller"));
        assertFalse(tokens.contains("forge"));
        assertFalse(tokens.contains("release"));
        assertFalse(tokens.contains("mapped"));
        assertTrue(tokens.stream().noneMatch(value -> value.matches("\\d+")));
    }

    private static InstalledModInfo mod(List<String> authors) {
        return new InstalledModInfo("cavedweller", "cavedweller", "Cave Dweller Reimagined", "1.0.0",
                "A cave stalking horror creature", authors, "ARR", "", "", "", "",
                "reimagined-cavedweller-RELEASE-1.0.0.jar", "1.20.1", "forge", List.of());
    }
}
