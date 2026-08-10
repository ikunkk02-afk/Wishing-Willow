package com.ikunkk02.wishingwillow.wish;

import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiExecutionMode;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.ai.InterpretationState;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishTone;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record WishRecord(
        UUID sessionId,
        UUID playerId,
        String rawWish,
        ResourceLocation dimension,
        long submittedGameTime,
        long submittedAtEpochMillis,
        WishState state,
        InterpretationState interpretationState,
        AiErrorCategory aiErrorCategory,
        AiExecutionMode aiExecutionMode,
        AiProviderType providerType,
        String model,
        long interpretationUpdatedAtEpochMillis,
        @Nullable WishInterpretation interpretation
) {
    public static WishRecord fromSession(WishSession session) {
        return new WishRecord(
                session.sessionId(),
                session.playerId(),
                session.rawWish(),
                session.dimension(),
                session.submittedGameTime(),
                session.submittedAtEpochMillis(),
                session.state(),
                session.interpretationState(),
                session.aiErrorCategory(),
                session.aiExecutionMode(),
                session.providerType(),
                session.model(),
                session.interpretationUpdatedAtEpochMillis(),
                session.interpretation()
        );
    }

    public WishRecord withInterpretation(
            InterpretationState newState,
            AiErrorCategory errorCategory,
            @Nullable WishInterpretation newInterpretation,
            long updatedAt
    ) {
        return new WishRecord(
                sessionId, playerId, rawWish, dimension, submittedGameTime, submittedAtEpochMillis,
                state, newState, errorCategory, aiExecutionMode, providerType, model, updatedAt, newInterpretation
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("SessionId", sessionId);
        tag.putUUID("PlayerId", playerId);
        tag.putString("Wish", rawWish);
        tag.putString("Dimension", dimension.toString());
        tag.putLong("GameTime", submittedGameTime);
        tag.putLong("SubmittedAt", submittedAtEpochMillis);
        tag.putString("State", state.name());
        tag.putString("InterpretationState", interpretationState.name());
        tag.putString("AiErrorCategory", aiErrorCategory.name());
        tag.putString("AiExecutionMode", aiExecutionMode.name());
        tag.putString("ProviderType", providerType.name());
        tag.putString("Model", model);
        tag.putLong("InterpretationUpdatedAt", interpretationUpdatedAtEpochMillis);
        if (interpretation != null) {
            tag.put("Interpretation", saveInterpretation(interpretation));
        }
        return tag;
    }

    public static WishRecord load(CompoundTag tag) {
        WishState loadedState = safeEnum(WishState.class, tag.getString("State"), WishState.CANCELLED);
        if (loadedState == WishState.REQUESTED || loadedState == WishState.ANIMATING) {
            loadedState = WishState.CANCELLED;
        } else if (loadedState == WishState.SNAPPED) {
            loadedState = WishState.FINISHED;
        }

        InterpretationState interpretationState = safeEnum(
                InterpretationState.class,
                tag.getString("InterpretationState"),
                InterpretationState.NOT_REQUESTED
        );
        AiErrorCategory errorCategory = safeEnum(
                AiErrorCategory.class,
                tag.getString("AiErrorCategory"),
                AiErrorCategory.NONE
        );
        if (interpretationState == InterpretationState.REQUESTING) {
            interpretationState = InterpretationState.AI_REQUEST_FAILED;
            errorCategory = AiErrorCategory.DISCONNECTED;
        }

        WishInterpretation interpretation = null;
        if (tag.contains("Interpretation", Tag.TAG_COMPOUND)) {
            try {
                interpretation = loadInterpretation(tag.getCompound("Interpretation"));
            } catch (RuntimeException ignored) {
                interpretationState = InterpretationState.INVALID_RESPONSE;
                errorCategory = AiErrorCategory.MALFORMED_RESPONSE;
            }
        }
        if (interpretationState == InterpretationState.SUCCESS && interpretation == null) {
            interpretationState = InterpretationState.INVALID_RESPONSE;
            errorCategory = AiErrorCategory.MALFORMED_RESPONSE;
        }
        return new WishRecord(
                tag.getUUID("SessionId"),
                tag.getUUID("PlayerId"),
                tag.getString("Wish"),
                safeResourceLocation(tag.getString("Dimension")),
                tag.getLong("GameTime"),
                tag.getLong("SubmittedAt"),
                loadedState,
                interpretationState,
                errorCategory,
                safeEnum(AiExecutionMode.class, tag.getString("AiExecutionMode"), AiExecutionMode.PLAYER_PROVIDED),
                safeEnum(AiProviderType.class, tag.getString("ProviderType"), AiProviderType.CUSTOM),
                tag.getString("Model"),
                tag.contains("InterpretationUpdatedAt", Tag.TAG_LONG)
                        ? tag.getLong("InterpretationUpdatedAt")
                        : tag.getLong("SubmittedAt"),
                interpretation
        );
    }

    private static CompoundTag saveInterpretation(WishInterpretation interpretation) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SchemaVersion", interpretation.schemaVersion());
        tag.putString("Intent", interpretation.intent());
        tag.putString("LiteralGoal", interpretation.literalGoal());
        tag.putString("Loophole", interpretation.loophole());
        tag.putString("TwistedOutcome", interpretation.twistedOutcome());
        tag.putString("ReasoningSummary", interpretation.reasoningSummary());
        tag.putString("Tone", interpretation.tone().name());
        tag.putInt("Severity", interpretation.severity());
        tag.putString("Delivery", interpretation.delivery().name());
        ListTag capabilities = new ListTag();
        interpretation.requiredCapabilities().stream()
                .map(capability -> StringTag.valueOf(capability.name()))
                .forEach(capabilities::add);
        tag.put("RequiredCapabilities", capabilities);
        return tag;
    }

    private static WishInterpretation loadInterpretation(CompoundTag tag) {
        ListTag capabilityTags = tag.getList("RequiredCapabilities", Tag.TAG_STRING);
        List<WishCapability> capabilities = new ArrayList<>();
        for (Tag capabilityTag : capabilityTags) {
            capabilities.add(WishCapability.valueOf(capabilityTag.getAsString()));
        }
        WishInterpretation interpretation = new WishInterpretation(
                tag.getInt("SchemaVersion"),
                tag.getString("Intent"),
                tag.getString("LiteralGoal"),
                tag.getString("Loophole"),
                tag.getString("TwistedOutcome"),
                tag.getString("ReasoningSummary"),
                WishTone.valueOf(tag.getString("Tone")),
                tag.getInt("Severity"),
                WishDelivery.valueOf(tag.getString("Delivery")),
                capabilities
        );
        com.ikunkk02.wishingwillow.ai.WishInterpretationValidator.validate(interpretation);
        return interpretation;
    }

    private static ResourceLocation safeResourceLocation(String value) {
        ResourceLocation location = ResourceLocation.tryParse(value);
        return location == null ? new ResourceLocation("minecraft", "overworld") : location;
    }

    private static <E extends Enum<E>> E safeEnum(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
