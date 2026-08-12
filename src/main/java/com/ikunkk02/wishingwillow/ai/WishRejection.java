package com.ikunkk02.wishingwillow.ai;

import java.util.Locale;
import java.util.Set;

/** Sanitized semantic rejection returned by the model and rechecked by the server. */
public record WishRejection(WishRejectionCode code, String playerMessage, String reason) {
    public static final int MAX_PLAYER_MESSAGE_LENGTH = 160;
    public static final int MAX_REASON_LENGTH = 512;
    private static final Set<String> TECHNICAL_TERMS = Set.of(
            "json", "api", "http", "deepseek", "validator", "exception", "enum", "shell", "cmd");

    public WishRejection {
        if (code == null || code == WishRejectionCode.NONE) {
            throw new IllegalArgumentException("INVALID_REJECTION_CODE");
        }
        playerMessage = sanitizePlayerMessage(playerMessage);
        reason = sanitize(reason, MAX_REASON_LENGTH);
    }

    public static WishRejection sanitized(WishRejectionCode code, String playerMessage, String reason) {
        return new WishRejection(code, playerMessage, reason);
    }

    private static String sanitizePlayerMessage(String value) {
        String clean = sanitize(value, MAX_PLAYER_MESSAGE_LENGTH);
        String lower = clean.toLowerCase(Locale.ROOT);
        for (String term : TECHNICAL_TERMS) {
            if (lower.contains(term)) return "";
        }
        return clean;
    }

    private static String sanitize(String value, int maximum) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .replace('\n', ' ').replace('\r', ' ').strip();
        return clean.length() <= maximum ? clean : clean.substring(0, maximum);
    }
}
