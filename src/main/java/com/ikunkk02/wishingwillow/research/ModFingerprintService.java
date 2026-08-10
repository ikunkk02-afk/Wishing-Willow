package com.ikunkk02.wishingwillow.research;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ModFingerprintService {
    public ModFingerprint fingerprint(ScannedMod mod) {
        String hash = "";
        if (Files.isRegularFile(mod.localPath()) && mod.publicInfo().fileName().toLowerCase().endsWith(".jar")) {
            try {
                hash = sha512(mod);
            } catch (IOException ignored) {
                // The path is intentionally not logged. The entry remains usable in local-only mode.
            }
        }
        return new ModFingerprint(mod.publicInfo().modId(), mod.publicInfo().version(),
                mod.publicInfo().fileName(), hash);
    }

    private static String sha512(ScannedMod mod) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] buffer = new byte[128 * 1024];
            try (InputStream input = Files.newInputStream(mod.localPath())) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
