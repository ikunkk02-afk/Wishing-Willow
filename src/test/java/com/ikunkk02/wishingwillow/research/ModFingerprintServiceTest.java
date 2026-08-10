package com.ikunkk02.wishingwillow.research;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModFingerprintServiceTest {
    @TempDir
    Path temporary;

    @Test
    void computesSha512WithoutExposingPath() throws Exception {
        Path jar = temporary.resolve("example.jar");
        Files.writeString(jar, "abc");
        InstalledModInfo info = info("example", "1.0", "example.jar", "content");
        ModFingerprint fingerprint = new ModFingerprintService().fingerprint(new ScannedMod(info, jar));
        assertEquals("ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2"
                + "192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f", fingerprint.sha512());
        assertTrue(fingerprint.available());
        assertFalse(fingerprint.toString().contains(temporary.toString()));
    }

    public static InstalledModInfo info(String id, String version, String file, String description) {
        return new InstalledModInfo(id, id, id, version, description, List.of("author"), "MIT",
                "https://github.com/example/example", "", "", "", file,
                "1.20.1", "forge", List.of());
    }
}
