package com.ikunkk02.wishingwillow.client.music;

public final class TradeRevealExperienceState {
    public enum Update {
        NONE,
        REVEALED,
        CLOSED_REVEAL
    }

    private boolean merchantOpen;
    private boolean revealActive;

    public Update observe(boolean merchantScreen, boolean containsWishingWillow) {
        if (!merchantScreen) {
            boolean closeReveal = merchantOpen && revealActive;
            merchantOpen = false;
            revealActive = false;
            return closeReveal ? Update.CLOSED_REVEAL : Update.NONE;
        }
        if (!merchantOpen) {
            merchantOpen = true;
            revealActive = false;
        }
        if (containsWishingWillow && !revealActive) {
            revealActive = true;
            return Update.REVEALED;
        }
        return Update.NONE;
    }

    public boolean shouldScanOffers() {
        return merchantOpen && !revealActive;
    }

    public boolean revealActive() {
        return revealActive;
    }

    public void clear() {
        merchantOpen = false;
        revealActive = false;
    }
}
