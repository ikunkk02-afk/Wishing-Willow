package com.ikunkk02.wishingwillow.research.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.ikunkk02.wishingwillow.research.ModFingerprint;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

public final class WebResearchCache {
    public static final Duration TTL = Duration.ofDays(7);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SEARCH_TYPE = new TypeToken<Entry<List<WebSearchResult>>>() { }.getType();
    private static final Type PAGE_TYPE = new TypeToken<Entry<WebPageDocument>>() { }.getType();
    private final Path root;

    public WebResearchCache() {
        this(FMLPaths.CONFIGDIR.get().resolve("wishing_willow").resolve("knowledge").resolve("web"));
    }
    WebResearchCache(Path root) { this.root = root.toAbsolutePath().normalize(); }

    @Nullable public List<WebSearchResult> loadSearch(ModFingerprint fingerprint, String source, String query) {
        Entry<List<WebSearchResult>> entry = read(path(fingerprint).resolve("search").resolve(hash(source + "\n" + query) + ".json"), SEARCH_TYPE);
        return fresh(entry) ? entry.value() : null;
    }
    public void saveSearch(ModFingerprint fingerprint, String source, String query, List<WebSearchResult> results) {
        write(path(fingerprint).resolve("search").resolve(hash(source + "\n" + query) + ".json"), new Entry<>(System.currentTimeMillis(), results));
    }
    @Nullable public WebPageDocument loadPage(ModFingerprint fingerprint, String url) {
        Entry<WebPageDocument> entry = read(path(fingerprint).resolve("pages").resolve(hash(url) + ".json"), PAGE_TYPE);
        return fresh(entry) ? entry.value() : null;
    }
    public void savePage(ModFingerprint fingerprint, String url, WebPageDocument page) {
        write(path(fingerprint).resolve("pages").resolve(hash(url) + ".json"), new Entry<>(System.currentTimeMillis(), page));
    }
    @Nullable public ModIdentityResolution loadIdentity(ModFingerprint fingerprint) {
        Entry<ModIdentityResolution> entry = read(path(fingerprint).resolve("identity.json"),
                new TypeToken<Entry<ModIdentityResolution>>() { }.getType());
        return fresh(entry) ? entry.value() : null;
    }
    public void saveIdentity(ModFingerprint fingerprint, ModIdentityResolution resolution) {
        write(path(fingerprint).resolve("identity.json"), new Entry<>(System.currentTimeMillis(), resolution));
    }
    public void saveManualUrl(ModFingerprint fingerprint, String url) {
        write(path(fingerprint).resolve("manual-url.json"), new Entry<>(System.currentTimeMillis(), url));
    }
    public String loadManualUrl(ModFingerprint fingerprint) {
        Entry<String> entry = read(path(fingerprint).resolve("manual-url.json"), new TypeToken<Entry<String>>() { }.getType());
        return entry == null || entry.value() == null ? "" : entry.value();
    }

    private Path path(ModFingerprint fingerprint) { return root.resolve(fingerprint.sha512()); }
    private static boolean fresh(@Nullable Entry<?> entry) {
        return entry != null && System.currentTimeMillis() - entry.fetchedAt() <= TTL.toMillis();
    }
    @Nullable private static <T> T read(Path path, Type type) {
        if (!Files.isRegularFile(path)) return null;
        try { return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type); }
        catch (Exception exception) { return null; }
    }
    private static void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent()); Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException exception) {
            LOGGER.warn("Unable to save Wishing Willow web research cache: {}", exception.getClass().getSimpleName());
        }
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private record Entry<T>(long fetchedAt, T value) { }
}
