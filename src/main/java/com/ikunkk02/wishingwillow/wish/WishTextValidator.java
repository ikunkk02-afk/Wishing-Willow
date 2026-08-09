package com.ikunkk02.wishingwillow.wish;

public final class WishTextValidator {
    public static final int MAX_LENGTH = 512;

    private WishTextValidator() {
    }

    public static Validation validate(String input) {
        String normalized = input.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.length() > MAX_LENGTH) {
            return new Validation(null, WishRejectionReason.TOO_LONG);
        }
        if (normalized.strip().isEmpty()) {
            return new Validation(null, WishRejectionReason.EMPTY);
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '\u0000'
                    || (Character.isISOControl(character) && character != '\n' && character != '\t')) {
                return new Validation(null, WishRejectionReason.INVALID_CHARACTERS);
            }
        }
        return new Validation(normalized, WishRejectionReason.NONE);
    }

    public record Validation(String normalizedText, WishRejectionReason reason) {
        public boolean valid() {
            return reason == WishRejectionReason.NONE;
        }
    }
}
