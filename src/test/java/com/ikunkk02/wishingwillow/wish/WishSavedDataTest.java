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
import net.minecraft.resources.ResourceLocation;
import com.ikunkk02.wishingwillow.execution.WishExecutionAcceptError;
import com.ikunkk02.wishingwillow.execution.WishExecutionState;
import com.ikunkk02.wishingwillow.planning.WishPlanError;
import com.ikunkk02.wishingwillow.planning.WishPlanState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WishSavedDataTest {
    @Test
    void preservesMultipleWishesAndInterpretationRoundTrip() {
        UUID player = UUID.randomUUID();
        WishSavedData data = new WishSavedData();
        data.update(record(UUID.randomUUID(), player, 100L, InterpretationState.SUCCESS));
        data.update(record(UUID.randomUUID(), player, 200L, InterpretationState.AI_REQUEST_FAILED));

        WishSavedData loaded = WishSavedData.load(data.save(new CompoundTag()));
        assertEquals(2, loaded.getAll(player).size());
        assertEquals(200L, loaded.getLatest(player).submittedAtEpochMillis());
        assertEquals(InterpretationState.SUCCESS, loaded.getAll(player).get(0).interpretationState());
    }

    @Test
    void convertsInterruptedRequestToDisconnectedFailureOnLoad() {
        WishRecord requesting = record(UUID.randomUUID(), UUID.randomUUID(), 100L, InterpretationState.REQUESTING);
        WishRecord loaded = WishRecord.load(requesting.save());
        assertEquals(InterpretationState.AI_REQUEST_FAILED, loaded.interpretationState());
        assertEquals(AiErrorCategory.DISCONNECTED, loaded.aiErrorCategory());
    }

    @Test
    void executionFailureDetailRoundTripsAndLegacyFieldsDefaultSafely(){
        WishRecord failed=record(UUID.randomUUID(),UUID.randomUUID(),100L,InterpretationState.SUCCESS)
                .withExecution(null,WishExecutionState.FAILED,WishExecutionAcceptError.RISK_TOO_HIGH,
                        "step=0 action=SPAWN_ENTITY risk=85");
        WishRecord loaded=WishRecord.load(failed.save());
        assertEquals(WishExecutionState.FAILED,loaded.executionState());
        assertEquals(WishExecutionAcceptError.RISK_TOO_HIGH,loaded.executionError());
        assertEquals("step=0 action=SPAWN_ENTITY risk=85",loaded.executionErrorDetail());

        CompoundTag legacy=record(UUID.randomUUID(),UUID.randomUUID(),200L,InterpretationState.SUCCESS).save();
        legacy.remove("ExecutionError");legacy.remove("ExecutionErrorDetail");
        WishRecord legacyLoaded=WishRecord.load(legacy);
        assertEquals(WishExecutionAcceptError.NONE,legacyLoaded.executionError());
        assertEquals("",legacyLoaded.executionErrorDetail());
    }

    @Test
    void returnsLatestDisconnectedPlanningForReconnectResume() {
        UUID player = UUID.randomUUID();
        UUID older = UUID.randomUUID();
        UUID latest = UUID.randomUUID();
        WishSavedData data = new WishSavedData();
        data.update(record(older, player, 100L, InterpretationState.SUCCESS)
                .withPlanning(WishPlanState.FAILED, WishPlanError.DISCONNECTED, null));
        data.update(record(latest, player, 200L, InterpretationState.SUCCESS)
                .withPlanning(WishPlanState.FAILED, WishPlanError.DISCONNECTED, null));
        data.update(record(UUID.randomUUID(), player, 300L, InterpretationState.SUCCESS)
                .withPlanning(WishPlanState.FAILED, WishPlanError.INVALID_PARAMETER, null));

        assertEquals(latest, data.getLatestResumablePlanning(player).sessionId());
    }

    private static WishRecord record(UUID session, UUID player, long submittedAt, InterpretationState state) {
        WishInterpretation interpretation = state == InterpretationState.SUCCESS
                ? new WishInterpretation(
                1, "companionship", "Company", "Identity unspecified", "A watcher follows",
                "The companion was unspecified", WishTone.HORROR, 72, WishDelivery.DELAYED,
                List.of(WishCapability.STALKING_ENTITY)
        )
                : null;
        return new WishRecord(
                session,
                player,
                "I do not want to be alone",
                new ResourceLocation("minecraft", "overworld"),
                20L,
                submittedAt,
                WishState.FINISHED,
                state,
                state == InterpretationState.AI_REQUEST_FAILED ? AiErrorCategory.TIMEOUT : AiErrorCategory.NONE,
                AiExecutionMode.PLAYER_PROVIDED,
                AiProviderType.CUSTOM,
                "test-model",
                submittedAt + 1,
                interpretation
        );
    }
}
