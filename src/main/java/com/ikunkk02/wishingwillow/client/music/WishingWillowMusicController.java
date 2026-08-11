package com.ikunkk02.wishingwillow.client.music;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.config.WishingWillowClientConfig;
import com.ikunkk02.wishingwillow.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WishingWillowMusicController {
    private static final WishMusicStateMachine MACHINE = new WishMusicStateMachine();
    public static final WishMusicCueConfig CUES = WishMusicCueConfig.UNALIGNED;
    private static FadingMusicSoundInstance active;
    private static int activeTicks;

    private WishingWillowMusicController() {}
    public static WishMusicState state() { return MACHINE.state(); }

    public static void startTradeReveal() {
        start(WishMusicScene.TRADE_REVEAL);
    }
    public static void startWishSequence() {
        start(WishMusicScene.WISH_SEQUENCE);
    }
    public static void tradeScreenClosed() { MACHINE.tradeScreenClosed(); }
    public static void cancelWishSequence() { MACHINE.wishCancelled(); }
    public static void omenFinished() { MACHINE.omenFinished(); }

    private static void start(WishMusicScene scene) {
        Minecraft minecraft = Minecraft.getInstance();
        WishMusicSettingsSnapshot settings = new WishMusicSettingsSnapshot(
                WishingWillowClientConfig.CINEMATIC_MUSIC.get(), WishingWillowClientConfig.MUSIC_VOLUME.get(),
                WishingWillowClientConfig.TRADE_REVEAL_MUSIC.get(), WishingWillowClientConfig.WISH_SEQUENCE_MUSIC.get());
        if (!settings.canPlay(scene, minecraft.options.getSoundSourceVolume(SoundSource.MUSIC))) return;
        WishMusicStateMachine.Action action = MACHINE.start(scene);
        if (action == WishMusicStateMachine.Action.NONE) return;
        if (active != null) minecraft.getSoundManager().stop(active);
        minecraft.getMusicManager().stopPlaying();
        active = new FadingMusicSoundInstance(scene == WishMusicScene.WISH_SEQUENCE
                ? ModSounds.WISH_SEQUENCE_MUSIC.get() : ModSounds.TRADE_REVEAL_MUSIC.get(),
                WishingWillowClientConfig.MUSIC_VOLUME.get() / 100.0F,
                scene == WishMusicScene.WISH_SEQUENCE, 40);
        activeTicks = 0;
        minecraft.getSoundManager().play(active);
    }

    @SubscribeEvent public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.player.isDeadOrDying()) {
            clear(); return;
        }
        if (active != null) activeTicks++;
        if (MACHINE.tick() == WishMusicStateMachine.Action.BEGIN_FADE && active != null) {
            active.fadeOut(MACHINE.fadeTicks());
        }
        if (active != null && (active.isStopped()
                || activeTicks > 2 && !minecraft.getSoundManager().isActive(active))) {
            active = null; MACHINE.stopped(); activeTicks = 0;
        }
    }

    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    public static void clear() {
        if (active != null) Minecraft.getInstance().getSoundManager().stop(active);
        active = null; activeTicks = 0; MACHINE.stopped();
    }
}
