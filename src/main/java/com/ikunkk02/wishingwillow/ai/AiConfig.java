package com.ikunkk02.wishingwillow.ai;

import java.util.Objects;

public final class AiConfig {
    public static final int MAX_BASE_URL_LENGTH = 2048;
    public static final int MAX_API_KEY_LENGTH = 8192;
    public static final int MAX_MODEL_LENGTH = 256;

    private final AiExecutionMode executionMode;
    private final AiProviderType providerType;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public AiConfig(
            AiExecutionMode executionMode,
            AiProviderType providerType,
            String baseUrl,
            String apiKey,
            String model
    ) {
        this.executionMode = Objects.requireNonNull(executionMode);
        this.providerType = Objects.requireNonNull(providerType);
        this.baseUrl = Objects.requireNonNullElse(baseUrl, "").strip();
        this.apiKey = Objects.requireNonNullElse(apiKey, "");
        this.model = Objects.requireNonNullElse(model, "").strip();
    }

    public static AiConfig defaults() {
        return forProvider(AiProviderType.DEEPSEEK);
    }

    public static AiConfig forProvider(AiProviderType providerType) {
        return new AiConfig(
                AiExecutionMode.PLAYER_PROVIDED,
                providerType,
                providerType.defaultBaseUrl(),
                "",
                providerType.defaultModel()
        );
    }

    public AiExecutionMode executionMode() {
        return executionMode;
    }

    public AiProviderType providerType() {
        return providerType;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String apiKey() {
        return apiKey;
    }

    public String model() {
        return model;
    }

    public boolean isConfigured() {
        if (executionMode != AiExecutionMode.PLAYER_PROVIDED
                || baseUrl.isEmpty()
                || model.isEmpty()
                || baseUrl.length() > MAX_BASE_URL_LENGTH
                || apiKey.length() > MAX_API_KEY_LENGTH
                || model.length() > MAX_MODEL_LENGTH
                || (providerType.apiKeyRequired() && apiKey.isBlank())) {
            return false;
        }
        try {
            AiEndpointNormalizer.normalize(baseUrl);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public AiConfig withProvider(AiProviderType newProvider) {
        return forProvider(newProvider);
    }

    @Override
    public String toString() {
        return "AiConfig{" +
                "executionMode=" + executionMode +
                ", providerType=" + providerType +
                ", baseUrl='<redacted>'" +
                ", apiKey='<redacted>'" +
                ", model='" + model + '\'' +
                '}';
    }
}
