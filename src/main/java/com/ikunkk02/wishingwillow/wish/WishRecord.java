package com.ikunkk02.wishingwillow.wish;

import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiExecutionMode;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.ai.InterpretationState;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishTone;
import com.ikunkk02.wishingwillow.planning.WishPlan;
import com.ikunkk02.wishingwillow.planning.WishPlanError;
import com.ikunkk02.wishingwillow.planning.WishPlanNbt;
import com.ikunkk02.wishingwillow.planning.WishPlanState;
import com.ikunkk02.wishingwillow.execution.WishExecutionState;
import com.ikunkk02.wishingwillow.execution.WishExecutionAcceptError;
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
        @Nullable WishInterpretation interpretation,
        WishPlanState planState,
        WishPlanError planError,
        @Nullable WishPlan plan,
        @Nullable UUID executionId,
        WishExecutionState executionState,
        WishExecutionAcceptError executionError,
        String executionErrorDetail
) {
    public WishRecord(UUID sessionId, UUID playerId, String rawWish, ResourceLocation dimension,
                      long submittedGameTime, long submittedAtEpochMillis, WishState state,
                      InterpretationState interpretationState, AiErrorCategory aiErrorCategory,
                      AiExecutionMode aiExecutionMode, AiProviderType providerType, String model,
                      long interpretationUpdatedAtEpochMillis, @Nullable WishInterpretation interpretation) {
        this(sessionId, playerId, rawWish, dimension, submittedGameTime, submittedAtEpochMillis, state,
                interpretationState, aiErrorCategory, aiExecutionMode, providerType, model,
                interpretationUpdatedAtEpochMillis, interpretation,
                WishPlanState.NOT_PLANNED, WishPlanError.NONE, null, null, WishExecutionState.NOT_ACCEPTED,
                WishExecutionAcceptError.NONE, "");
    }
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
                session.interpretation(),
                WishPlanState.NOT_PLANNED,
                WishPlanError.NONE,
                null,
                null,
                WishExecutionState.NOT_ACCEPTED,
                WishExecutionAcceptError.NONE,
                ""
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
                state, newState, errorCategory, aiExecutionMode, providerType, model, updatedAt, newInterpretation,
                planState, planError, plan, executionId, executionState, executionError, executionErrorDetail
        );
    }

    public WishRecord withPlanning(WishPlanState newState, WishPlanError error, @Nullable WishPlan newPlan) {
        return new WishRecord(sessionId, playerId, rawWish, dimension, submittedGameTime, submittedAtEpochMillis,
                state, interpretationState, aiErrorCategory, aiExecutionMode, providerType, model,
                interpretationUpdatedAtEpochMillis, interpretation, newState, error, newPlan, executionId, executionState,
                executionError, executionErrorDetail);
    }

    public WishRecord withExecution(@Nullable UUID id, WishExecutionState newState) {
        return withExecution(id, newState, WishExecutionAcceptError.NONE, "");
    }

    public WishRecord withExecution(@Nullable UUID id, WishExecutionState newState,
                                    WishExecutionAcceptError error, String detail) {
        return new WishRecord(sessionId, playerId, rawWish, dimension, submittedGameTime, submittedAtEpochMillis,
                state, interpretationState, aiErrorCategory, aiExecutionMode, providerType, model,
                interpretationUpdatedAtEpochMillis, interpretation, planState, planError, plan, id, newState,
                error == null ? WishExecutionAcceptError.UNKNOWN : error, sanitizeDetail(detail));
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
        tag.putString("PlanState", planState.name());
        tag.putString("PlanError", planError.name());
        if (plan != null) tag.put("WishPlan", WishPlanNbt.save(plan));
        if (executionId != null) tag.putUUID("ExecutionId", executionId);
        tag.putString("ExecutionState", executionState.name());
        tag.putString("ExecutionError", executionError.name());
        tag.putString("ExecutionErrorDetail", sanitizeDetail(executionErrorDetail));
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
        WishPlanState planState = safeEnum(WishPlanState.class, tag.getString("PlanState"), WishPlanState.NOT_PLANNED);
        WishPlanError planError = safeEnum(WishPlanError.class, tag.getString("PlanError"), WishPlanError.NONE);
        WishPlan plan = null;
        if (tag.contains("WishPlan", Tag.TAG_COMPOUND)) {
            try { plan = WishPlanNbt.load(tag.getCompound("WishPlan")); }
            catch (RuntimeException ignored) { planState = WishPlanState.STALE; planError = WishPlanError.STALE_RESOURCE; }
        }
        if (planState == WishPlanState.MATCHING || planState == WishPlanState.PLANNING
                || planState == WishPlanState.VALIDATING) {
            planState = WishPlanState.FAILED;
            planError = WishPlanError.DISCONNECTED;
            plan = null;
        }
        if ((planState == WishPlanState.READY || planState == WishPlanState.STALE) && plan == null) {
            planState = WishPlanState.STALE;
            planError = WishPlanError.STALE_RESOURCE;
        }
        UUID executionId = tag.hasUUID("ExecutionId") ? tag.getUUID("ExecutionId") : null;
        WishExecutionState executionState = safeEnum(WishExecutionState.class,
                tag.getString("ExecutionState"), WishExecutionState.NOT_ACCEPTED);
        WishExecutionAcceptError executionError = safeEnum(WishExecutionAcceptError.class,
                tag.getString("ExecutionError"), WishExecutionAcceptError.NONE);
        String executionErrorDetail = tag.getString("ExecutionErrorDetail");
        if (executionId == null && executionState != WishExecutionState.NOT_ACCEPTED
                && executionState != WishExecutionState.FAILED) {
            executionState = WishExecutionState.STALE;
        }
        if(executionId==null&&loadedState==WishState.CANCELLED){
            executionState=WishExecutionState.CANCELLED;
            executionError=WishExecutionAcceptError.NONE;
            executionErrorDetail="wish lifecycle cancelled";
        }else if(executionId==null&&executionState==WishExecutionState.NOT_ACCEPTED
                &&planState==WishPlanState.FAILED){
            executionState=WishExecutionState.FAILED;
            executionError=WishExecutionAcceptError.VALIDATION_FAILED;
            executionErrorDetail="planning="+planError.name();
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
                interpretation,
                planState,
                planError,
                plan,
                executionId,
                executionState,
                executionError,
                sanitizeDetail(executionErrorDetail)
        );
    }

    private static CompoundTag saveInterpretation(WishInterpretation interpretation) {
        CompoundTag tag = new CompoundTag();
        if (interpretation.schemaVersion() == com.ikunkk02.wishingwillow.ai.WishInterpretationValidator.CURRENT_SCHEMA_VERSION) {
            tag.putString("SchemaJson", com.ikunkk02.wishingwillow.ai.WishInterpretationValidator.toJson(interpretation));
        }
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
        if (tag.contains("SchemaJson", Tag.TAG_STRING)) {
            return com.ikunkk02.wishingwillow.ai.WishInterpretationValidator.parseAndValidate(tag.getString("SchemaJson"));
        }
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
        com.ikunkk02.wishingwillow.ai.WishInterpretationValidator.validateStored(interpretation);
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

    private static String sanitizeDetail(String detail) {
        if (detail == null) return "";
        String clean = detail.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 256 ? clean.substring(0, 256) : clean;
    }
}
