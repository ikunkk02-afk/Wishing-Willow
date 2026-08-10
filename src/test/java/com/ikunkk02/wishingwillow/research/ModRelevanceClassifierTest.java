package com.ikunkk02.wishingwillow.research;

import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModRelevanceClassifierTest {
    private final ModRelevanceClassifier classifier = new ModRelevanceClassifier();

    @Test
    void lowersLibraryAndPerformanceButElevatesHorror() {
        RegistrySnapshot empty = RegistrySnapshot.empty();
        var library = classifier.classify(info("lib", "A multiplatform library API framework"), empty, List.of());
        var performance = classifier.classify(info("fast", "Performance optimization and memory usage fixes"), empty, List.of());
        var horror = classifier.classify(info("fear", "A psychological horror stalker monster"), empty, List.of());
        assertTrue(library.ignored());
        assertEquals(ModCategory.LIBRARY, library.category());
        assertTrue(performance.ignored());
        assertEquals(ModCategory.PERFORMANCE, performance.category());
        assertFalse(horror.ignored());
        assertEquals(ModCategory.HORROR, horror.category());
    }

    @Test
    void doesNotTreatNegatedHorrorAsHorror() {
        assertFalse(classifier.classify(info("calm", "Removes horror effects and disables horror"),
                RegistrySnapshot.empty(), List.of()).category() == ModCategory.HORROR);
    }

    @Test
    void usesDependencyRoleAndSemanticTechnologyBeforeRegistryCounts() {
        var library = classifier.classify(info("render_core", "Rendering support"),
                RegistrySnapshot.empty(), List.of(), true);
        assertTrue(library.ignored());
        assertEquals(ModCategory.API, library.category());

        RegistrySnapshot snapshot = new RegistrySnapshot(
                Map.of(RegistryEntryType.ENTITY, List.of("create:a", "create:b", "create:c")),
                Map.of("create", "create"), Set.of());
        var technology = classifier.classify(info("create", "Kinetic automation and machines"),
                snapshot, List.of(), false);
        assertEquals(ModCategory.TECHNOLOGY, technology.category());
    }

    private static InstalledModInfo info(String id, String description) {
        return ModFingerprintServiceTest.info(id, "1", id + ".jar", description);
    }
}
