package com.ikunkk02.wishingwillow.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AiConfigManagerTest {
    @TempDir Path temporary;

    @Test void toolCallingSupportPersistsForTheSameProviderEndpointAndModel() throws Exception {
        Path path = temporary.resolve("ai-client.json");
        AiConfig config = config("http://127.0.0.1:12345/v1", "model-a");
        AiConfigManager manager = new AiConfigManager(path);
        assertTrue(manager.save(config));
        manager.updateToolCallingSupport(config, ToolCallingSupport.SUPPORTED);

        AiConfigManager reloaded = new AiConfigManager(path);
        assertEquals(ToolCallingSupport.SUPPORTED, reloaded.toolCallingSupport(reloaded.get()));
        assertTrue(Files.readString(path).contains("\"tool_calling_support\": \"SUPPORTED\""));
    }

    @Test void changingProviderBaseUrlOrModelInvalidatesPersistedSupport() {
        Path path = temporary.resolve("ai-client.json");
        AiConfig original = config("http://127.0.0.1:12345/v1", "model-a");
        AiConfigManager manager = new AiConfigManager(path);
        assertTrue(manager.save(original));
        manager.updateToolCallingSupport(original, ToolCallingSupport.SUPPORTED);

        assertEquals(ToolCallingSupport.UNKNOWN,
                manager.toolCallingSupport(config("http://127.0.0.1:54321/v1", "model-a")));
        assertEquals(ToolCallingSupport.UNKNOWN,
                manager.toolCallingSupport(config("http://127.0.0.1:12345/v1", "model-b")));
        AiConfig differentProvider = new AiConfig(AiExecutionMode.PLAYER_PROVIDED, AiProviderType.DEEPSEEK,
                "http://127.0.0.1:12345/v1", "key", "model-a");
        assertEquals(ToolCallingSupport.UNKNOWN, manager.toolCallingSupport(differentProvider));
    }

    private static AiConfig config(String baseUrl, String model) {
        return new AiConfig(AiExecutionMode.PLAYER_PROVIDED, AiProviderType.CUSTOM, baseUrl, "", model);
    }
}
