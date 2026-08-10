package com.ikunkk02.wishingwillow.research;

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

public final class ResearchConfigManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResearchConfigManager INSTANCE = new ResearchConfigManager(
            FMLPaths.CONFIGDIR.get().resolve("wishing_willow").resolve("research-client.json")
    );

    private final Path path;
    private ResearchConfig current;

    ResearchConfigManager(Path path) {
        this.path = path;
        this.current = load();
    }

    public static ResearchConfigManager getInstance() {
        return INSTANCE;
    }

    public synchronized ResearchConfig get() {
        return current;
    }

    public synchronized boolean save(ResearchConfig config) {
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("curseforge_api_key", config.curseForgeApiKey());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temporary, GSON.toJson(json), StandardCharsets.UTF_8);
            move(temporary, path);
            current = config;
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Unable to save local Wishing Willow research configuration: {}",
                    exception.getClass().getSimpleName());
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    private ResearchConfig load() {
        if (!Files.isRegularFile(path)) {
            return ResearchConfig.defaults();
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!json.has("version") || json.get("version").getAsInt() != 1) {
                return ResearchConfig.defaults();
            }
            return new ResearchConfig(json.has("curseforge_api_key")
                    ? json.get("curseforge_api_key").getAsString() : "");
        } catch (Exception exception) {
            LOGGER.warn("Unable to load local Wishing Willow research configuration: {}",
                    exception.getClass().getSimpleName());
            return ResearchConfig.defaults();
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
