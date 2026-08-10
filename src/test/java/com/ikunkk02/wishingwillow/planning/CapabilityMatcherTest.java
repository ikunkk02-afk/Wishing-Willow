package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.research.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;

class CapabilityMatcherTest {
    @Test void exactVerifiedHorrorCandidateBeatsLowConfidenceIdentifiedCandidate(){
        var verified=PlanningFixtures.entry("cavedweller","Cave Dweller Reimagined",KnowledgeLevel.VERIFIED,0.94,
                WishCapability.STALKING_ENTITY,FeatureType.ENTITY,RegistryEntryType.ENTITY,"cavedweller:cave_dweller",95,94);
        var weak=PlanningFixtures.entry("weakmod","Weak Mod",KnowledgeLevel.IDENTIFIED,0.45,
                WishCapability.STALKING_ENTITY,FeatureType.ENTITY,RegistryEntryType.ENTITY,"weakmod:watcher",99,99);
        var registry=PlanningFixtures.registry(Map.of(RegistryEntryType.ENTITY,List.of("cavedweller:cave_dweller","weakmod:watcher")));
        var catalog=new CapabilityMatcher().match("I never want to be alone",
                PlanningFixtures.interpretation(72,WishDelivery.HIDDEN,WishCapability.STALKING_ENTITY),
                new KnowledgeBaseSnapshot(KnowledgeBaseState.READY,false,List.of(weak,verified)),registry);
        assertEquals("cavedweller",catalog.candidates().get(0).sourceModId());
        assertEquals(MatchType.EXACT,catalog.candidates().get(0).matchType());
        assertTrue(catalog.candidates().get(0).matchScore()>catalog.candidates().get(1).matchScore());
    }

    @Test void compatibleAndCrossModCandidatesAreKept(){
        var first=PlanningFixtures.entry("cavedweller","Cave Dweller",KnowledgeLevel.VERIFIED,0.94,
                WishCapability.STALKING_ENTITY,FeatureType.ENTITY,RegistryEntryType.ENTITY,"cavedweller:cave_dweller",95,94);
        var second=PlanningFixtures.entry("otherhorror","Other Horror",KnowledgeLevel.VERIFIED,0.9,
                WishCapability.FRIENDLY_ENTITY,FeatureType.ENTITY,RegistryEntryType.ENTITY,"otherhorror:companion",80,90);
        var registry=PlanningFixtures.registry(Map.of(RegistryEntryType.ENTITY,List.of("cavedweller:cave_dweller","otherhorror:companion")));
        var catalog=new CapabilityMatcher().match("company",PlanningFixtures.interpretation(61,WishDelivery.HIDDEN,WishCapability.PERSISTENT_FOLLOWER),
                new KnowledgeBaseSnapshot(KnowledgeBaseState.READY,false,List.of(first,second)),registry);
        var modCandidates=catalog.candidates().stream().filter(c->c.sourceKind()==CandidateSourceKind.MOD_FEATURE).toList();
        assertTrue(modCandidates.stream().allMatch(c->c.matchType()==MatchType.COMPATIBLE));
        assertEquals(2,modCandidates.stream().map(CapabilityCandidate::sourceModId).distinct().count());
    }

    @Test void vanillaDiamondComesFromRealRegistryAndUnknownSpacecraftDoesNotHallucinate(){
        var registry=PlanningFixtures.registry(Map.of(RegistryEntryType.ITEM,List.of("minecraft:diamond")));
        var empty=new KnowledgeBaseSnapshot(KnowledgeBaseState.PARTIAL_READY,false,List.of());
        var diamonds=new CapabilityMatcher().match("I want 100 diamonds",PlanningFixtures.interpretation(30,WishDelivery.HIDDEN,WishCapability.GIVE_ITEM),empty,registry);
        assertEquals("minecraft:diamond",diamonds.candidates().get(0).registryResource().id());
        var spacecraft=new CapabilityMatcher().match("spaceship",PlanningFixtures.interpretation(50,WishDelivery.HIDDEN,WishCapability.SPACECRAFT),empty,registry);
        assertFalse(spacecraft.candidates().isEmpty());
        assertTrue(spacecraft.candidates().stream().allMatch(candidate -> candidate.matchType()==MatchType.APPROXIMATE));
        assertTrue(spacecraft.candidates().stream().allMatch(candidate -> candidate.registryResource()==null));
    }

    @Test void limitsEachCapabilityAndWholeCatalog(){
        var entries=java.util.stream.IntStream.range(0,40).mapToObj(i->PlanningFixtures.entry("weakmod"+i,"Mod "+i,KnowledgeLevel.VERIFIED,0.9,
                WishCapability.STALKING_ENTITY,FeatureType.ENTITY,RegistryEntryType.ENTITY,"weakmod"+i+":watcher",80,80)).toList();
        Map<RegistryEntryType,List<String>> values=Map.of(RegistryEntryType.ENTITY,entries.stream().map(e->e.knowledge().features().get(0).registryCandidates().get(0)).toList());
        var catalog=new CapabilityMatcher().match("watcher",PlanningFixtures.interpretation(61,WishDelivery.HIDDEN,WishCapability.STALKING_ENTITY),
                new KnowledgeBaseSnapshot(KnowledgeBaseState.READY,false,entries),PlanningFixtures.registry(values));
        assertEquals(5,catalog.candidates().size());
        assertTrue(catalog.candidates().size()<=CapabilityCatalog.MAX_CANDIDATES);
    }

    @Test void coversTheFiveRequestedWishFamiliesWithoutUnrelatedExplosion(){
        var dimensionMod=PlanningFixtures.entry("otherhorror","Dimension Mod",KnowledgeLevel.VERIFIED,0.93,
                WishCapability.DIMENSION_TRAVEL,FeatureType.DIMENSION,RegistryEntryType.DIMENSION,"otherhorror:lost_realm",10,95);
        var registry=PlanningFixtures.registry(Map.of(
                RegistryEntryType.EFFECT,List.of("minecraft:darkness","minecraft:strength"),
                RegistryEntryType.ENTITY,List.of("minecraft:wither","cavedweller:cave_dweller"),
                RegistryEntryType.ITEM,List.of("minecraft:diamond"),
                RegistryEntryType.DIMENSION,List.of("otherhorror:lost_realm")));
        var knowledge=new KnowledgeBaseSnapshot(KnowledgeBaseState.PARTIAL_READY,false,List.of(dimensionMod));
        CapabilityMatcher matcher=new CapabilityMatcher();

        var safeNight=matcher.match("I want to be safe tonight",PlanningFixtures.interpretation(55,WishDelivery.HIDDEN,
                WishCapability.MOB_BEHAVIOR,WishCapability.DARKNESS,WishCapability.WORLD_EVENT),knowledge,registry);
        assertTrue(safeNight.matchSets().stream().allMatch(set->set.quality()!=MatchType.UNSATISFIED));

        var strongest=matcher.match("I want to be the strongest",PlanningFixtures.interpretation(85,WishDelivery.HIDDEN,
                WishCapability.POWER_BUFF,WishCapability.POWERFUL_ENEMY),knowledge,registry);
        assertTrue(strongest.candidates().stream().anyMatch(c->c.registryResource()!=null&&c.registryResource().id().equals("minecraft:strength")));
        assertTrue(strongest.candidates().stream().anyMatch(c->c.registryResource()!=null&&c.registryResource().id().equals("minecraft:wither")));

        var diamonds=matcher.match("I want 100 diamonds",PlanningFixtures.interpretation(30,WishDelivery.HIDDEN,
                WishCapability.GIVE_ITEM),knowledge,registry);
        assertEquals("minecraft:diamond",diamonds.candidates().get(0).registryResource().id());

        var hiddenPlace=matcher.match("Take me somewhere nobody can find me",PlanningFixtures.interpretation(50,WishDelivery.HIDDEN,
                WishCapability.DIMENSION_TRAVEL),knowledge,registry);
        assertTrue(hiddenPlace.candidates().stream().anyMatch(c->c.registryResource()!=null&&c.registryResource().id().equals("otherhorror:lost_realm")));

        assertTrue(java.util.stream.Stream.of(safeNight,strongest,diamonds,hiddenPlace)
                .flatMap(catalog->catalog.candidates().stream()).noneMatch(c->c.providedCapability()==WishCapability.EXPLOSION));
    }

    @Test void executionSettingsRemoveThirdPartyCandidatesBeforePlanning(){
        var thirdParty=PlanningFixtures.entry("cavedweller","Cave Dweller",KnowledgeLevel.VERIFIED,0.95,
                WishCapability.STALKING_ENTITY,FeatureType.ENTITY,RegistryEntryType.ENTITY,
                "cavedweller:cave_dweller",95,95);
        var registry=PlanningFixtures.registry(Map.of(RegistryEntryType.ENTITY,
                List.of("cavedweller:cave_dweller","minecraft:wolf")));
        var settings=new ExecutionSettingsSnapshot(true,false,true,true,false,false,false,80,false);
        var catalog=new CapabilityMatcher().match("company",
                PlanningFixtures.interpretation(70,WishDelivery.HIDDEN,WishCapability.STALKING_ENTITY),
                new KnowledgeBaseSnapshot(KnowledgeBaseState.READY,false,List.of(thirdParty)),registry,settings);
        assertTrue(catalog.candidates().stream().noneMatch(candidate->candidate.registryResource()!=null
                && candidate.registryResource().id().startsWith("cavedweller:")));
    }
}
