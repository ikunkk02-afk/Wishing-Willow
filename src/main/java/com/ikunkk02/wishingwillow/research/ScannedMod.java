package com.ikunkk02.wishingwillow.research;

import java.nio.file.Path;

/** Internal-only carrier. Never pass this object to network or AI code. */
record ScannedMod(InstalledModInfo publicInfo, Path localPath) {
}
