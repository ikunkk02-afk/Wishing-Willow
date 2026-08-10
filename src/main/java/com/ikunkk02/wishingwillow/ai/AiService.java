package com.ikunkk02.wishingwillow.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class AiService {
    private static final AiService INSTANCE = new AiService();

    private final ScheduledExecutorService executor;
    private final HttpClient httpClient;

    public AiService() {
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
        return provider(config).complete(request).handle((response, throwable) -> {
            if (throwable == null && response.assistantContent() != null && !response.assistantContent().isBlank()) {
                return AiConnectionResult.successful();
            }
            AiRequestException failure = throwable == null
                    ? new AiRequestException(AiErrorCategory.MALFORMED_RESPONSE, 200, false)
                    : unwrap(throwable);
            return AiConnectionResult.failure(failure.category(), failure.httpStatus());
        });
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
