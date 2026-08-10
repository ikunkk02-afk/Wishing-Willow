package com.ikunkk02.wishingwillow.research.web;

import com.ikunkk02.wishingwillow.research.ModFingerprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebResearchCacheTest {
    @TempDir Path temporary;

    @Test
    void bindsSearchCacheToSha512AndExpiresSanitizedWebDataAfterSevenDays() throws Exception {
        WebResearchCache cache = new WebResearchCache(temporary.resolve("web"));
        ModFingerprint fingerprint = new ModFingerprint("example", "1", "example.jar", "a".repeat(128));
        WebSearchResult result = new WebSearchResult("Example", "https://www.curseforge.com/minecraft/mc-mods/example",
                "clean snippet", "author", List.of("1.20.1"), List.of("forge"), List.of("Mods"), List.of(), "CURSEFORGE");
        cache.saveSearch(fingerprint, "CURSEFORGE", "query", List.of(result));

        assertEquals(Duration.ofDays(7), WebResearchCache.TTL);
        assertEquals(List.of(result), cache.loadSearch(fingerprint, "CURSEFORGE", "query"));
        assertNull(cache.loadSearch(new ModFingerprint("example", "1", "example.jar", "b".repeat(128)),
                "CURSEFORGE", "query"));

        Path stored;
        try (var files = Files.walk(temporary)) {
            stored = files.filter(path -> path.toString().endsWith(".json")).findFirst().orElseThrow();
        }
        String expired = Files.readString(stored, StandardCharsets.UTF_8)
                .replaceFirst("\\\"fetchedAt\\\"\\s*:\\s*\\d+", "\\\"fetchedAt\\\": 0");
        Files.writeString(stored, expired, StandardCharsets.UTF_8);
        assertNull(cache.loadSearch(fingerprint, "CURSEFORGE", "query"));
    }
}
