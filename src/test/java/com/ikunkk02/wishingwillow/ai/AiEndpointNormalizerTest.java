package com.ikunkk02.wishingwillow.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiEndpointNormalizerTest {
    @Test
    void appendsEndpointsExactlyOnce() {
        var endpoints = AiEndpointNormalizer.normalize("https://example.com/v1/");
        assertEquals("https://example.com/v1", endpoints.root().toString());
        assertEquals("https://example.com/v1/chat/completions", endpoints.chatCompletions().toString());
        assertEquals("https://example.com/v1/models", endpoints.models().toString());
    }

    @Test
    void acceptsCompleteChatEndpointAndCollapsesDuplicateSlashes() {
        var endpoints = AiEndpointNormalizer.normalize("http://localhost:11434//v1///chat/completions/");
        assertEquals("http://localhost:11434/v1", endpoints.root().toString());
        assertEquals("http://localhost:11434/v1/chat/completions", endpoints.chatCompletions().toString());
    }

    @Test
    void doesNotInventV1ForDeepSeek() {
        var endpoints = AiEndpointNormalizer.normalize("https://api.deepseek.com");
        assertEquals("https://api.deepseek.com/chat/completions", endpoints.chatCompletions().toString());
    }

    @Test
    void rejectsUnsafeOrNonHttpUrls() {
        assertThrows(IllegalArgumentException.class, () -> AiEndpointNormalizer.normalize("file:///tmp/api"));
        assertThrows(IllegalArgumentException.class, () -> AiEndpointNormalizer.normalize("https://user:secret@example.com/v1"));
        assertThrows(IllegalArgumentException.class, () -> AiEndpointNormalizer.normalize("https://example.com/v1?key=value"));
    }
}
