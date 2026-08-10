package com.ikunkk02.wishingwillow.research;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
