package com.ikunkk02.wishingwillow.wish;

import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiExecutionMode;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.ai.InterpretationState;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.program.WishProgram;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import javax.annotation.Nullable;
import java.util.UUID;

public final class WishSession {
    private final UUID sessionId;
    private final UUID playerId;
    private final String rawWish;
    private final ResourceLocation dimension;
    private final long submittedGameTime;
    private final long submittedAtEpochMillis;
    private final InteractionHand hand;
    private final long itemInstanceId;
    private final AiExecutionMode aiExecutionMode;
    private final AiProviderType providerType;
    private final String model;
    private WishState state;
    private long stateChangedGameTime;
    private InterpretationState interpretationState;
    private AiErrorCategory aiErrorCategory;
    @Nullable
    private WishInterpretation interpretation;
    @Nullable
    private WishProgram program;
    private long interpretationUpdatedAtEpochMillis;

    public WishSession(
            UUID sessionId,
            UUID playerId,
            String rawWish,
            ResourceLocation dimension,
            long submittedGameTime,
            long submittedAtEpochMillis,
            InteractionHand hand,
            long itemInstanceId,
            AiExecutionMode aiExecutionMode,
            AiProviderType providerType,
            String model
    ) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.rawWish = rawWish;
        this.dimension = dimension;
        this.submittedGameTime = submittedGameTime;
        this.submittedAtEpochMillis = submittedAtEpochMillis;
        this.hand = hand;
        this.itemInstanceId = itemInstanceId;
        this.aiExecutionMode = aiExecutionMode;
        this.providerType = providerType;
        this.model = model;
        this.state = WishState.REQUESTED;
        this.stateChangedGameTime = submittedGameTime;
        this.interpretationState = InterpretationState.NOT_REQUESTED;
        this.aiErrorCategory = AiErrorCategory.NONE;
        this.interpretationUpdatedAtEpochMillis = submittedAtEpochMillis;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID playerId() {
        return playerId;
    }

    public String rawWish() {
        return rawWish;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public long submittedGameTime() {
        return submittedGameTime;
    }

    public long submittedAtEpochMillis() {
        return submittedAtEpochMillis;
    }

    public InteractionHand hand() {
        return hand;
    }

    public long itemInstanceId() {
        return itemInstanceId;
    }

    public AiExecutionMode aiExecutionMode() {
        return aiExecutionMode;
    }

    public AiProviderType providerType() {
        return providerType;
    }

    public String model() {
        return model;
    }

    public WishState state() {
        return state;
    }

    public long stateChangedGameTime() {
        return stateChangedGameTime;
    }

    public InterpretationState interpretationState() {
        return interpretationState;
    }

    public AiErrorCategory aiErrorCategory() {
        return aiErrorCategory;
    }

    @Nullable
    public WishInterpretation interpretation() {
        return interpretation;
    }

    @Nullable
    public WishProgram program() { return program; }

    public long interpretationUpdatedAtEpochMillis() {
        return interpretationUpdatedAtEpochMillis;
    }

    public void transitionTo(WishState newState, long gameTime) {
        state = newState;
        stateChangedGameTime = gameTime;
    }

    public void markInterpretationRequesting(long epochMillis) {
        interpretationState = InterpretationState.REQUESTING;
        aiErrorCategory = AiErrorCategory.NONE;
        interpretation = null;
        program = null;
        interpretationUpdatedAtEpochMillis = epochMillis;
    }

    public void completeInterpretation(
            InterpretationState newState,
            AiErrorCategory errorCategory,
            @Nullable WishInterpretation newInterpretation,
            long epochMillis
    ) {
        completeInterpretation(newState, errorCategory, newInterpretation, null, epochMillis);
    }

    public void completeInterpretation(
            InterpretationState newState,
            AiErrorCategory errorCategory,
            @Nullable WishInterpretation newInterpretation,
            @Nullable WishProgram newProgram,
            long epochMillis
    ) {
        interpretationState = newState;
        aiErrorCategory = errorCategory;
        interpretation = newInterpretation;
        program = newProgram;
        interpretationUpdatedAtEpochMillis = epochMillis;
    }
}
