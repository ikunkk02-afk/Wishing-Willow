package com.ikunkk02.wishingwillow.research.web.source;

import com.ikunkk02.wishingwillow.research.source.ResearchHttpClient;
import com.ikunkk02.wishingwillow.research.web.HtmlContentExtractor;
import com.ikunkk02.wishingwillow.research.web.WebSearchResult;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurseForgePublicWebSourceTest {
    private final CurseForgePublicWebSource source = new CurseForgePublicWebSource(
            new ResearchHttpClient(), new HtmlContentExtractor());

    @Test
    void parsesSameNameSearchCardsWithoutTreatingDownloadAsAProject() {
        String html = "<main><article><a href='/minecraft/mc-mods/reimagination-of-the-cavedweller'>"
                + "Cave Dweller Reimagined</a><p>By Valk Forge 1.20.1 cavedweller-RELEASE-1.0.0.jar</p>"
                + "<a href='/minecraft/mc-mods/reimagination-of-the-cavedweller/download/1'>Download</a></article>"
                + "<article><a href='/minecraft/mc-mods/cave-dweller-reimagined'>Cave Dweller Reimagined</a>"
                + "<p>By AnotherAuthor Fabric 1.21.1</p></article></main>";
        List<WebSearchResult> results = source.parseSearch(html,
                URI.create("https://www.curseforge.com/minecraft/search?class=mc-mods"));

        assertEquals(2, results.size());
        assertEquals("Valk", results.get(0).author());
        assertTrue(results.get(0).loaders().stream().anyMatch(value -> value.equalsIgnoreCase("forge")));
        assertTrue(results.get(0).gameVersions().contains("1.20.1"));
        assertTrue(results.get(0).fileNames().contains("cavedweller-RELEASE-1.0.0.jar"));
        assertTrue(results.stream().noneMatch(result -> result.url().contains("/download")));
    }

    @Test
    void parsesProjectPageIdentityFieldsFromPublicHtmlFixture() {
        String html = "<html><head><title>Cave Dweller Reimagined - Minecraft Mods</title></head><body><main>"
                + "<h1>Cave Dweller Reimagined</h1><p>By Valk</p><p>Project ID 907713. Forge 1.20.1.</p>"
                + "<p>Main File cavedweller-RELEASE-1.0.0.jar</p>"
                + "<a href='/minecraft/search?class=mc-mods&category=mobs'>Mobs</a>"
                + "<a href='https://github.com/Valk/repo'>Source</a></main></body></html>";
        WebSearchResult result = source.parseProject(html,
                URI.create("https://www.curseforge.com/minecraft/mc-mods/reimagination-of-the-cavedweller"));

        assertEquals("Cave Dweller Reimagined", result.title());
        assertEquals("Valk", result.author());
        assertTrue(result.gameVersions().contains("1.20.1"));
        assertTrue(result.loaders().stream().anyMatch(value -> value.equalsIgnoreCase("forge")));
        assertTrue(result.categories().contains("Mobs"));
        assertTrue(result.fileNames().contains("cavedweller-RELEASE-1.0.0.jar"));
    }
}
