package com.ikunkk02.wishingwillow.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishInterpreterRepairTest {
    private static final String VALID = """
            {"schema_version":1,"intent":"wealth","literal_goal":"The player wants diamonds",
             "loophole":"The form was not specified","twisted_outcome":"Diamonds arrive inconveniently",
             "reasoning_summary":"The request is granted with an ironic delivery","tone":"IRONIC",
             "severity":35,"delivery":"IMMEDIATE","required_capabilities":["GIVE_ITEM"]}
            """;

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
    void rejectsCandidateWhenTheSingleRepairIsStillInvalid() {
        SequenceProvider provider = new SequenceProvider(
                VALID.replace("\"IRONIC\"", "\"MISCHIEVOUS\""),
                VALID.replace("\"IRONIC\"", "\"GREEDY\"")
        );
        WishInterpretationResult result = new WishInterpreter(config -> provider)
                .interpret(config(), "我想要十颗钻石").join();

        assertEquals(InterpretationState.INVALID_RESPONSE, result.state());
        assertEquals(2, provider.requests.size());
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
