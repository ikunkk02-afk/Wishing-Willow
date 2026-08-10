package com.ikunkk02.wishingwillow.ai;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public final class AiEndpointNormalizer {
    private static final String CHAT_SUFFIX = "/chat/completions";

    private AiEndpointNormalizer() {
    }

    public static Endpoints normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Base URL is empty");
        }
        final URI parsed;
        try {
            parsed = new URI(input.strip());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Base URL is invalid", exception);
        }
        String scheme = parsed.getScheme();
        if ((scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")))
                || parsed.getHost() == null
                || parsed.getUserInfo() != null
                || parsed.getQuery() != null
                || parsed.getFragment() != null) {
            throw new IllegalArgumentException("Base URL must be an HTTP(S) origin without credentials, query, or fragment");
        }

        List<String> segments = new ArrayList<>();
        String rawPath = parsed.getPath();
        if (rawPath != null) {
            for (String segment : rawPath.split("/+")) {
                if (!segment.isBlank()) {
                    segments.add(segment);
                }
            }
        }
        String normalizedPath = segments.isEmpty() ? "" : "/" + String.join("/", segments);
        String rootPath = normalizedPath.endsWith(CHAT_SUFFIX)
                ? normalizedPath.substring(0, normalizedPath.length() - CHAT_SUFFIX.length())
                : normalizedPath;
        String authority = parsed.getRawAuthority();
        String root = scheme.toLowerCase() + "://" + authority + rootPath;
        return new Endpoints(
                URI.create(root),
                URI.create(root + CHAT_SUFFIX),
                URI.create(root + "/models")
        );
    }

    public record Endpoints(URI root, URI chatCompletions, URI models) {
    }
}
