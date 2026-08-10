package com.ikunkk02.wishingwillow.wish;

import com.ikunkk02.wishingwillow.WishingWillow;

import javax.annotation.Nullable;
import java.util.UUID;

public final class WishPipelineAudit {
    private WishPipelineAudit() {}

    public static void success(UUID session, String stage, String detail) {
        WishingWillow.LOGGER.info("Wish pipeline session={} stage={} status=SUCCESS {}",
                session, stage, safe(detail));
    }

    public static void failure(UUID session, String stage, String error, String detail) {
        WishingWillow.LOGGER.warn("Wish pipeline session={} stage={} status=FAILED error={} detail={}",
                session, stage, safe(error), safe(detail));
    }

    public static void execution(UUID session, @Nullable UUID plan, @Nullable UUID execution,
                                 String status, String error, String detail) {
        WishingWillow.LOGGER.info(
                "Wish pipeline session={} stage=EXECUTION status={} plan={} execution={} error={} detail={}",
                session, safe(status), plan, execution, safe(error), safe(detail));
    }

    private static String safe(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 256 ? clean.substring(0, 256) : clean;
    }
}
