package com.ikunkk02.wishingwillow.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleProviderTest {
    private HttpServer server;
    private ScheduledExecutorService executor;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        executor = Executors.newScheduledThreadPool(2);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void fallsBackFromJsonSchemaToJsonObject() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.incrementAndGet();
            if (request.contains("json_schema")) {
                respond(exchange, 400, "{\"error\":{\"message\":\"response_format json_schema unsupported\"}}");
            } else {
                respond(exchange, 200, completion("{\\\"ok\\\":true}"));
            }
        });
        OpenAiCompatibleProvider provider = provider("");
        AiResponse response = provider.complete(new AiRequest(
                "system", "user", 100, AiOutputMode.JSON_SCHEMA, WishInterpretationValidator.jsonSchema()
        )).join();
        assertEquals(AiOutputMode.JSON_OBJECT, response.outputMode());
        assertEquals(2, calls.get());
    }

    @Test
    void listsModelsWithoutAuthorizationWhenKeyIsEmpty() {
        server.createContext("/v1/models", exchange -> {
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"data\":[{\"id\":\"qwen\"},{\"id\":\"llama\"}]}");
        });
        AiModelListResult result = provider("").listModels().join();
        assertEquals(java.util.List.of("llama", "qwen"), result.models());
    }

    @Test
    void disablesDefaultThinkingForDeepSeekOnly() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, completion("OK"));
        });
        AiResponse response = provider("key", AiProviderType.DEEPSEEK)
                .complete(new AiRequest("system", "user", 32, AiOutputMode.TEXT, null)).join();
        assertEquals("OK", response.assistantContent());
        assertTrue(requestBody.get().contains("\"thinking\":{\"type\":\"disabled\"}"));
    }

    @Test
    void usesDocumentedDeepSeekJsonObjectModeAndRetriesOneEmptyContent() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (calls.incrementAndGet() == 1) {
                respond(exchange, 200, completion(""));
            } else {
                respond(exchange, 200, completion("{\\\"schema_version\\\":1}"));
            }
        });
        AiResponse response = provider("key", AiProviderType.DEEPSEEK).complete(new AiRequest(
                "Return JSON", "user", 1200, AiOutputMode.JSON_SCHEMA,
                WishInterpretationValidator.jsonSchema()
        )).join();
        assertEquals(AiOutputMode.JSON_OBJECT, response.outputMode());
        assertEquals(2, calls.get());
        assertTrue(requestBody.get().contains("\"type\":\"json_object\""));
        assertTrue(requestBody.get().contains("\"temperature\":0.0"));
        org.junit.jupiter.api.Assertions.assertFalse(requestBody.get().contains("json_schema"));
        String systemMessage = com.google.gson.JsonParser.parseString(requestBody.get()).getAsJsonObject()
                .getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString();
        assertTrue(systemMessage.contains("JSON Schema contract exactly"));
        assertTrue(systemMessage.contains("\"tone\""));
        assertTrue(systemMessage.contains("\"HORROR\""));
    }

    @Test
    void doesNotRetryUnauthorized() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 401, "{\"error\":{\"message\":\"unauthorized\"}}");
        });
        CompletionException exception = assertThrows(CompletionException.class, () -> provider("bad-key")
                .complete(new AiRequest("system", "user", 8, AiOutputMode.TEXT, null)).join());
        assertEquals(AiErrorCategory.UNAUTHORIZED, root(exception).category());
        assertEquals(1, calls.get());
    }

    @Test
    void retriesRateLimitOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            if (calls.incrementAndGet() == 1) {
                respond(exchange, 429, "{\"error\":{\"message\":\"rate limited\"}}");
            } else {
                respond(exchange, 200, completion("OK"));
            }
        });
        AiResponse response = provider("")
                .complete(new AiRequest("system", "user", 8, AiOutputMode.TEXT, null)).join();
        assertEquals("OK", response.assistantContent());
        assertEquals(2, calls.get());
    }

    @Test
    void fallsBackThroughJsonObjectToPlainText() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.incrementAndGet();
            if (request.contains("response_format")) {
                respond(exchange, 422, "{\"error\":{\"message\":\"response_format json unsupported\"}}");
            } else {
                respond(exchange, 200, completion("plain-json-content"));
            }
        });
        AiResponse response = provider("").complete(new AiRequest(
                "system", "user", 100, AiOutputMode.JSON_SCHEMA, WishInterpretationValidator.jsonSchema()
        )).join();
        assertEquals(AiOutputMode.TEXT, response.outputMode());
        assertEquals(3, calls.get());
    }

    @Test
    void classifiesModelUnavailableWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 404, "{\"error\":{\"message\":\"model does not exist\"}}");
        });
        CompletionException exception = assertThrows(CompletionException.class, () -> provider("")
                .complete(new AiRequest("system", "user", 8, AiOutputMode.TEXT, null)).join());
        assertEquals(AiErrorCategory.MODEL_UNAVAILABLE, root(exception).category());
        assertEquals(1, calls.get());
    }

    @Test
    void retriesServerErrorOnceAndRejectsMalformedSuccess() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            if (calls.incrementAndGet() == 1) {
                respond(exchange, 503, "{\"error\":{\"message\":\"unavailable\"}}");
            } else {
                respond(exchange, 200, "{\"not_choices\":[]}");
            }
        });
        CompletionException exception = assertThrows(CompletionException.class, () -> provider("")
                .complete(new AiRequest("system", "user", 8, AiOutputMode.TEXT, null)).join());
        assertEquals(AiErrorCategory.MALFORMED_RESPONSE, root(exception).category());
        assertEquals(2, calls.get());
    }

    @Test
    void limitsResponseBodySize() {
        server.createContext("/v1/chat/completions", exchange -> respond(
                exchange,
                200,
                "x".repeat(OpenAiCompatibleProvider.MAX_RESPONSE_BYTES + 1)
        ));
        CompletionException exception = assertThrows(CompletionException.class, () -> provider("")
                .complete(new AiRequest("system", "user", 8, AiOutputMode.TEXT, null)).join());
        assertEquals(AiErrorCategory.RESPONSE_TOO_LARGE, root(exception).category());
    }

    @Test
    void mapsTimeoutDnsAndRefusedConnections() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(250L);
                respond(exchange, 200, completion("too late"));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Client timeout closes the response stream.
            }
        });
        OpenAiCompatibleProvider provider = provider("", Duration.ofMillis(75), 5L);
        CompletionException exception = assertThrows(CompletionException.class, () -> provider
                .complete(new AiRequest("system", "user", 8, AiOutputMode.TEXT, null)).join());
        assertEquals(AiErrorCategory.TIMEOUT, root(exception).category());
        assertEquals(2, calls.get());
        assertEquals(AiErrorCategory.DNS_FAILURE,
                OpenAiCompatibleProvider.mapTransportFailure(new UnknownHostException()).category());
        assertEquals(AiErrorCategory.CONNECTION_REFUSED,
                OpenAiCompatibleProvider.mapTransportFailure(new ConnectException()).category());
    }

    @Test
    void sendsToolSchemaAndParsesToolCallWithoutExposingUnrelatedSecrets() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":["
                    + "{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"search_mod_web\","
                    + "\"arguments\":\"{\\\"query\\\":\\\"Cave Dweller\\\",\\\"preferred_domain\\\":\\\"CURSEFORGE\\\"}\"}}]}}]}");
        });
        AiToolResponse response = provider("provider-secret").completeTools(new AiToolRequest(
                List.of(AiConversationMessage.text("system", "no direct network"),
                        AiConversationMessage.text("user", "identify mod")),
                List.of(new AiToolDefinition("search_mod_web", "controlled search",
                        com.google.gson.JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject())), 128)).join();

        assertEquals(1, response.toolCalls().size());
        assertEquals("search_mod_web", response.toolCalls().get(0).name());
        assertTrue(requestBody.get().contains("\"tools\""));
        assertTrue(requestBody.get().contains("search_mod_web"));
        assertTrue(requestBody.get().contains("no direct network"));
        assertTrue(!requestBody.get().contains("provider-secret"));
    }

    @Test
    void synthesizesSessionLocalToolCallIdWhenCompatibleProviderOmitsIt() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 200,
                "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":["
                        + "{\"type\":\"function\",\"function\":{\"name\":\"probe\",\"arguments\":\"{}\"}}]}}]}"));
        AiToolResponse response = provider("").completeTools(new AiToolRequest(
                List.of(AiConversationMessage.text("user", "probe")),
                List.of(new AiToolDefinition("probe", "probe",
                        com.google.gson.JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject())), 32)).join();
        assertEquals(1, response.toolCalls().size());
        assertFalse(response.toolCalls().get(0).id().isBlank());
        assertTrue(response.toolCalls().get(0).id().startsWith("provider-call-"));
    }

    @Test
    void connectionProbeCachesToolCallingSupportWithoutFailingTextConnectivity() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            if (calls.incrementAndGet() == 1) respond(exchange, 200, completion("OK"));
            else respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":["
                    + "{\"id\":\"probe-1\",\"type\":\"function\",\"function\":{\"name\":\"wishing_willow_tool_probe\","
                    + "\"arguments\":\"{\\\"value\\\":\\\"OK\\\"}\"}}]}}]}");
        });
        AiConfig config = new AiConfig(AiExecutionMode.PLAYER_PROVIDED, AiProviderType.CUSTOM,
                baseUrl, "", "test-model");
        AiService service = new AiService();
        AiConnectionResult result = service.testConnection(config).join();
        assertTrue(result.success());
        assertEquals(ToolCallingSupport.SUPPORTED, result.toolCallingSupport());
        assertEquals(ToolCallingSupport.SUPPORTED, service.toolCallingSupport(config));
    }

    @Test
    void failedToolProbeStillReportsAUsableJsonCompatibilityConnection() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 200, completion("OK")));
        AiConfig config = new AiConfig(AiExecutionMode.PLAYER_PROVIDED, AiProviderType.CUSTOM,
                baseUrl, "", "test-model");
        AiConnectionResult result = new AiService().testConnection(config).join();
        assertTrue(result.success());
        assertEquals(ToolCallingSupport.UNSUPPORTED, result.toolCallingSupport());
    }

    @Test
    void classifiesExplicitToolRejectionForAutomaticFallback() {
        server.createContext("/v1/chat/completions", exchange ->
                respond(exchange, 422, "{\"error\":{\"message\":\"tools are not supported\"}}"));
        CompletionException exception = assertThrows(CompletionException.class, () -> provider("")
                .completeTools(new AiToolRequest(List.of(AiConversationMessage.text("user", "identify")),
                        List.of(new AiToolDefinition("search_mod_web", "search",
                                com.google.gson.JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject())), 32)).join());

        assertEquals(AiErrorCategory.UNSUPPORTED_FEATURE, root(exception).category());
    }

    private OpenAiCompatibleProvider provider(String apiKey) {
        return provider(apiKey, AiProviderType.CUSTOM);
    }

    private OpenAiCompatibleProvider provider(String apiKey, AiProviderType providerType) {
        return provider(apiKey, providerType, Duration.ofSeconds(2), 10L);
    }

    private OpenAiCompatibleProvider provider(String apiKey, Duration requestTimeout, long retryDelayMillis) {
        return provider(apiKey, AiProviderType.CUSTOM, requestTimeout, retryDelayMillis);
    }

    private OpenAiCompatibleProvider provider(
            String apiKey,
            AiProviderType providerType,
            Duration requestTimeout,
            long retryDelayMillis
    ) {
        AiConfig config = new AiConfig(
                AiExecutionMode.PLAYER_PROVIDED,
                providerType,
                baseUrl,
                apiKey,
                "test-model"
        );
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .executor(executor)
                .build();
        return new OpenAiCompatibleProvider(config, client, executor, requestTimeout, retryDelayMillis);
    }

    private static String completion(String content) {
        return "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static AiRequestException root(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            if (current instanceof AiRequestException requestException) {
                return requestException;
            }
            current = current.getCause();
        }
        return (AiRequestException) current;
    }
}
