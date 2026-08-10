package com.ikunkk02.wishingwillow.research;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class ModKnowledgeCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path root;

    public ModKnowledgeCache() {
        this(FMLPaths.CONFIGDIR.get().resolve("wishing_willow").resolve("knowledge"));
    }

    ModKnowledgeCache(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Nullable
    public KnowledgeEntry load(ModFingerprint fingerprint) {
        if (!fingerprint.available()) {
            return null;
        }
        Path path = entryPath(fingerprint);
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            KnowledgeEntry entry = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), KnowledgeEntry.class);
            if (entry == null || entry.schemaVersion() != 1
                    || !entry.fingerprint().cacheKey().equals(fingerprint.cacheKey())) {
                return null;
            }
            return entry;
        } catch (Exception exception) {
            LOGGER.warn("Ignoring one invalid Wishing Willow knowledge cache entry: {}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    public synchronized void save(KnowledgeEntry entry) {
        if (!entry.fingerprint().available()) {
            return;
        }
        try {
            atomicWrite(entryPath(entry.fingerprint()), GSON.toJson(entry));
        } catch (IOException exception) {
            LOGGER.warn("Unable to save Wishing Willow mod knowledge: {}", exception.getClass().getSimpleName());
        }
    }

    public synchronized void saveIndex(KnowledgeBaseSnapshot snapshot) {
        JsonObject rootObject = new JsonObject();
        rootObject.addProperty("schema_version", 1);
        rootObject.addProperty("state", snapshot.state().name());
        rootObject.addProperty("paused", snapshot.paused());
        JsonArray entries = new JsonArray();
        for (KnowledgeEntry entry : snapshot.entries()) {
            JsonObject value = new JsonObject();
            value.addProperty("mod_id", entry.installed().modId());
            value.addProperty("version", entry.installed().version());
            value.addProperty("sha512", entry.fingerprint().sha512());
            value.addProperty("state", entry.state().name());
            value.addProperty("knowledge_level", entry.knowledgeLevel().name());
            entries.add(value);
        }
        rootObject.add("mods", entries);
        try {
            atomicWrite(root.resolve("index.json"), GSON.toJson(rootObject));
        } catch (IOException exception) {
            LOGGER.warn("Unable to save Wishing Willow knowledge index: {}", exception.getClass().getSimpleName());
        }
    }

    public synchronized void saveRegistry(RegistrySnapshot snapshot) {
        try {
            atomicWrite(root.resolve("registry.json"), GSON.toJson(snapshot));
        } catch (IOException exception) {
            LOGGER.warn("Unable to save Wishing Willow registry snapshot: {}", exception.getClass().getSimpleName());
        }
    }

    public synchronized boolean clear() {
        if (!Files.exists(root)) {
            return true;
        }
        try (var paths = Files.walk(root)) {
            List<Path> targets = paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList();
            for (Path target : targets) {
                Path resolved = target.toAbsolutePath().normalize();
                if (!resolved.startsWith(root) || resolved.equals(root.getRoot())) {
                    throw new IOException("Unsafe cache target");
                }
                Files.deleteIfExists(resolved);
            }
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Unable to clear Wishing Willow knowledge cache: {}", exception.getClass().getSimpleName());
            return false;
        }
    }

    public Path root() {
        return root;
    }

    private Path entryPath(ModFingerprint fingerprint) {
        String safeId = fingerprint.modId().replaceAll("[^a-z0-9_.-]", "_");
        return root.resolve("mods").resolve(safeId + "-" + fingerprint.sha512() + ".json");
    }

    private static void atomicWrite(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
