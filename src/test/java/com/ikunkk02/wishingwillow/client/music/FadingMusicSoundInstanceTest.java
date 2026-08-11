package com.ikunkk02.wishingwillow.client.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FadingMusicSoundInstanceTest {
    @Test
    void fadeInSoundExplicitlyOverridesMinecraftSilentStartContract() throws ReflectiveOperationException {
        var method = FadingMusicSoundInstance.class.getDeclaredMethod("canStartSilent");
        assertEquals(FadingMusicSoundInstance.class, method.getDeclaringClass(),
                "a zero-volume fade-in must opt in instead of inheriting SoundInstance's false default");
        assertEquals(boolean.class, method.getReturnType());
    }
}
