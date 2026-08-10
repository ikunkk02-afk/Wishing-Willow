package com.ikunkk02.wishingwillow.ai;

public enum AiProviderType {
    DEEPSEEK("https://api.deepseek.com", "deepseek-v4-flash", true),
    OLLAMA("http://localhost:11434/v1", "", false),
    LM_STUDIO("http://localhost:1234/v1", "", false),
    CUSTOM("", "", false);

    private final String defaultBaseUrl;
    private final String defaultModel;
    private final boolean apiKeyRequired;

    AiProviderType(String defaultBaseUrl, String defaultModel, boolean apiKeyRequired) {
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.apiKeyRequired = apiKeyRequired;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public boolean apiKeyRequired() {
        return apiKeyRequired;
    }
}
