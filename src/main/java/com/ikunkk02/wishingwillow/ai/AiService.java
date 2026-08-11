package com.ikunkk02.wishingwillow.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import com.google.gson.JsonObject;

public final class AiService {
    private static final AiService INSTANCE = new AiService(true);

    private final ScheduledExecutorService executor;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<String, ToolCallingSupport> toolSupport = new ConcurrentHashMap<>();
    private final boolean persistedSupportEnabled;

    public AiService() {
        this(false);
    }

    private AiService(boolean persistedSupportEnabled) {
        this.persistedSupportEnabled = persistedSupportEnabled;
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "wishing-willow-ai-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newScheduledThreadPool(2, threadFactory);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executor)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public static AiService getInstance() {
        return INSTANCE;
    }

    public AiProvider provider(AiConfig config) {
        return new OpenAiCompatibleProvider(config, httpClient, executor);
    }

    public CompletableFuture<AiConnectionResult> testConnection(AiConfig config) {
        if (!config.isConfigured()) {
            return CompletableFuture.completedFuture(
                    AiConnectionResult.failure(AiErrorCategory.NOT_CONFIGURED, 0)
            );
        }
        AiRequest request = new AiRequest(
                "Return a short plain-text response.",
                "Return exactly: OK",
                32,
                AiOutputMode.TEXT,
                null
        );
        AiProvider provider = provider(config);
        return provider.complete(request).thenCompose(response -> {
            if (response.assistantContent() == null || response.assistantContent().isBlank()) {
                return CompletableFuture.completedFuture(AiConnectionResult.failure(AiErrorCategory.MALFORMED_RESPONSE, 200));
            }
            return probeToolCallingSupport(config, provider)
                    .thenApply(AiConnectionResult::successful);
        }).exceptionally(throwable -> {
            AiRequestException failure = throwable == null
                    ? new AiRequestException(AiErrorCategory.MALFORMED_RESPONSE, 200, false)
                    : unwrap(throwable);
            return AiConnectionResult.failure(failure.category(), failure.httpStatus());
        });
    }

    public ToolCallingSupport toolCallingSupport(AiConfig config) {
        ToolCallingSupport cached = toolSupport.get(key(config));
        if (cached != null) return cached;
        ToolCallingSupport persisted = persistedSupportEnabled
                ? AiConfigManager.getInstance().toolCallingSupport(config) : ToolCallingSupport.UNKNOWN;
        if (persisted != ToolCallingSupport.UNKNOWN) toolSupport.put(key(config), persisted);
        return persisted;
    }

    public CompletableFuture<ToolCallingSupport> probeToolCallingSupport(AiConfig config) {
        ToolCallingSupport cached = toolCallingSupport(config);
        if (cached != ToolCallingSupport.UNKNOWN) return CompletableFuture.completedFuture(cached);
        if (!config.isConfigured()) return CompletableFuture.completedFuture(ToolCallingSupport.UNKNOWN);
        return probeToolCallingSupport(config, provider(config));
    }

    private CompletableFuture<ToolCallingSupport> probeToolCallingSupport(AiConfig config, AiProvider provider) {
        JsonObject properties = new JsonObject();
        JsonObject value = new JsonObject(); value.addProperty("type", "string"); value.addProperty("const", "OK");
        properties.add("value", value);
        JsonObject schema = new JsonObject(); schema.addProperty("type", "object"); schema.add("properties", properties);
        com.google.gson.JsonArray required = new com.google.gson.JsonArray(); required.add("value");
        schema.add("required", required); schema.addProperty("additionalProperties", false);
        return provider.completeTools(new AiToolRequest(
                        List.of(AiConversationMessage.text("system", "Call the provided safe probe tool exactly once."),
                                AiConversationMessage.text("user", "Call wishing_willow_tool_probe with value OK.")),
                        List.of(new AiToolDefinition("wishing_willow_tool_probe",
                                "Safe tool-calling capability probe.", schema)), 64))
                .orTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .handle((probe, probeFailure) -> {
                    ToolCallingSupport status;
                    if (probeFailure == null && probe != null && probe.toolCalls().stream()
                            .anyMatch(call -> call.name().equals("wishing_willow_tool_probe"))) {
                        status = ToolCallingSupport.SUPPORTED;
                    } else if (probeFailure == null && probe != null) {
                        status = ToolCallingSupport.UNSUPPORTED;
                    } else {
                        AiRequestException failure = unwrap(probeFailure);
                        status = failure.category() == AiErrorCategory.UNSUPPORTED_FEATURE
                                ? ToolCallingSupport.UNSUPPORTED : ToolCallingSupport.UNKNOWN;
                    }
                    recordToolCallingSupport(config, status);
                    return status;
                });
    }

    public void recordToolCallingSupport(AiConfig config, ToolCallingSupport status) {
        if (config == null || status == null || status == ToolCallingSupport.UNKNOWN) return;
        toolSupport.put(key(config), status);
        if (persistedSupportEnabled) {
            AiConfigManager.getInstance().updateToolCallingSupport(config, status);
        }
    }

    public void clearToolCallingSupportCache() { toolSupport.clear(); }

    public void retainOnlyToolCallingSupport(AiConfig config) {
        ToolCallingSupport retained = toolSupport.get(key(config));
        toolSupport.clear();
        if (retained != null) toolSupport.put(key(config), retained);
    }

    private static String key(AiConfig config) {
        return config.providerType().name() + "\n" + config.baseUrl() + "\n" + config.model();
    }

    public CompletableFuture<AiModelListResult> listModels(AiConfig config) {
        if (!config.isConfigured() && (config.baseUrl().isBlank() || config.baseUrl().length() > AiConfig.MAX_BASE_URL_LENGTH)) {
            return CompletableFuture.completedFuture(
                    AiModelListResult.failure(AiErrorCategory.NOT_CONFIGURED, 0)
            );
        }
        try {
            AiConfig queryConfig = config.model().isBlank()
                    ? new AiConfig(
                    config.executionMode(), config.providerType(), config.baseUrl(), config.apiKey(), "model-list-placeholder"
            )
                    : config;
            return provider(queryConfig).listModels();
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(
                    AiModelListResult.failure(AiErrorCategory.NOT_CONFIGURED, 0)
            );
        }
    }

    private static AiRequestException unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof AiRequestException requestException
                ? requestException
                : new AiRequestException(AiErrorCategory.UNKNOWN, current);
    }
}
