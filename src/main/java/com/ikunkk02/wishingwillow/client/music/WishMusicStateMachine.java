package com.ikunkk02.wishingwillow.client.music;

/** Pure state machine used by the client controller and unit tests. */
public final class WishMusicStateMachine {
    public enum Action { NONE, START, CONTINUE, REPLACE, BEGIN_FADE, STOP }
    private WishMusicState state = WishMusicState.NONE;
    private WishMusicScene scene;
    private int holdTicks;
    private int fadeTicks;

    public WishMusicState state() { return state; }
    public WishMusicScene scene() { return scene; }
    public int fadeTicks() { return fadeTicks; }

    public Action start(WishMusicScene requested) {
        if (scene == requested && state != WishMusicState.NONE) return Action.NONE;
        if (scene == WishMusicScene.WISH_SEQUENCE && requested == WishMusicScene.TRADE_REVEAL) return Action.NONE;
        boolean seamlessUpgrade = scene == WishMusicScene.TRADE_REVEAL
                && requested == WishMusicScene.WISH_SEQUENCE;
        Action action = seamlessUpgrade ? Action.CONTINUE : scene == null ? Action.START : Action.REPLACE;
        scene = requested;
        state = requested == WishMusicScene.WISH_SEQUENCE ? WishMusicState.WISH_SEQUENCE : WishMusicState.TRADE_REVEAL;
        holdTicks = fadeTicks = 0;
        return action;
    }

    public void tradeScreenClosed() {
        if (scene == WishMusicScene.TRADE_REVEAL && state == WishMusicState.TRADE_REVEAL) scheduleFade(200, 60);
    }
    public void wishCancelled() {
        if (scene == WishMusicScene.WISH_SEQUENCE) scheduleFade(0, 60);
    }
    public void omenFinished() {
        if (scene == WishMusicScene.WISH_SEQUENCE) scheduleFade(20, 80);
    }
    private void scheduleFade(int hold, int fade) { holdTicks = hold; fadeTicks = fade; }

    public Action tick() {
        if (scene == null || state == WishMusicState.FADING_OUT || fadeTicks == 0) return Action.NONE;
        if (holdTicks > 0) { holdTicks--; return Action.NONE; }
        state = WishMusicState.FADING_OUT;
        return Action.BEGIN_FADE;
    }
    public Action stopped() {
        if (scene == null) return Action.NONE;
        state = WishMusicState.NONE; scene = null; holdTicks = fadeTicks = 0; return Action.STOP;
    }
}
