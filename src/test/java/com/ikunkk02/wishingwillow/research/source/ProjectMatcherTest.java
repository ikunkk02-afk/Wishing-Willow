package com.ikunkk02.wishingwillow.research.source;

import com.ikunkk02.wishingwillow.research.ModFingerprintServiceTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMatcherTest {
    @Test
    void exactCompatibleProjectScoresAboveUnrelatedProject() {
        var mod = ModFingerprintServiceTest.info("watcher_mod", "1.0", "watcher_mod-1.0.jar",
                "A watcher horror creature");
        double exact = ProjectMatcher.score(mod, "watcher mod", "watcher-mod", "author",
                "A watcher horror creature", List.of("1.20.1"), List.of("forge"),
                List.of("watcher_mod-1.0.jar"));
        double unrelated = ProjectMatcher.score(mod, "Machines", "machines", "someone",
                "Technology", List.of("1.20.1"), List.of("forge"), List.of("machines.jar"));
        assertTrue(exact >= 0.82);
        assertTrue(exact - unrelated >= 0.12);
    }

    @Test
    void matchesCompactModIdToSpacedProjectSlug() {
        var mod = ModFingerprintServiceTest.info("cavedweller", "1.0.0",
                "cavedweller-RELEASE-1.0.0.jar", "A reimagined cave terror");
        double score = ProjectMatcher.score(mod, "reimagined cave dweller", "reimagined-cave-dweller",
                "author", "A reimagination of the cave dweller", List.of("1.20.1"),
                List.of("forge"), List.of());
        assertTrue(score >= 0.82);
    }
}
