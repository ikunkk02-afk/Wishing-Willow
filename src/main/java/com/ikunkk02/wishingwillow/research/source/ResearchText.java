package com.ikunkk02.wishingwillow.research.source;

import java.util.Locale;

final class ResearchText {
    static final int MAX_DOCUMENT_CHARS = 24 * 1024;

    private ResearchText() {
    }

    static String sanitize(String content, String contentType) {
        String lowerType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!(lowerType.contains("json") || lowerType.startsWith("text/") || lowerType.isBlank())) {
            throw new ResearchHttpClient.ResearchHttpException("UNSUPPORTED_CONTENT_TYPE", 0);
        }
        String value = content == null ? "" : content.replace('\u0000', ' ');
        if (lowerType.contains("html")) {
            value = value.replaceAll("(?is)<(script|style|noscript)[^>]*>.*?</\\1>", " ")
                    .replaceAll("(?s)<[^>]+>", " ")
                    .replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&amp;", "&").replace("&quot;", "\"")
                    .replace("&#39;", "'");
        }
        value = value.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\n{3,}", "\n\n").strip();
        return value.length() <= MAX_DOCUMENT_CHARS ? value : value.substring(0, MAX_DOCUMENT_CHARS);
    }
}
