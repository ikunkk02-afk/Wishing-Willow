package com.ikunkk02.wishingwillow.research;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModKnowledgeCacheTest {
    @TempDir
    Path temporary;

    @Test
    void exactTripleHitsButVersionOrHashChangeMisses() {
        ModKnowledgeCache cache = new ModKnowledgeCache(temporary.resolve("knowledge"));
        String hash = "a".repeat(128);
        ModFingerprint fingerprint = new ModFingerprint("example", "1.0", "example.jar", hash);
        KnowledgeEntry entry = KnowledgeEntry.scanned(
                ModFingerprintServiceTest.info("example", "1.0", "example.jar", "content"),
                fingerprint, ModCategory.CONTENT).withState(ResearchState.READY);
        cache.save(entry);
        assertEquals("example", cache.load(fingerprint).installed().modId());
        assertNull(cache.load(new ModFingerprint("example", "1.1", "example.jar", hash)));
        assertNull(cache.load(new ModFingerprint("example", "1.0", "example.jar", "b".repeat(128))));
    }

    @Test
    void corruptEntryFailsClosed() throws Exception {
        ModKnowledgeCache cache = new ModKnowledgeCache(temporary.resolve("knowledge"));
        ModFingerprint fingerprint = new ModFingerprint("broken", "1", "broken.jar", "c".repeat(128));
        Path path = cache.root().resolve("mods").resolve("broken-" + fingerprint.sha512() + ".json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "not-json");
        assertNull(cache.load(fingerprint));
    }

    @Test
    void migratesSchemaOneAndLegacySourceNamesWithoutLosingKnowledge() throws Exception {
        ModKnowledgeCache cache = new ModKnowledgeCache(temporary.resolve("knowledge"));
        ModFingerprint fingerprint = new ModFingerprint("legacy", "1", "legacy.jar", "d".repeat(128));
        KnowledgeEntry legacy = new KnowledgeEntry(1, ModFingerprintServiceTest.info("legacy", "1", "legacy.jar", "content"),
                fingerprint, ModCategory.CONTENT, ResearchState.READY, KnowledgeLevel.UNDERSTOOD,
                Set.of(ResearchSource.CURSEFORGE, ResearchSource.SOURCE_README), List.of(), null,
                "registry-digest", "", 123L);
        Path path = cache.root().resolve("mods").resolve("legacy-" + fingerprint.sha512() + ".json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, new Gson().toJson(legacy), StandardCharsets.UTF_8);

        KnowledgeEntry migrated = cache.load(fingerprint);
        assertEquals(2, migrated.schemaVersion());
        assertEquals(KnowledgeLevel.UNDERSTOOD, migrated.knowledgeLevel());
        assertEquals("registry-digest", migrated.registryDigest());
        assertEquals(Set.of(ResearchSource.CURSEFORGE_API, ResearchSource.GITHUB_README), migrated.sources());
        assertEquals(com.ikunkk02.wishingwillow.research.web.IdentityConfidenceLevel.UNRESOLVED,
                migrated.webResearch().identity().level());
    }
}
