package com.ikunkk02.wishingwillow.research;

public record ResearchConfig(String curseForgeApiKey) {
    public static final int MAX_KEY_LENGTH = 8192;

    public ResearchConfig {
        curseForgeApiKey = curseForgeApiKey == null ? "" : curseForgeApiKey.strip();
        if (curseForgeApiKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("CurseForge API key is too long");
        }
    }

    public static ResearchConfig defaults() {
        return new ResearchConfig("");
    }

    public boolean curseForgeEnabled() {
        return !curseForgeApiKey.isBlank();
    }

    @Override
    public String toString() {
        return "ResearchConfig{curseForgeApiKey='<redacted>'}";
    }
}
