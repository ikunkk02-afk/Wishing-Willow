package com.ikunkk02.wishingwillow.client.music;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

final class FadingMusicSoundInstance extends AbstractTickableSoundInstance {
    private final float configuredVolume;
    private final int fadeInTicks;
    private int age;
    private int fadeOutTicks;
    private int fadeOutAge;
    private int recoveryTicks;
    private int recoveryAge;
    private float recoveryStartVolume;

    FadingMusicSoundInstance(SoundEvent event, float configuredVolume, boolean loop, int fadeInTicks) {
        super(event, SoundSource.MUSIC, RandomSource.create());
        this.configuredVolume = configuredVolume;
        this.fadeInTicks = Math.max(1, fadeInTicks);
        this.looping = loop;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.volume = 0;
    }

    void fadeOut(int ticks) { fadeOutTicks = Math.max(1, ticks); fadeOutAge = 0; }

    void continueFromCurrentPosition(int ticks) {
        if (fadeOutTicks <= 0) return;
        fadeOutTicks = fadeOutAge = 0;
        recoveryTicks = Math.max(1, ticks);
        recoveryAge = 0;
        recoveryStartVolume = volume;
    }

    /** SoundEngine otherwise discards the instance before its first fade-in tick because volume starts at zero. */
    @Override public boolean canStartSilent() { return true; }

    @Override public void tick() {
        age++;
        float fadeIn = Mth.clamp(age / (float) fadeInTicks, 0, 1);
        if (fadeOutTicks > 0) {
            fadeOutAge++;
            volume = configuredVolume * fadeIn * Mth.clamp(1.0F - fadeOutAge / (float) fadeOutTicks, 0, 1);
            if (fadeOutAge >= fadeOutTicks) stop();
        } else if (recoveryTicks > 0) {
            recoveryAge++;
            volume = Mth.lerp(Mth.clamp(recoveryAge / (float) recoveryTicks, 0, 1),
                    recoveryStartVolume, configuredVolume);
            if (recoveryAge >= recoveryTicks) recoveryTicks = recoveryAge = 0;
        } else volume = configuredVolume * fadeIn;
    }
}
