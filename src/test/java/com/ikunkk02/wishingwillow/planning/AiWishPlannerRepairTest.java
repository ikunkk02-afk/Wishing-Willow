package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiExecutionMode;
import com.ikunkk02.wishingwillow.ai.AiModelListResult;
import com.ikunkk02.wishingwillow.ai.AiOutputMode;
import com.ikunkk02.wishingwillow.ai.AiProvider;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.ai.AiRequest;
import com.ikunkk02.wishingwillow.ai.AiResponse;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.planning.ai.AiWishPlanner;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiWishPlannerRepairTest {
    @Test
    void repairsBudgetExceededDiamondPlanIntoReadyBoundedPlan() {
        var interpretation = PlanningFixtures.interpretation(35, WishDelivery.IMMEDIATE,
                WishCapability.GIVE_ITEM, WishCapability.BLOCK_CHANGE);
        var item = PlanningFixtures.candidate("candidate-001", WishCapability.GIVE_ITEM,
                RegistryEntryType.ITEM, "minecraft:diamond");
        var block = PlanningFixtures.candidate("candidate-002", WishCapability.BLOCK_CHANGE,
                RegistryEntryType.BLOCK, "minecraft:diamond_block");
        var catalog = PlanningFixtures.catalog(item, block);
        SequenceProvider provider = new SequenceProvider(overBudgetPlan(), repairedPlan());

        WishPlanResult result = new AiWishPlanner(config -> provider).plan(
                config(), "我想要10组钻石", interpretation, context(), catalog,
                PlanningFixtures.environment(true, true)).join();

        assertEquals(WishPlanError.NONE, result.error());
        assertEquals(2, result.draft().steps().size());
        assertEquals(WishActionType.GIVE_ITEM, result.draft().steps().get(0).action());
        assertEquals(WishActionType.CHANGE_BLOCK, result.draft().steps().get(1).action());
        assertEquals(2, provider.requests.size());
        AiRequest repair = provider.requests.get(1);
        assertTrue(repair.systemMessage().contains("severity=35"));
        assertTrue(repair.systemMessage().contains("at most 3 steps"));
        assertTrue(repair.userMessage().contains("BUDGET_EXCEEDED"));
        assertTrue(repair.userMessage().contains("maximum_destructive_cost"));
    }

    @Test
    void doesNotAcceptASecondOverBudgetPlan() {
        var interpretation = PlanningFixtures.interpretation(35, WishDelivery.IMMEDIATE,
                WishCapability.GIVE_ITEM, WishCapability.BLOCK_CHANGE);
        var catalog = PlanningFixtures.catalog(
                PlanningFixtures.candidate("candidate-001", WishCapability.GIVE_ITEM,
                        RegistryEntryType.ITEM, "minecraft:diamond"),
                PlanningFixtures.candidate("candidate-002", WishCapability.BLOCK_CHANGE,
                        RegistryEntryType.BLOCK, "minecraft:diamond_block"));
        SequenceProvider provider = new SequenceProvider(overBudgetPlan(), overBudgetPlan());

        WishPlanResult result = new AiWishPlanner(config -> provider).plan(
                config(), "我想要10组钻石", interpretation, context(), catalog,
                PlanningFixtures.environment(true, true)).join();

        assertEquals(WishPlanError.BUDGET_EXCEEDED, result.error());
        assertEquals(2, provider.requests.size());
    }

    private static String overBudgetPlan() {
        return """
                {"schema_version":1,"summary":"Too many falling diamonds","delivery":"IMMEDIATE",
                 "severity":35,"estimated_duration":"SHORT","steps":[
                  {"step_index":0,"timing":"IMMEDIATE","delay_seconds":0,"trigger":"NONE","action":"GIVE_ITEM","capability":"GIVE_ITEM","candidate_id":"candidate-001","target":"PLAYER","parameters":{"count":64},"selection_reason":"Grant diamonds"},
                  {"step_index":1,"timing":"IMMEDIATE","delay_seconds":0,"trigger":"NONE","action":"CHANGE_BLOCK","capability":"BLOCK_CHANGE","candidate_id":"candidate-002","target":"WORLD","parameters":{"distance_min":2,"distance_max":4},"selection_reason":"First falling block"},
                  {"step_index":2,"timing":"IMMEDIATE","delay_seconds":0,"trigger":"NONE","action":"CHANGE_BLOCK","capability":"BLOCK_CHANGE","candidate_id":"candidate-002","target":"WORLD","parameters":{"distance_min":3,"distance_max":5},"selection_reason":"Second falling block"},
                  {"step_index":3,"timing":"IMMEDIATE","delay_seconds":0,"trigger":"NONE","action":"CHANGE_BLOCK","capability":"BLOCK_CHANGE","candidate_id":"candidate-002","target":"WORLD","parameters":{"distance_min":4,"distance_max":6},"selection_reason":"Third falling block"}]}
                """;
    }

    private static String repairedPlan() {
        return """
                {"schema_version":1,"summary":"A bounded diamond windfall","delivery":"IMMEDIATE",
                 "severity":35,"estimated_duration":"SHORT","steps":[
                  {"step_index":0,"timing":"IMMEDIATE","delay_seconds":0,"trigger":"NONE","action":"GIVE_ITEM","capability":"GIVE_ITEM","candidate_id":"candidate-001","target":"PLAYER","parameters":{"count":64},"selection_reason":"Grant a safe bounded stack"},
                  {"step_index":1,"timing":"IMMEDIATE","delay_seconds":0,"trigger":"NONE","action":"CHANGE_BLOCK","capability":"BLOCK_CHANGE","candidate_id":"candidate-002","target":"WORLD","parameters":{"distance_min":2,"distance_max":4},"selection_reason":"Preserve the block-form twist within budget"}]}
                """;
    }

    private static WishContextSnapshot context() {
        return new WishContextSnapshot("minecraft:overworld", 0, "DAY", "CLEAR",
                20, 20, 20, 0, "survival", "minecraft:plains", 64, "surface",
                "minecraft:air", List.of(), List.of(), 0, 0);
    }

    private static AiConfig config() {
        return new AiConfig(AiExecutionMode.PLAYER_PROVIDED, AiProviderType.CUSTOM,
                "https://example.invalid/v1", "", "test-model");
    }

    private static final class SequenceProvider implements AiProvider {
        private final Queue<String> responses;
        private final java.util.ArrayList<AiRequest> requests = new java.util.ArrayList<>();

        private SequenceProvider(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override public AiProviderType type() { return AiProviderType.CUSTOM; }

        @Override
        public CompletableFuture<AiResponse> complete(AiRequest request) {
            requests.add(request);
            return CompletableFuture.completedFuture(
                    new AiResponse(responses.remove(), 200, AiOutputMode.JSON_OBJECT));
        }

        @Override
        public CompletableFuture<AiModelListResult> listModels() {
            return CompletableFuture.completedFuture(AiModelListResult.unsupported(501));
        }
    }
}
