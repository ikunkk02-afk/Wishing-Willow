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
    private static final AiConfigManager INSTANCE = new AiConfigManager(defaultPath());

    private final Path path;
    private AiConfig current;
    private ToolCallingSupport currentToolCallingSupport;

    public AiConfigManager(Path path) {
        this.path = path;
        Loaded loaded = loadFromDisk();
        this.current = loaded.config();
        this.currentToolCallingSupport = loaded.toolCallingSupport();
    }

    public static AiConfigManager getInstance() {
        return INSTANCE;
    }

    private static Path defaultPath() {
        Path configDirectory = FMLPaths.CONFIGDIR.get();
        if (configDirectory == null) configDirectory = Path.of("config");
        return configDirectory.resolve("wishing_willow").resolve("ai-client.json");
    }

    public synchronized AiConfig get() {
        return current;
    }

    public synchronized boolean save(AiConfig config) {
        if (!config.isConfigured()) {
            return false;
        }
        ToolCallingSupport support = AiService.getInstance().toolCallingSupport(config);
        if (!write(config, support)) return false;
        current = config;
        currentToolCallingSupport = support;
        AiService.getInstance().retainOnlyToolCallingSupport(config);
        return true;
    }

    public synchronized ToolCallingSupport toolCallingSupport(AiConfig config) {
        return sameConnection(current, config) ? currentToolCallingSupport : ToolCallingSupport.UNKNOWN;
    }

    public synchronized void updateToolCallingSupport(AiConfig config, ToolCallingSupport support) {
        if (support == null || support == ToolCallingSupport.UNKNOWN || !sameConnection(current, config)) return;
        if (write(current, support)) currentToolCallingSupport = support;
    }

    private boolean write(AiConfig config, ToolCallingSupport support) {
        JsonObject json = new JsonObject();
        json.addProperty("version", 2);
        json.addProperty("execution_mode", config.executionMode().name());
        json.addProperty("provider_type", config.providerType().name());
        json.addProperty("base_url", config.baseUrl());
        json.addProperty("api_key", config.apiKey());
        json.addProperty("model", config.model());
        json.addProperty("tool_calling_support", (support == null
                ? ToolCallingSupport.UNKNOWN : support).name());

        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temporary, GSON.toJson(json), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
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

    private Loaded loadFromDisk() {
        if (!Files.isRegularFile(path)) {
            return new Loaded(AiConfig.defaults(), ToolCallingSupport.UNKNOWN);
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
            AiConfig config = new AiConfig(
                    mode,
                    provider,
                    string(json, "base_url"),
                    string(json, "api_key"),
                    string(json, "model")
            );
            ToolCallingSupport support = safeEnum(ToolCallingSupport.class,
                    string(json, "tool_calling_support"), ToolCallingSupport.UNKNOWN);
            return new Loaded(config, support);
        } catch (Exception exception) {
            LOGGER.warn("Unable to load local Wishing Willow AI configuration: {}", exception.getClass().getSimpleName());
            return new Loaded(AiConfig.defaults(), ToolCallingSupport.UNKNOWN);
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

    private static boolean sameConnection(AiConfig left, AiConfig right) {
        return left.providerType() == right.providerType()
                && left.baseUrl().equals(right.baseUrl())
                && left.model().equals(right.model());
    }

    private record Loaded(AiConfig config, ToolCallingSupport toolCallingSupport) { }
}
