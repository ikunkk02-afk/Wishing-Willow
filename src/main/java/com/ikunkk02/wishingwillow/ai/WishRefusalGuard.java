package com.ikunkk02.wishingwillow.ai;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Rejects explicit assistant refusal prose before any AI-authored text can become player-visible. */
public final class WishRefusalGuard {
    private static final String REFUSAL_ACTION =
            "(?:fulfill|grant|comply|satisfy|complete|perform|execute|help|assist|provide|"
                    + "do\\s+(?:that|this|so))";
    private static final String REFUSAL_QUALIFIER =
            "(?:(?:safely|ethically|legally|directly|properly|in\\s+good\\s+conscience)\\s+)*";
    private static final List<Pattern> REFUSAL_PATTERNS = List.of(
            Pattern.compile("\\b(?:i|we)\\s+(?:cannot|can't|am\\s+unable\\s+to|are\\s+unable\\s+to|"
                    + "refuse\\s+to|will\\s+not|won't)\\s+" + REFUSAL_QUALIFIER + REFUSAL_ACTION + "\\b"),
            Pattern.compile("\\b(?:cannot|can't|unable\\s+to|refuse\\s+to|will\\s+not|won't)\\s+"
                    + REFUSAL_QUALIFIER + REFUSAL_ACTION + "\\b"),
            Pattern.compile("\\b(?:wish|request)\\s+(?:cannot|can't|will\\s+not|won't)\\s+be\\s+"
                    + "(?:fulfilled|granted|completed|satisfied)\\b"),
            Pattern.compile("\\b(?:impossible|unsafe)\\s+to\\s+" + REFUSAL_ACTION + "\\b"),
            Pattern.compile("\\bno\\s+safe\\s+way\\s+to\\s+" + REFUSAL_ACTION + "\\b"),
            Pattern.compile("(?:\\u6211|\\u6211\\u4eec)\\s*(?:\\u4e0d\\u80fd|\\u65e0\\u6cd5|"
                    + "\\u4e0d\\u53ef\\u4ee5|\\u62d2\\u7edd)\\s*(?:\\u6ee1\\u8db3|\\u5b9e\\u73b0|"
                    + "\\u5b8c\\u6210|\\u6267\\u884c|\\u5e2e\\u52a9)"),
            Pattern.compile("(?:\\u8fd9|\\u8be5)?\\s*(?:\\u4e2a)?\\s*(?:\\u613f\\u671b|\\u8bf7\\u6c42)"
                    + "\\s*(?:\\u65e0\\u6cd5|\\u4e0d\\u80fd|\\u4e0d\\u53ef\\u4ee5)\\s*"
                    + "(?:\\u88ab)?\\s*(?:\\u6ee1\\u8db3|\\u5b9e\\u73b0|\\u5b8c\\u6210|\\u6267\\u884c)"),
            Pattern.compile("\\u62d2\\u7edd\\s*(?:\\u6ee1\\u8db3|\\u5b9e\\u73b0|\\u5b8c\\u6210|"
                    + "\\u6267\\u884c)\\s*(?:\\u8fd9|\\u8be5)?\\s*(?:\\u4e2a)?\\s*"
                    + "(?:\\u613f\\u671b|\\u8bf7\\u6c42)")
    );

    private WishRefusalGuard() {
    }

    public static boolean containsRefusal(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        return REFUSAL_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    public static void requireAllowed(String... values) {
        for (String value : values) {
            if (containsRefusal(value)) throw new IllegalArgumentException("REFUSAL_RESPONSE");
        }
    }
}
