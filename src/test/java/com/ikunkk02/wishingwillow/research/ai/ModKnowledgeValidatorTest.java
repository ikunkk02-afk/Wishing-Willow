package com.ikunkk02.wishingwillow.research.ai;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.ModFingerprintServiceTest;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.ResearchSource;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModKnowledgeValidatorTest {
    @Test
    void removesHallucinatedRegistryIdAndKeepsRealTypedResource() {
        InstalledModInfo mod = ModFingerprintServiceTest.info("fakehorror", "1.0", "fakehorror.jar", "horror");
        RegistrySnapshot snapshot = new RegistrySnapshot(
                Map.of(RegistryEntryType.ENTITY, List.of("fakehorror:watcher")),
                Map.of("fakehorror", "fakehorror"), Set.of());
        String json = """
                {"schema_version":1,"mod_id":"fakehorror","name":"Fake Horror","version":"1.0",
                 "category":"HORROR","summary":"A watcher stalks players.","horror_score":92,"wish_relevance":95,
                 "themes":["stalking"],"features":[{"name":"Watcher","type":"ENTITY",
                 "description":"Stalks the player","possible_capabilities":["STALKING_ENTITY"],
                 "registry_candidates":["fakehorror:watcher","fakehorror:super_ghost"],"confidence":0.94}],
                 "available_capabilities":["STALKING_ENTITY","NOT_A_REAL_CAPABILITY"],"research_confidence":0.91}
                """;
        var result = ModKnowledgeValidator.parseAndValidate(json, mod, snapshot,
                Set.of(ResearchSource.LOCAL_METADATA), 1.0);
        assertEquals(KnowledgeLevel.VERIFIED, result.knowledgeLevel());
        assertEquals(List.of("fakehorror:watcher"), result.features().get(0).registryCandidates());
        assertFalse(result.features().get(0).registryCandidates().contains("fakehorror:super_ghost"));
        assertTrue(result.availableCapabilities().contains(WishCapability.STALKING_ENTITY));
        assertEquals(1, result.availableCapabilities().size());
    }
}
