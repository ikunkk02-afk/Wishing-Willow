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
        String updateUrl,
        String fileName,
        List<String> dependencies
) {
    public InstalledModInfo {
        authors = List.copyOf(authors);
        dependencies = List.copyOf(dependencies);
    }
}
