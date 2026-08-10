package com.ikunkk02.wishingwillow.research;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.forgespi.language.IModInfo;

import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ModScanner {
    public List<ScannedMod> scan() {
        return ModList.get().getMods().stream().map(this::scan).toList();
    }

    private ScannedMod scan(IModInfo mod) {
        Path path = mod.getOwningFile().getFile().getFilePath();
        String authors = configString(mod, "authors");
        String displayUrl = configString(mod, "displayURL");
        String modUrl = mod.getModURL().map(URL::toString).orElse("");
        String issueTrackerUrl = fileConfigString(mod, "issueTrackerURL");
        InstalledModInfo info = new InstalledModInfo(
                mod.getModId(),
                mod.getNamespace(),
                clean(mod.getDisplayName(), 256),
                clean(mod.getVersion().toString(), 128),
                clean(mod.getDescription(), 8192),
                splitAuthors(authors),
                clean(mod.getOwningFile().getLicense(), 256),
                clean(displayUrl, 2048),
                clean(modUrl, 2048),
                clean(issueTrackerUrl, 2048),
                clean(mod.getUpdateURL().map(URL::toString).orElse(""), 2048),
                clean(path.getFileName() == null ? mod.getModId() + ".jar" : path.getFileName().toString(), 256),
                clean(FMLLoader.versionInfo().mcVersion(), 64),
                "forge",
                mod.getDependencies().stream().map(IModInfo.ModVersion::getModId).distinct().sorted().toList()
        );
        return new ScannedMod(info, path);
    }

    private static String configString(IModInfo mod, String key) {
        return mod.getConfig().getConfigElement(key).map(String::valueOf).orElse("");
    }

    private static String fileConfigString(IModInfo mod, String key) {
        return mod.getOwningFile().getConfig().getConfigElement(key).map(String::valueOf).orElse("");
    }

    private static List<String> splitAuthors(String authors) {
        if (authors.isBlank()) {
            return List.of();
        }
        return Arrays.stream(authors.split("[,;]"))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .limit(32)
                .toList();
    }

    private static String clean(String value, int maxLength) {
        String cleaned = value == null ? "" : value.replace('\u0000', ' ').strip();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
