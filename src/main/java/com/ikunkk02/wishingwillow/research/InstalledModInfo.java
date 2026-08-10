package com.ikunkk02.wishingwillow.research;

import java.util.List;

/** Public, serializable metadata. It deliberately cannot contain a local file path. */
public record InstalledModInfo(
        String modId,
        String namespace,
        String displayName,
        String version,
        String description,
        List<String> authors,
        String license,
        String displayUrl,
        String modUrl,
        String issueTrackerUrl,
        String updateUrl,
        String fileName,
        String minecraftVersion,
        String loader,
        List<String> dependencies
) {
    public InstalledModInfo {
        modId = safe(modId); namespace = safe(namespace); displayName = safe(displayName);
        version = safe(version); description = safe(description); license = safe(license);
        displayUrl = safe(displayUrl); modUrl = safe(modUrl); issueTrackerUrl = safe(issueTrackerUrl);
        updateUrl = safe(updateUrl); fileName = safe(fileName); minecraftVersion = safe(minecraftVersion);
        loader = safe(loader);
        authors = List.copyOf(authors == null ? List.of() : authors);
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
    }

    /** Compatibility constructor for callers compiled against the phase-four metadata shape. */
    public InstalledModInfo(String modId, String namespace, String displayName, String version,
                            String description, List<String> authors, String license,
                            String displayUrl, String modUrl, String fileName, List<String> dependencies) {
        this(modId, namespace, displayName, version, description, authors, license, displayUrl, modUrl,
                "", "", fileName, "1.20.1", "forge", dependencies);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
