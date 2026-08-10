package com.ikunkk02.wishingwillow.research;

public enum ResearchSource {
    LOCAL_METADATA,
    LOCAL_REGISTRY,
    MODRINTH_HASH,
    MODRINTH_PROJECT,
    MODRINTH_SEARCH,
    CURSEFORGE_API,
    CURSEFORGE_PUBLIC_SEARCH,
    CURSEFORGE_PUBLIC_PAGE,
    GITHUB_README,
    OFFICIAL_WEBPAGE,
    AI_WEB_DISCOVERY,
    /** Legacy cache value retained for schema-1 compatibility. */
    CURSEFORGE,
    /** Legacy cache value retained for schema-1 compatibility. */
    SOURCE_README,
}
