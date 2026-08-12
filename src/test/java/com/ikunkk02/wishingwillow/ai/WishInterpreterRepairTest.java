package com.ikunkk02.wishingwillow.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishInterpreterRepairTest {
    private static final String VALID_INTERPRETATION = """
            {"schema_version":2,"intent":"wealth","literal_goal":"The player wants diamonds",
             "contract":{"type":"OBTAIN_RESOURCE","required_outcome":"The player must obtain ten real diamonds","hard_constraints":[
               {"kind":"RESOURCE_KIND","operator":"EQUALS","semantic":"item","quantity":0,"amount":0,"required":true},
               {"kind":"RESOURCE_SEMANTIC","operator":"EQUALS","semantic":"diamond","quantity":0,"amount":0,"required":true},
               {"kind":"MINIMUM_QUANTITY","operator":"AT_LEAST","semantic":"","quantity":10,"amount":0,"required":true},
               {"kind":"REAL_RESOURCE","operator":"REQUIRED","semantic":"","quantity":0,"amount":0,"required":true},
               {"kind":"PLAYER_ACCESSIBLE","operator":"REQUIRED","semantic":"","quantity":0,"amount":0,"required":true}]},
             "fulfillment":{"mode":"ABSURD","method":"Diamonds arrive inconveniently","styles":["QUANTITY_ABSURDITY"],"absurdity":70},
             "reasoning_summary":"The request is granted with an ironic delivery","tone":"IRONIC",
             "severity":35,"delivery":"IMMEDIATE","required_capabilities":["GIVE_ITEM"]}
            """;
    private static final String VALID = """
            {"decision":"ACCEPT","rejection_code":"NONE","player_message":"","reason":"",
             "interpretation":%s,"program":{"schema_version":1,"goal":"Give ten diamonds",
             "core_actions":[{"action":"give_item","parameters":{"item":"minecraft:diamond","count":10}}],
             "presentation_actions":[],"skill":"","unknown_capability":""}}
            """.formatted(VALID_INTERPRETATION);

    @Test
    void repairsOneSchemaInvalidInterpretationWithoutRelaxingValidation() {
        SequenceProvider provider = new SequenceProvider(
                VALID.replace("\"IRONIC\"", "\"MISCHIEVOUS\""),
                VALID
        );
        WishInterpretationResult result = new WishInterpreter(config -> provider)
                .interpret(config(), "我想要十颗钻石").join();

        assertEquals(InterpretationState.SUCCESS, result.state());
        assertEquals(WishTone.IRONIC, result.interpretation().tone());
        assertEquals(2, provider.requests.size());
        AiRequest repair = provider.requests.get(1);
        assertTrue(repair.systemMessage().contains("repairing one previous invalid response"));
        assertTrue(repair.userMessage().contains("UNTRUSTED_INVALID_INTERPRETATION_JSON"));
        assertTrue(repair.userMessage().contains("MISCHIEVOUS"));
    }

    @Test
    void locallyNormalizesOutOfRangeProgramWithoutAnotherAiRequest() {
        SequenceProvider provider = new SequenceProvider(
                VALID.replace("\"action\":\"give_item\",\"parameters\":{\"item\":\"minecraft:diamond\",\"count\":10}",
                        "\"action\":\"FOLLOW-PLAYER\",\"parameters\":{\"max_entities\":100}")
        );

        WishInterpretationResult result = new WishInterpreter(config -> provider)
                .interpret(config(), "让附近的生物跟着我").join();

        assertEquals(InterpretationState.SUCCESS, result.state());
        assertEquals(1, provider.requests.size());
        assertEquals("follow_player", result.program().coreActions().get(0).action());
        assertEquals(32, result.program().coreActions().get(0).parameters()
                .get("max_entities").getAsInt());
    }

    @Test
    void sendsStructuredUnrecoverableValidationDetailToAiRepair() {
        String missingItem = VALID.replace(
                "{\"item\":\"minecraft:diamond\",\"count\":10}", "{\"count\":10}");
        SequenceProvider provider = new SequenceProvider(missingItem, VALID);

        WishInterpretationResult result = new WishInterpreter(config -> provider)
                .interpret(config(), "我想要十颗钻石").join();

        assertEquals(InterpretationState.SUCCESS, result.state());
        assertEquals(2, provider.requests.size());
        String repairMessage = provider.requests.get(1).userMessage();
        assertTrue(repairMessage.contains("INVALID_WISH_PROGRAM:MISSING_REQUIRED_PARAMETER_item"));
        assertTrue(repairMessage.contains("\"action\":\"give_item\""));
        assertTrue(repairMessage.contains("\"parameter\":\"item\""));
    }

    @Test
    void thirdAttemptCanRecoverAfterTwoSchemaInvalidResponses() {
        SequenceProvider provider = new SequenceProvider(
                VALID.replace("\"IRONIC\"", "\"MISCHIEVOUS\""),
                VALID.replace("\"IRONIC\"", "\"GREEDY\""),
                VALID
        );
        WishInterpretationResult result = new WishInterpreter(config -> provider)
                .interpret(config(), "我想要十颗钻石").join();

        assertEquals(InterpretationState.SUCCESS, result.state());
        assertEquals(3, provider.requests.size());
    }

    @Test
    void rejectsCandidateOnlyAfterAllThreeSchemaAttemptsFail() {
        SequenceProvider provider = new SequenceProvider(
                VALID.replace("\"IRONIC\"", "\"MISCHIEVOUS\""),
                VALID.replace("\"IRONIC\"", "\"GREEDY\""),
                VALID.replace("\"IRONIC\"", "\"HOSTILE_COMEDY\"")
        );
        WishInterpretationResult result = new WishInterpreter(config -> provider)
                .interpret(config(), "all buffs").join();

        assertEquals(InterpretationState.INVALID_RESPONSE, result.state());
        assertEquals(WishInterpreter.MAX_ATTEMPTS, provider.requests.size());
    }

    @Test
    void retriesOneProviderLevelMalformedEnvelopeWithTheRepairContract() {
        FailureThenResponseProvider provider = new FailureThenResponseProvider(VALID);

        WishInterpretationResult result = new WishInterpreter(config -> provider)
                .interpret(config(), "I want ten diamonds").join();

        assertEquals(InterpretationState.SUCCESS, result.state());
        assertEquals(2, provider.requests.size());
        assertTrue(provider.requests.get(1).systemMessage()
                .contains("repairing one previous invalid response"));
    }

    @Test
    void firstPromptSeparatesResourceKindFromPhysicalDelivery() {
        SequenceProvider provider = new SequenceProvider(VALID);
        new WishInterpreter(config -> provider)
                .interpret(config(), "让64个钻石从天而降").join();

        String prompt = provider.requests.get(0).systemMessage();
        assertTrue(prompt.contains("RESOURCE KIND MATTERS"));
        assertTrue(prompt.contains("ITEM + physical falling from above -> spawn_item_rain"));
        assertTrue(prompt.contains("BLOCK + physical falling from above -> spawn_falling_block"));
        assertTrue(prompt.contains("minecraft:diamond != minecraft:diamond_block"));
        assertTrue(prompt.contains("Never put an item registry id into a block parameter"));
        assertTrue(prompt.contains("group=beneficial"));
        assertTrue(prompt.contains("\"resource_kind\":\"item\""));
        assertTrue(prompt.contains("\"resource_kind\":\"block\""));
        assertTrue(!prompt.contains("group=BENEFICIAL"));
    }

    @Test
    void capabilityAliasIsNormalizedLocallyWithoutRepair() {
        SequenceProvider provider = new SequenceProvider(VALID.replace(
                "\"required_capabilities\":[\"GIVE_ITEM\"]",
                "\"required_capabilities\":[\"REMOVE_MOBS\"]").replace(
                "\"action\":\"give_item\",\"parameters\":{\"item\":\"minecraft:diamond\",\"count\":10}",
                "\"action\":\"entity_suppression\",\"parameters\":{\"group\":\"all_mobs\",\"scope\":\"all_dimensions\",\"remove_existing\":true,\"prevent_future\":true,\"permanent\":true,\"disappearance_mode\":\"discard\",\"exclude_players\":true}"));

        WishInterpretationResult result = new WishInterpreter(config -> provider)
                .interpret(config(), "让世界上所有的生物消失，我不想看到任何生物").join();

        assertEquals(InterpretationState.SUCCESS, result.state());
        assertEquals(1, provider.requests.size());
        assertEquals(WishCapability.ENTITY_REMOVAL,
                result.interpretation().requiredCapabilities().get(0));
        assertEquals("entity_suppression", result.program().coreActions().get(0).action());
    }

    @Test
    void structuredRejectDoesNotEnterRepair() {
        SequenceProvider provider = new SequenceProvider("""
                {"decision":"REJECT","rejection_code":"EXTERNAL_SYSTEM_ACCESS",
                 "player_message":"This wish reaches beyond the world.",
                 "reason":"The request asks for external system access.",
                 "interpretation":null,"program":null}
                """);

        WishInterpretationResult result = new WishInterpreter(config -> provider)
                .interpret(config(), "帮我运行cmd").join();

        assertEquals(InterpretationState.REJECTED, result.state());
        assertEquals(WishRejectionCode.EXTERNAL_SYSTEM_ACCESS, result.rejection().code());
        assertEquals(1, provider.requests.size());
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

    private static final class FailureThenResponseProvider implements AiProvider {
        private final String response;
        private final java.util.ArrayList<AiRequest> requests = new java.util.ArrayList<>();

        private FailureThenResponseProvider(String response) {
            this.response = response;
        }

        @Override public AiProviderType type() { return AiProviderType.CUSTOM; }

        @Override
        public CompletableFuture<AiResponse> complete(AiRequest request) {
            requests.add(request);
            if (requests.size() == 1) {
                return CompletableFuture.failedFuture(
                        new AiRequestException(AiErrorCategory.MALFORMED_RESPONSE, 200, false));
            }
            return CompletableFuture.completedFuture(
                    new AiResponse(response, 200, AiOutputMode.JSON_OBJECT));
        }

        @Override
        public CompletableFuture<AiModelListResult> listModels() {
            return CompletableFuture.completedFuture(AiModelListResult.unsupported(501));
        }
    }
}
