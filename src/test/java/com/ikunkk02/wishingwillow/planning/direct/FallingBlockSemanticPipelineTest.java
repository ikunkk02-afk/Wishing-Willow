package com.ikunkk02.wishingwillow.planning.direct;

import com.ikunkk02.wishingwillow.ai.*;
import com.ikunkk02.wishingwillow.contract.*;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishPlanPacket;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FallingBlockSemanticPipelineTest {
    @Test
    void hundredDiamondBlocksFromSkyStayDirectAndNeedNoAgentOrReviewer() {
        WishInterpretation interpretation = fallingResource("diamond_block", 100, true);
        WishRouteDecision route = new WishActionRouter().select("让100个钻石块从天而降", interpretation);
        AtomicInteger complexAgentCalls = new AtomicInteger();
        CountingProvider directProvider = new CountingProvider(shower("minecraft:diamond_block", 100));

        DirectActionPlanningResult result;
        if (route.route() == WishExecutionRoute.COMPLEX_AGENT) {
            complexAgentCalls.incrementAndGet();
            fail("Vanilla physical delivery was routed to the Complex Agent");
            return;
        } else {
            result = new DirectWishActionPlanner().plan(UUID.randomUUID(), directProvider,
                    "让100个钻石块从天而降", interpretation, emptyCatalog(), registry(),
                    ExecutionSettingsSnapshot.permissive()).join();
        }

        assertEquals(WishExecutionRoute.DIRECT_ACTION, route.route());
        assertEquals("semantic_expressible_by_vanilla_primitives", route.reason());
        assertEquals(0, complexAgentCalls.get());
        assertEquals(1, directProvider.calls.get());
        assertEquals(DirectActionPlanningResult.State.SUCCESS, result.state());
        WishPlanStep shower = result.compiled().draft().steps().stream()
                .filter(step -> step.action() == WishActionType.FALLING_BLOCK_SHOWER)
                .findFirst().orElseThrow();
        assertEquals("minecraft:diamond_block", shower.candidateReference().registryResource().id());
        assertTrue(shower.parameters().get("count").getAsInt() >= 100);
        assertEquals("DELIVER_TO_PLAYER", shower.parameters().get("landing_mode").getAsString());

        WishContractValidation proof = WishContractValidator.validate(interpretation, result.compiled().draft());
        assertEquals(WishContractValidationState.CONTRACT_FULFILLED, proof.state());
        assertEquals("FALLING_BLOCK_DELIVERY_PROVEN", proof.code());

        SubmitWishPlanPacket packet = SubmitWishPlanPacket.fromResult(UUID.randomUUID(), UUID.randomUUID(),
                result.result(), result.catalog());
        assertNotNull(packet.draftJson());
        assertEquals(WishPlanState.READY, WishPlanValidator.parseAndValidate(packet.draftJson(), interpretation,
                packet.catalog(), new RegistrySnapshotEnvironment(registry()),
                ExecutionSettingsSnapshot.permissive()).state());
    }

    @Test
    void sandAndGoldRainShareTheSameSemanticRecipe() {
        assertShower("让20个沙子从天上掉下来", "sand", "minecraft:sand", 20);
        assertShower("让金块像下雨一样从天空落下来", "gold_block", "minecraft:gold_block", 32);
    }

    @Test
    void unsupportedEntityRainCannotMasqueradeAsOrdinarySpawn() {
        WishContract contract = new WishContract(WishContractType.CHANGE_WORLD_STATE,
                "Ten chickens physically fall from the sky", List.of(
                constraint(WishConstraintKind.DELIVERY_SEMANTIC, "entity_rain", 0)));
        WishInterpretation interpretation = interpretation(contract, List.of(WishCapability.SPAWN_ENTITY));
        CapabilityCandidate chicken = new CapabilityCandidate("candidate-001", WishCapability.SPAWN_ENTITY,
                WishCapability.SPAWN_ENTITY, MatchType.EXACT, CandidateSourceKind.VANILLA_REGISTRY,
                "minecraft", "Minecraft", "1.20.1", "minecraft:chicken",
                com.ikunkk02.wishingwillow.research.FeatureType.ENTITY,
                new com.ikunkk02.wishingwillow.research.VerifiedRegistryResource(
                        RegistryEntryType.ENTITY, "minecraft:chicken"), "test",
                com.ikunkk02.wishingwillow.research.KnowledgeLevel.VERIFIED,
                1, 1, 0, 100, 20, 100);
        WishPlanStep plainSpawn = new WishPlanStep(0, WishStepTiming.IMMEDIATE, 0, WishTriggerType.NONE,
                WishActionType.SPAWN_ENTITY, WishCapability.SPAWN_ENTITY, chicken.candidateId(),
                WishTargetType.AREA,
                com.google.gson.JsonParser.parseString("{\"count\":10,\"distance_min\":2,\"distance_max\":8}")
                        .getAsJsonObject(), "test", chicken.reference());
        assertEquals(WishContractValidationState.AI_REVIEW_REQUIRED,
                WishContractValidator.validate(interpretation, List.of(plainSpawn)).state());
    }

    @Test
    void explicitCaveDwellerSpecialTrackingStillUsesComplexAgent() {
        WishContract contract = new WishContract(WishContractType.PERSISTENT_CONDITION,
                "Cave Dweller uses its own mod tracking mechanism", List.of(
                constraint(WishConstraintKind.STATE_METRIC, "special_tracking", 0)));
        WishRouteDecision route = new WishActionRouter().select(
                "让洞穴居住者使用它模组自己的特殊追踪机制追我",
                interpretation(contract, List.of(WishCapability.STALKING_ENTITY)));
        assertEquals(WishExecutionRoute.COMPLEX_AGENT, route.route());
    }

    private static void assertShower(String wish, String semantic, String id, int count) {
        WishInterpretation interpretation = fallingResource(semantic, count, false);
        assertEquals(WishExecutionRoute.DIRECT_ACTION,
                new WishActionRouter().select(wish, interpretation).route());
        DirectActionPlanningResult result = new DirectWishActionPlanner().plan(new CountingProvider(shower(id, count)),
                wish, interpretation, emptyCatalog(), registry(), ExecutionSettingsSnapshot.permissive()).join();
        WishPlanStep action = result.compiled().draft().steps().stream()
                .filter(step -> step.action() == WishActionType.FALLING_BLOCK_SHOWER).findFirst().orElseThrow();
        assertEquals(id, action.candidateReference().registryResource().id());
        assertEquals(count, action.parameters().get("count").getAsInt());
    }

    private static WishInterpretation fallingResource(String semantic, int count, boolean requiresReview) {
        java.util.ArrayList<WishHardConstraint> constraints = new java.util.ArrayList<>(List.of(
                constraint(WishConstraintKind.RESOURCE_SEMANTIC, semantic, 0),
                new WishHardConstraint(WishConstraintKind.MINIMUM_QUANTITY,
                        WishConstraintOperator.AT_LEAST, "", count, 0, true),
                new WishHardConstraint(WishConstraintKind.REAL_RESOURCE,
                        WishConstraintOperator.REQUIRED, "", 0, 0, true),
                new WishHardConstraint(WishConstraintKind.PLAYER_ACCESSIBLE,
                        WishConstraintOperator.REQUIRED, "", 0, 0, true),
                constraint(WishConstraintKind.DELIVERY_SEMANTIC, "fall_from_sky", 0)));
        if (requiresReview) constraints.add(constraint(WishConstraintKind.CUSTOM_SEMANTIC, "fall_from_sky", 0));
        WishContract contract = new WishContract(WishContractType.OBTAIN_RESOURCE,
                "The player must obtain at least " + count + " real " + semantic + " blocks that fell from the sky.",
                constraints);
        return interpretation(contract, List.of(WishCapability.GIVE_ITEM));
    }

    private static WishHardConstraint constraint(WishConstraintKind kind, String semantic, int quantity) {
        return new WishHardConstraint(kind, WishConstraintOperator.REQUIRED, semantic, quantity, 0, true);
    }

    private static WishInterpretation interpretation(WishContract contract, List<WishCapability> capabilities) {
        return new WishInterpretation(2, "semantic_delivery", contract.requiredOutcome(), contract,
                new WishFulfillment(WishFulfillmentMode.ABSURD, "Physical delivery",
                        List.of(FulfillmentStyle.PHYSICAL_ABSURDITY), 85), "Vanilla composition",
                WishTone.ABSURD, 60, WishDelivery.IMMEDIATE, capabilities);
    }

    private static String shower(String resource, int count) {
        return """
                {"route":"DIRECT_ACTION","summary":"Physical block rain","actions":[
                  {"type":"FALLING_BLOCK_SHOWER","target":"SELF","resource":"%s","parameters":{
                    "count":%d,"spawn_height":28,"radius":10,"interval_ticks":2,
                    "landing_mode":"DELIVER_TO_PLAYER","spread":"RANDOM"}}],
                 "absurdity":{"style":"NONE","intensity":0,"modifiers":[]}}
                """.formatted(resource, count);
    }

    private static RegistrySnapshot registry() {
        return new RegistrySnapshot(Map.of(
                RegistryEntryType.BLOCK, List.of("minecraft:diamond_block", "minecraft:sand", "minecraft:gold_block"),
                RegistryEntryType.ITEM, List.of("minecraft:diamond")),
                Map.of("minecraft", "minecraft"), Set.of());
    }

    private static CapabilityCatalog emptyCatalog() {
        return CapabilityCatalog.create(List.of(), List.of(), "READY", "", registry().digest());
    }

    private static final class CountingProvider implements AiProvider {
        private final String response;
        private final AtomicInteger calls = new AtomicInteger();
        private CountingProvider(String response) { this.response = response; }
        @Override public AiProviderType type() { return AiProviderType.CUSTOM; }
        @Override public CompletableFuture<AiResponse> complete(AiRequest request) {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(new AiResponse(response, 200, AiOutputMode.JSON_SCHEMA));
        }
        @Override public CompletableFuture<AiModelListResult> listModels() {
            return CompletableFuture.completedFuture(AiModelListResult.success(List.of("test")));
        }
    }
}
