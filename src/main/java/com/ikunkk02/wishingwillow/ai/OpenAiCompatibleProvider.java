package com.ikunkk02.wishingwillow.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class OpenAiCompatibleProvider implements AiProvider {
    public static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private static final Map<String, AiOutputMode> OUTPUT_MODE_CACHE = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();

    private final AiConfig config;
    private final AiEndpointNormalizer.Endpoints endpoints;
    private final HttpClient client;
    private final ScheduledExecutorService executor;
    private final Duration requestTimeout;
    private final long retryDelayMillis;

    public OpenAiCompatibleProvider(
            AiConfig config,
            HttpClient client,
            ScheduledExecutorService executor
    ) {
        this(config, client, executor, DEFAULT_REQUEST_TIMEOUT, 1000L);
    }

    OpenAiCompatibleProvider(
            AiConfig config,
            HttpClient client,
            ScheduledExecutorService executor,
            Duration requestTimeout,
            long retryDelayMillis
    ) {
        if (!config.isConfigured()) {
            throw new IllegalArgumentException(AiErrorCategory.NOT_CONFIGURED.name());
        }
        this.config = config;
        this.endpoints = AiEndpointNormalizer.normalize(config.baseUrl());
        this.client = client;
        this.executor = executor;
        this.requestTimeout = requestTimeout;
        this.retryDelayMillis = retryDelayMillis;
    }

    @Override
    public AiProviderType type() {
        return config.providerType();
    }

    @Override
    public CompletableFuture<AiResponse> complete(AiRequest request) {
        AiOutputMode startingMode = request.outputMode();
        if (startingMode == AiOutputMode.JSON_SCHEMA && config.providerType() == AiProviderType.DEEPSEEK) {
            // DeepSeek documents JSON Object mode, but not OpenAI's JSON Schema response format.
            startingMode = AiOutputMode.JSON_OBJECT;
        }
        if (startingMode == AiOutputMode.JSON_SCHEMA) {
            startingMode = OUTPUT_MODE_CACHE.getOrDefault(cacheKey(), AiOutputMode.JSON_SCHEMA);
        }
        return completeWithFallback(request, startingMode);
    }

    @Override
    public CompletableFuture<AiModelListResult> listModels() {
        HttpRequest.Builder builder = authorizedRequest(endpoints.models())
                .GET()
                .timeout(requestTimeout);
        return sendBody(builder.build()).handle((response, throwable) -> {
            if (throwable != null) {
                AiRequestException failure = unwrap(throwable);
                return AiModelListResult.failure(failure.category(), failure.httpStatus());
            }
            int status = response.statusCode();
            if (status == 404 || status == 405 || status == 501) {
                return AiModelListResult.unsupported(status);
            }
            if (status < 200 || status >= 300) {
                return AiModelListResult.failure(classifyHttp(status, response.body()), status);
            }
            try {
                JsonElement data = JsonParser.parseString(response.body()).getAsJsonObject().get("data");
                if (data == null || !data.isJsonArray()) {
                    return AiModelListResult.unsupported(status);
                }
                List<String> models = new ArrayList<>();
                for (JsonElement entry : data.getAsJsonArray()) {
                    if (entry.isJsonObject() && entry.getAsJsonObject().has("id")) {
                        String id = entry.getAsJsonObject().get("id").getAsString().strip();
                        if (!id.isEmpty() && id.length() <= AiConfig.MAX_MODEL_LENGTH) {
                            models.add(id);
                        }
                    }
                }
                models.sort(Comparator.naturalOrder());
                return AiModelListResult.success(models.stream().distinct().toList());
            } catch (RuntimeException exception) {
                return AiModelListResult.unsupported(status);
            }
        });
    }

    @Override
    public CompletableFuture<AiToolResponse> completeTools(AiToolRequest request) {
        return sendToolOnce(request);
    }

    private CompletableFuture<AiToolResponse> sendToolOnce(AiToolRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("stream", false);
        body.addProperty("max_tokens", request.maxTokens());
        if (config.providerType() == AiProviderType.DEEPSEEK) {
            JsonObject thinking = new JsonObject(); thinking.addProperty("type", "disabled"); body.add("thinking", thinking);
        }
        JsonArray messages = new JsonArray();
        for (AiConversationMessage value : request.messages()) messages.add(toolMessage(value));
        body.add("messages", messages);
        JsonArray tools = new JsonArray();
        for (AiToolDefinition definition : request.tools()) {
            JsonObject function = new JsonObject();
            function.addProperty("name", definition.name());
            function.addProperty("description", definition.description());
            function.add("parameters", definition.parameters());
            JsonObject tool = new JsonObject(); tool.addProperty("type", "function"); tool.add("function", function); tools.add(tool);
        }
        body.add("tools", tools); body.addProperty("tool_choice", "auto");
        HttpRequest httpRequest = authorizedRequest(endpoints.chatCompletions())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .timeout(requestTimeout).build();
        return sendBody(httpRequest).thenApply(response -> {
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String lower = response.body().toLowerCase(Locale.ROOT);
                boolean unsupported = (status == 400 || status == 404 || status == 422)
                        && (lower.contains("tool") || lower.contains("function"))
                        && (lower.contains("unsupported") || lower.contains("unknown") || lower.contains("not support"));
                throw new AiRequestException(unsupported ? AiErrorCategory.UNSUPPORTED_FEATURE
                        : classifyHttp(status, response.body()), status, false);
            }
            try {
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonObject message = root.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
                String content = message.has("content") && !message.get("content").isJsonNull()
                        ? message.get("content").getAsString() : "";
                List<AiToolCall> calls = new ArrayList<>();
                if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
                    for (JsonElement element : message.getAsJsonArray("tool_calls")) {
                        JsonObject call = element.getAsJsonObject(); JsonObject function = call.getAsJsonObject("function");
                        calls.add(new AiToolCall(call.get("id").getAsString(), function.get("name").getAsString(),
                                function.get("arguments").getAsString()));
                    }
                }
                if (content.isBlank() && calls.isEmpty()) throw new IllegalStateException();
                return new AiToolResponse(content, calls, status);
            } catch (RuntimeException exception) {
                if (exception instanceof AiRequestException requestException) throw requestException;
                throw new AiRequestException(AiErrorCategory.MALFORMED_RESPONSE, status, false);
            }
        });
    }

    private static JsonObject toolMessage(AiConversationMessage value) {
        JsonObject message = new JsonObject(); message.addProperty("role", value.role());
        if (value.role().equals("tool")) {
            message.addProperty("tool_call_id", value.toolCallId()); message.addProperty("content", value.content()); return message;
        }
        if (!value.content().isBlank()) message.addProperty("content", value.content());
        else message.add("content", com.google.gson.JsonNull.INSTANCE);
        if (!value.toolCalls().isEmpty()) {
            JsonArray calls = new JsonArray();
            for (AiToolCall valueCall : value.toolCalls()) {
                JsonObject function = new JsonObject(); function.addProperty("name", valueCall.name());
                function.addProperty("arguments", valueCall.argumentsJson());
                JsonObject call = new JsonObject(); call.addProperty("id", valueCall.id());
                call.addProperty("type", "function"); call.add("function", function); calls.add(call);
            }
            message.add("tool_calls", calls);
        }
        return message;
    }

    private CompletableFuture<AiResponse> completeWithFallback(AiRequest request, AiOutputMode mode) {
        AiRequest selected = request.withOutputMode(mode);
        return retryOnce(() -> sendChatOnce(selected), 0).handle((response, throwable) -> {
            if (throwable == null) {
                if (request.outputMode() == AiOutputMode.JSON_SCHEMA) {
                    OUTPUT_MODE_CACHE.put(cacheKey(), mode);
                }
                return CompletableFuture.completedFuture(response);
            }
            AiRequestException failure = unwrap(throwable);
            if (failure.outputModeUnsupported() && mode != AiOutputMode.TEXT) {
                AiOutputMode fallback = mode == AiOutputMode.JSON_SCHEMA
                        ? AiOutputMode.JSON_OBJECT
                        : AiOutputMode.TEXT;
                return completeWithFallback(request, fallback);
            }
            return CompletableFuture.<AiResponse>failedFuture(failure);
        }).thenCompose(future -> future);
    }

    private CompletableFuture<AiResponse> retryOnce(
            Supplier<CompletableFuture<AiResponse>> request,
            int attempt
    ) {
        return request.get().handle((response, throwable) -> {
            if (throwable == null) {
                return CompletableFuture.completedFuture(response);
            }
            AiRequestException failure = unwrap(throwable);
            if (attempt == 0 && isTransient(failure.category())) {
                CompletableFuture<AiResponse> delayed = new CompletableFuture<>();
                executor.schedule(
                        () -> retryOnce(request, 1).whenComplete((value, retryError) -> {
                            if (retryError == null) {
                                delayed.complete(value);
                            } else {
                                delayed.completeExceptionally(retryError);
                            }
                        }),
                        retryDelayMillis,
                        TimeUnit.MILLISECONDS
                );
                return delayed;
            }
            return CompletableFuture.<AiResponse>failedFuture(failure);
        }).thenCompose(future -> future);
    }

    private CompletableFuture<AiResponse> sendChatOnce(AiRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("stream", false);
        body.addProperty("max_tokens", request.maxTokens());
        if (config.providerType() == AiProviderType.DEEPSEEK) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "disabled");
            body.add("thinking", thinking);
        }
        JsonArray messages = new JsonArray();
        messages.add(message("system", request.systemMessage()));
        messages.add(message("user", request.userMessage()));
        body.add("messages", messages);
        if (request.outputMode() == AiOutputMode.JSON_OBJECT) {
            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_object");
            body.add("response_format", responseFormat);
        } else if (request.outputMode() == AiOutputMode.JSON_SCHEMA) {
            JsonObject jsonSchema = new JsonObject();
            jsonSchema.addProperty("name", request.jsonSchemaName() == null
                    ? "structured_response" : request.jsonSchemaName());
            jsonSchema.addProperty("strict", true);
            jsonSchema.add("schema", request.jsonSchema());
            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_schema");
            responseFormat.add("json_schema", jsonSchema);
            body.add("response_format", responseFormat);
        }

        HttpRequest httpRequest = authorizedRequest(endpoints.chatCompletions())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .timeout(requestTimeout)
                .build();
        return sendBody(httpRequest).thenApply(response -> {
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                boolean unsupported = isOutputModeUnsupported(status, response.body(), request.outputMode());
                throw new AiRequestException(classifyHttp(status, response.body()), status, unsupported);
            }
            try {
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray choices = root.getAsJsonArray("choices");
                if (choices == null || choices.isEmpty()) {
                    throw new IllegalStateException();
                }
                JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                JsonElement content = message == null ? null : message.get("content");
                if (content == null || !content.isJsonPrimitive() || !content.getAsJsonPrimitive().isString()) {
                    throw new IllegalStateException();
                }
                String assistantContent = content.getAsString();
                if (assistantContent.isBlank()) {
                    throw new AiRequestException(AiErrorCategory.EMPTY_RESPONSE, status, false);
                }
                return new AiResponse(assistantContent, status, request.outputMode());
            } catch (AiRequestException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new AiRequestException(AiErrorCategory.MALFORMED_RESPONSE, status, false);
            }
        });
    }

    private CompletableFuture<BodyResponse> sendBody(HttpRequest request) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApplyAsync(response -> {
                    try (InputStream input = response.body()) {
                        byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (bytes.length > MAX_RESPONSE_BYTES) {
                            throw new AiRequestException(AiErrorCategory.RESPONSE_TOO_LARGE, response.statusCode(), false);
                        }
                        return new BodyResponse(response.statusCode(), new String(bytes, StandardCharsets.UTF_8));
                    } catch (IOException exception) {
                        throw new CompletionException(new AiRequestException(AiErrorCategory.UNKNOWN, exception));
                    }
                }, executor)
                .exceptionally(throwable -> {
                    throw new CompletionException(mapTransportFailure(throwable));
                });
    }

    private HttpRequest.Builder authorizedRequest(java.net.URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).header("Accept", "application/json");
        if (!config.apiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.apiKey());
        }
        return builder;
    }

    private String cacheKey() {
        return endpoints.chatCompletions() + "\n" + config.model();
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static boolean isOutputModeUnsupported(int status, String body, AiOutputMode mode) {
        if (mode == AiOutputMode.TEXT || (status != 400 && status != 422)) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("response_format")
                || lower.contains("json_schema")
                || lower.contains("json_object")
                || (lower.contains("unsupported") && lower.contains("json"));
    }

    private static AiErrorCategory classifyHttp(int status, String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        if (status == 401) {
            return AiErrorCategory.UNAUTHORIZED;
        }
        if (status == 403) {
            return AiErrorCategory.FORBIDDEN;
        }
        if ((status == 400 || status == 404)
                && (lower.contains("model") && (lower.contains("not found") || lower.contains("does not exist")
                || lower.contains("invalid") || lower.contains("unavailable")))) {
            return AiErrorCategory.MODEL_UNAVAILABLE;
        }
        if (status == 404) {
            return AiErrorCategory.ENDPOINT_NOT_FOUND;
        }
        if (status == 429) {
            return AiErrorCategory.RATE_LIMITED;
        }
        if (status >= 500) {
            return AiErrorCategory.SERVER_ERROR;
        }
        return AiErrorCategory.UNKNOWN;
    }

    private static boolean isTransient(AiErrorCategory category) {
        return category == AiErrorCategory.RATE_LIMITED
                || category == AiErrorCategory.SERVER_ERROR
                || category == AiErrorCategory.TIMEOUT
                || category == AiErrorCategory.EMPTY_RESPONSE;
    }

    static AiRequestException mapTransportFailure(Throwable throwable) {
        Throwable cause = rootCause(throwable);
        if (cause instanceof AiRequestException requestException) {
            return requestException;
        }
        if (cause instanceof HttpTimeoutException || cause instanceof java.util.concurrent.TimeoutException) {
            return new AiRequestException(AiErrorCategory.TIMEOUT, cause);
        }
        if (cause instanceof UnknownHostException) {
            return new AiRequestException(AiErrorCategory.DNS_FAILURE, cause);
        }
        if (cause instanceof ConnectException) {
            return new AiRequestException(AiErrorCategory.CONNECTION_REFUSED, cause);
        }
        return new AiRequestException(AiErrorCategory.UNKNOWN, cause);
    }

    private static AiRequestException unwrap(Throwable throwable) {
        Throwable cause = rootCause(throwable);
        return cause instanceof AiRequestException requestException
                ? requestException
                : mapTransportFailure(cause);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record BodyResponse(int statusCode, String body) {
    }
}
