package com.ikunkk02.wishingwillow.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AiConfigManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AiConfigManager INSTANCE = new AiConfigManager(
            FMLPaths.CONFIGDIR.get().resolve("wishing_willow").resolve("ai-client.json")
    );

    private final Path path;
    private AiConfig current;

    public AiConfigManager(Path path) {
        this.path = path;
        this.current = loadFromDisk();
    }

    public static AiConfigManager getInstance() {
        return INSTANCE;
    }

    public synchronized AiConfig get() {
        return current;
    }

    public synchronized boolean save(AiConfig config) {
        if (!config.isConfigured()) {
            return false;
        }
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("execution_mode", config.executionMode().name());
        json.addProperty("provider_type", config.providerType().name());
        json.addProperty("base_url", config.baseUrl());
        json.addProperty("api_key", config.apiKey());
        json.addProperty("model", config.model());

        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temporary, GSON.toJson(json), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            current = config;
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Unable to save local Wishing Willow AI configuration: {}", exception.getClass().getSimpleName());
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Best-effort cleanup. Never include configuration contents in logs.
            }
            return false;
        }
    }

    public Path path() {
        return path;
    }

    private AiConfig loadFromDisk() {
        if (!Files.isRegularFile(path)) {
            return AiConfig.defaults();
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            AiExecutionMode mode = safeEnum(
                    AiExecutionMode.class,
                    string(json, "execution_mode"),
                    AiExecutionMode.PLAYER_PROVIDED
            );
            AiProviderType provider = safeEnum(
                    AiProviderType.class,
                    string(json, "provider_type"),
                    AiProviderType.DEEPSEEK
            );
            return new AiConfig(
                    mode,
                    provider,
                    string(json, "base_url"),
                    string(json, "api_key"),
                    string(json, "model")
            );
        } catch (Exception exception) {
            LOGGER.warn("Unable to load local Wishing Willow AI configuration: {}", exception.getClass().getSimpleName());
            return AiConfig.defaults();
        }
    }

    private static String string(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsString() : "";
    }

    private static <E extends Enum<E>> E safeEnum(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
