package com.ikunkk02.wishingwillow.research;

public enum RegistryEntryType {
    ITEM(24),
    BLOCK(24),
    ENTITY(32),
    EFFECT(24),
    SOUND(32),
    PARTICLE(16),
    BIOME(16),
    STRUCTURE(24),
    DIMENSION(16);

    private final int promptLimit;

    RegistryEntryType(int promptLimit) {
        this.promptLimit = promptLimit;
    }

    public int promptLimit() {
        return promptLimit;
    }
}
