package com.ikunkk02.wishingwillow.research;

import java.util.Locale;

public record ModFingerprint(String modId, String version, String fileName, String sha512) {
    public ModFingerprint {
        sha512 = sha512 == null ? "" : sha512.toLowerCase(Locale.ROOT);
    }

    public boolean available() {
        return sha512.matches("[0-9a-f]{128}");
    }

    public String cacheKey() {
        return modId + "\n" + version + "\n" + sha512;
    }
}
